import argparse
import json
import os
import sys
import time
from pathlib import Path

import requests
import psycopg2

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CORPUS = ROOT.parent / "data" / "semantic_security_v1"

OLLAMA = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
MODEL = os.getenv("OLLAMA_EMBED_MODEL", "nomic-embed-text")
TIMEOUT = int(os.getenv("OLLAMA_TIMEOUT_SECONDS", "120"))

def pg():
    return psycopg2.connect(
        host=os.getenv("PGHOST", "localhost"),
        port=int(os.getenv("PGPORT", "5433")),
        dbname=os.getenv("PGDATABASE", "aegisai"),
        user=os.getenv("PGUSER", "postgres"),
        password=os.getenv("PGPASSWORD", "postgres"),
    )

def embed(text):
    r = requests.post(
        f"{OLLAMA}/api/embeddings",
        json={"model": MODEL, "prompt": text},
        timeout=TIMEOUT,
    )
    r.raise_for_status()
    vec = r.json().get("embedding")
    if not vec:
        raise RuntimeError("Ollama returned no embedding")
    if len(vec) != 768:
        raise RuntimeError(
            f"Expected 768 dimensions from {MODEL}, got {len(vec)}"
        )
    return vec

def pgvector_literal(vec):
    return "[" + ",".join(str(float(x)) for x in vec) + "]"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--split",
        choices=["train", "validation"],
        default="train",
        help="Only train/validation are indexable in P11.9.2. Test is deliberately excluded."
    )
    parser.add_argument(
        "--limit", type=int, default=0,
        help="0 = all rows"
    )
    parser.add_argument(
        "--reset", action="store_true",
        help="Delete existing indexed corpus rows before loading."
    )
    args = parser.parse_args()

    path = DEFAULT_CORPUS / f"{args.split}.jsonl"
    if not path.exists():
        raise SystemExit(f"Corpus not found: {path}")

    rows = [
        json.loads(x)
        for x in path.read_text(encoding="utf-8").splitlines()
        if x.strip()
    ]
    if args.limit:
        rows = rows[:args.limit]

    conn = pg()
    try:
        with conn.cursor() as cur:
            if args.reset:
                cur.execute("TRUNCATE TABLE personal_security_semantic_examples RESTART IDENTITY")
                conn.commit()

        for i, row in enumerate(rows, 1):
            start = time.perf_counter()
            vec = embed(row["text"])
            literal = pgvector_literal(vec)

            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO personal_security_semantic_examples
                    (corpus_id, text, primary_label, labels, family, variant,
                     language, source, severity, embedding, metadata)
                    VALUES (%s,%s,%s,%s::jsonb,%s,%s,%s,%s,%s,%s::vector,%s::jsonb)
                    ON CONFLICT (corpus_id) DO UPDATE SET
                      text=EXCLUDED.text,
                      primary_label=EXCLUDED.primary_label,
                      labels=EXCLUDED.labels,
                      family=EXCLUDED.family,
                      variant=EXCLUDED.variant,
                      language=EXCLUDED.language,
                      source=EXCLUDED.source,
                      severity=EXCLUDED.severity,
                      embedding=EXCLUDED.embedding,
                      metadata=EXCLUDED.metadata
                    """,
                    (
                        f"{args.split}:{row['id']}",
                        row["text"],
                        row["label"],
                        json.dumps(row["labels"]),
                        row["family"],
                        row["variant"],
                        row["language"],
                        row["source"],
                        row["severity"],
                        literal,
                        json.dumps({
                            "split": args.split,
                            "originalId": row["id"]
                        }),
                    ),
                )
            conn.commit()

            if i == 1 or i % 25 == 0 or i == len(rows):
                elapsed = (time.perf_counter() - start) * 1000
                print(f"{i}/{len(rows)} indexed; last embedding {elapsed:.0f} ms")

        print(f"Indexed {len(rows)} {args.split} rows.")
    finally:
        conn.close()

if __name__ == "__main__":
    main()
