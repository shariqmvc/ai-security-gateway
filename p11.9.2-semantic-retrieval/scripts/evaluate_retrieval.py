import argparse
import json
import os
import statistics
import time
from collections import Counter
from pathlib import Path

import requests
import psycopg2

ROOT = Path(__file__).resolve().parents[1]
TEST = ROOT.parent / "data" / "semantic_security_v1" / "test.jsonl"
OLLAMA = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
MODEL = os.getenv("OLLAMA_EMBED_MODEL", "nomic-embed-text")

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

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--top-k", type=int, default=5)
    p.add_argument("--limit", type=int, default=0)
    p.add_argument("--threshold", type=float, default=0.0)
    args = p.parse_args()

    rows = [
        json.loads(x) for x in TEST.read_text(encoding="utf-8").splitlines()
        if x.strip()
    ]
    if args.limit:
        rows = rows[:args.limit]

    conn = connect()
    try:
        results = []
        latencies = []
        for i, row in enumerate(rows, 1):
            start = time.perf_counter()
            vec = embed(row["text"])
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT corpus_id, primary_label, labels, similarity
                    FROM search_personal_security_semantic_examples(%s::vector, %s)
                    """,
                    (vec, args.top_k),
                )
                hits = cur.fetchall()
            latency = (time.perf_counter() - start) * 1000
            latencies.append(latency)

            top = hits[0] if hits else None
            predicted = top[1] if top else None
            similarity = float(top[3]) if top else 0.0

            results.append({
                "id": row["id"],
                "expected": row["label"],
                "expectedLabels": row["labels"],
                "predicted": predicted,
                "similarity": similarity,
                "topKLabels": [h[1] for h in hits],
                "latencyMs": latency
            })

            if i == 1 or i % 10 == 0 or i == len(rows):
                print(f"{i}/{len(rows)} evaluated")

        top1 = sum(
            1 for r in results
            if r["predicted"] == r["expected"] and r["similarity"] >= args.threshold
        )
        attack_labels = {
            "PROMPT_INJECTION","JAILBREAK","SYSTEM_PROMPT_EXTRACTION",
            "DATA_EXFILTRATION","INDIRECT_INJECTION","TOOL_MANIPULATION",
            "RAG_POISONING","OBFUSCATED_ATTACK","MULTILINGUAL_ATTACK"
        }
        attacks = [r for r in results if r["expected"] in attack_labels]
        benign = [r for r in results if r["expected"] in {"BENIGN","HARD_BENIGN"}]
        attack_correct = sum(
            1 for r in attacks
            if r["predicted"] not in {None,"BENIGN","HARD_BENIGN"}
            and r["similarity"] >= args.threshold
        )
        benign_fp = sum(
            1 for r in benign
            if r["predicted"] not in {None,"BENIGN","HARD_BENIGN"}
            and r["similarity"] >= args.threshold
        )

        print("\nP11.9.2 semantic retrieval baseline")
        print("=" * 40)
        print(f"Examples:                 {len(results)}")
        print(f"Top-1 label accuracy:     {top1/len(results):.4f}")
        print(f"Attack retrieval recall:  {attack_correct/len(attacks):.4f}" if attacks else "Attack recall: N/A")
        print(f"Benign false-positive:    {benign_fp/len(benign):.4f}" if benign else "Benign FPR: N/A")
        print(f"Mean latency (ms):        {statistics.mean(latencies):.1f}")
        print(f"P95 latency (ms):         {statistics.quantiles(latencies, n=20)[18]:.1f}" if len(latencies) >= 20 else "P95 latency (ms):         N/A (<20 samples)")

        out = ROOT / "results" / "retrieval_baseline.json"
        out.parent.mkdir(exist_ok=True)
        out.write_text(json.dumps({
            "topK": args.top_k,
            "threshold": args.threshold,
            "examples": len(results),
            "top1Accuracy": top1/len(results),
            "attackRecall": attack_correct/len(attacks) if attacks else None,
            "benignFalsePositiveRate": benign_fp/len(benign) if benign else None,
            "meanLatencyMs": statistics.mean(latencies),
            "p95LatencyMs": statistics.quantiles(latencies, n=20)[18] if len(latencies)>=20 else None,
            "results": results
        }, indent=2), encoding="utf-8")
        print(f"Saved: {out}")
    finally:
        conn.close()

if __name__ == "__main__":
    main()
