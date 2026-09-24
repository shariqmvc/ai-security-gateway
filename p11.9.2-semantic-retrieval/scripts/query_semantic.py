import argparse
import json
import os

import requests
import psycopg2

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

def main():
    p = argparse.ArgumentParser()
    p.add_argument("text")
    p.add_argument("--top-k", type=int, default=5)
    args = p.parse_args()

    vec = embed(args.text)

    conn = psycopg2.connect(
        host=os.getenv("PGHOST", "localhost"),
        port=int(os.getenv("PGPORT", "5433")),
        dbname=os.getenv("PGDATABASE", "aegisai"),
        user=os.getenv("PGUSER", "postgres"),
        password=os.getenv("PGPASSWORD", "postgres"),
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT corpus_id, text, primary_label, labels,
                       family, similarity
                FROM search_personal_security_semantic_examples(%s::vector, %s)
                """,
                (vec, args.top_k),
            )
            rows = cur.fetchall()

        for rank, row in enumerate(rows, 1):
            corpus_id, text, label, labels, family, similarity = row
            print(f"\n#{rank} similarity={similarity:.4f}")
            print(f"label={label} labels={labels} family={family}")
            print(f"text={text}")
    finally:
        conn.close()

if __name__ == "__main__":
    main()
