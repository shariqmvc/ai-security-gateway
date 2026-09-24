import json
import os
import statistics
import time
from collections import Counter, defaultdict
from pathlib import Path

import requests
import psycopg2

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data" / "boundary_v1.jsonl"
OLLAMA = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
MODEL = os.getenv("OLLAMA_EMBED_MODEL", "nomic-embed-text")

# Map the diagnostic boundary concept to the actual P11.9 security taxonomy.
# The corpus itself remains unchanged.
CONCEPT_TO_INDEX_LABELS = {
    "PROMPT_INJECTION": {"PROMPT_INJECTION", "INDIRECT_INJECTION"},
    "INDIRECT_INJECTION": {"INDIRECT_INJECTION", "PROMPT_INJECTION"},
    "DATA_EXFILTRATION": {"DATA_EXFILTRATION"},
    "SYSTEM_PROMPT_EXTRACTION": {"SYSTEM_PROMPT_EXTRACTION"},
    "RAG_POISONING": {"RAG_POISONING", "INDIRECT_INJECTION"},
    "MULTILINGUAL_ATTACK": {"MULTILINGUAL_ATTACK"},
    "OBFUSCATED_ATTACK": {"OBFUSCATED_ATTACK"},
    "HARD_BENIGN": {"BENIGN", "HARD_BENIGN"},
    "BENIGN": {"BENIGN", "HARD_BENIGN"},
}

def embed(text):
    r = requests.post(
        f"{OLLAMA}/api/embeddings",
        json={"model": MODEL, "prompt": text},
        timeout=int(os.getenv("OLLAMA_TIMEOUT_SECONDS", "120")),
    )
    r.raise_for_status()
    vec = r.json()["embedding"]
    if len(vec) != 768:
        raise RuntimeError(f"Expected 768 dimensions, got {len(vec)}")
    return "[" + ",".join(str(float(x)) for x in vec) + "]"

def connect():
    return psycopg2.connect(
        host=os.getenv("PGHOST", "localhost"),
        port=int(os.getenv("PGPORT", "5433")),
        dbname=os.getenv("PGDATABASE", "aegisai"),
        user=os.getenv("PGUSER", "postgres"),
        password=os.getenv("PGPASSWORD", "postgres"),
    )

def hit(labels, target, k):
    return any(x in labels[:k] for x in CONCEPT_TO_INDEX_LABELS[target])

def main():
    rows = [
        json.loads(x)
        for x in DATA.read_text(encoding="utf-8").splitlines()
        if x.strip()
    ]

    conn = connect()
    results = []
    try:
        for i, row in enumerate(rows, 1):
            vec = embed(row["text"])
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT corpus_id, primary_label, family, similarity
                    FROM search_personal_security_semantic_examples(%s::vector, 5)
                    """,
                    (vec,),
                )
                hits = cur.fetchall()

            labels = [h[1] for h in hits]
            similarities = [float(h[3]) for h in hits]
            expected = row["label"]
            contrast = row["contrast"]

            expected_sims = [
                float(h[3])
                for h in hits
                if h[1] in CONCEPT_TO_INDEX_LABELS[expected]
            ]
            contrast_sims = [
                float(h[3])
                for h in hits
                if h[1] in CONCEPT_TO_INDEX_LABELS[contrast]
            ]

            results.append({
                "id": row["id"],
                "expected": expected,
                "contrast": contrast,
                "family": row["family"],
                "text": row["text"],
                "top1": labels[0] if labels else None,
                "top3": labels[:3],
                "top5": labels[:5],
                "top1Similarity": similarities[0] if similarities else 0.0,
                "expectedTop1": hit(labels, expected, 1),
                "expectedTop3": hit(labels, expected, 3),
                "expectedTop5": hit(labels, expected, 5),
                "contrastTop1": hit(labels, contrast, 1),
                "contrastTop3": hit(labels, contrast, 3),
                "contrastTop5": hit(labels, contrast, 5),
                "expectedBestSimilarity": max(expected_sims) if expected_sims else None,
                "contrastBestSimilarity": max(contrast_sims) if contrast_sims else None,
                "expectedContrastMargin": (
                    (max(expected_sims) if expected_sims else 0.0)
                    - (max(contrast_sims) if contrast_sims else 0.0)
                ),
            })

            if i == 1 or i % 10 == 0 or i == len(rows):
                print(f"{i}/{len(rows)} analyzed")

        n = len(results)
        print("\nP11.9.3.1 TOP-K BOUNDARY ANALYSIS")
        print("=" * 42)
        print(f"Examples:                 {n}")
        for k in (1,3,5):
            print(
                f"Expected Top-{k} hit:      "
                f"{sum(r[f'expectedTop{k}'] for r in results)/n:.4f}"
            )
        for k in (1,3,5):
            print(
                f"Contrast Top-{k} hit:      "
                f"{sum(r[f'contrastTop{k}'] for r in results)/n:.4f}"
            )

        margins = [r["expectedContrastMargin"] for r in results]
        print(f"Mean expected-contrast margin: {statistics.mean(margins):.4f}")
        print(f"Median expected-contrast margin: {statistics.median(margins):.4f}")

        by_family = defaultdict(list)
        for r in results:
            by_family[r["family"]].append(r)

        print("\nBY BOUNDARY FAMILY")
        print("-" * 42)
        for family, group in sorted(by_family.items()):
            g=len(group)
            print(
                f"{family:30s} n={g:2d} "
                f"T1={sum(x['expectedTop1'] for x in group)/g:.3f} "
                f"T3={sum(x['expectedTop3'] for x in group)/g:.3f} "
                f"T5={sum(x['expectedTop5'] for x in group)/g:.3f}"
            )

        counter = Counter()
        for r in results:
            if not r["expectedTop1"]:
                counter[(r["expected"], r["top1"])] += 1

        print("\nTOP-1 EXPECTED-LABEL FAILURES")
        print("-" * 42)
        for (expected, predicted), count in counter.most_common():
            print(f"{expected} -> {predicted}: {count}")

        out = ROOT / "results"
        out.mkdir(exist_ok=True)
        payload = {
            "examples": n,
            "expectedTop1HitRate": sum(r["expectedTop1"] for r in results)/n,
            "expectedTop3HitRate": sum(r["expectedTop3"] for r in results)/n,
            "expectedTop5HitRate": sum(r["expectedTop5"] for r in results)/n,
            "contrastTop1HitRate": sum(r["contrastTop1"] for r in results)/n,
            "contrastTop3HitRate": sum(r["contrastTop3"] for r in results)/n,
            "contrastTop5HitRate": sum(r["contrastTop5"] for r in results)/n,
            "meanExpectedContrastMargin": statistics.mean(margins),
            "medianExpectedContrastMargin": statistics.median(margins),
            "byFamily": {
                f: {
                    "count": len(g),
                    "top1": sum(x["expectedTop1"] for x in g)/len(g),
                    "top3": sum(x["expectedTop3"] for x in g)/len(g),
                    "top5": sum(x["expectedTop5"] for x in g)/len(g)
                } for f,g in by_family.items()
            },
            "results": results
        }
        path = out / "topk_boundary_analysis.json"
        path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        print(f"\nSaved: {path}")
    finally:
        conn.close()

if __name__ == "__main__":
    main()
