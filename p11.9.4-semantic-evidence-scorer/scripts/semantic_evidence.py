import argparse
import json
import os
from pathlib import Path

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

def score_hits(hits, top_k=5, similarity_floor=0.65, rank_decay=0.85):
    scores = {}
    for rank, hit in enumerate(hits[:top_k], start=1):
        label = hit["label"]
        sim = float(hit["similarity"])
        weight = max(0.0, (sim - similarity_floor) / (1.0 - similarity_floor))
        contribution = weight * (rank_decay ** (rank - 1))
        scores[label] = scores.get(label, 0.0) + contribution

    total = sum(scores.values())
    normalized = {
        label: value / total
        for label, value in scores.items()
    } if total > 0 else {}

    ordered = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return {
        "rawScores": dict(ordered),
        "normalizedEvidence": dict(
            sorted(normalized.items(), key=lambda x: x[1], reverse=True)
        ),
        "topLabel": ordered[0][0] if ordered else None,
        "topScore": ordered[0][1] if ordered else 0.0,
        "margin": (
            ordered[0][1] - ordered[1][1]
            if len(ordered) > 1 else ordered[0][1] if ordered else 0.0
        )
    }

def connect():
    return psycopg2.connect(
        host=os.getenv("PGHOST", "localhost"),
        port=int(os.getenv("PGPORT", "5433")),
        dbname=os.getenv("PGDATABASE", "aegisai"),
        user=os.getenv("PGUSER", "postgres"),
        password=os.getenv("PGPASSWORD", "postgres"),
    )

def retrieve(conn, text, top_k):
    vec = embed(text)
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT corpus_id, primary_label, family, similarity
            FROM search_personal_security_semantic_examples(%s::vector,%s)
            """,
            (vec, top_k),
        )
        rows = cur.fetchall()
    return [
        {
            "corpusId": r[0],
            "label": r[1],
            "family": r[2],
            "similarity": float(r[3])
        }
        for r in rows
    ]

def main():
    p = argparse.ArgumentParser()
    p.add_argument("text")
    p.add_argument("--top-k", type=int, default=5)
    p.add_argument("--similarity-floor", type=float, default=0.65)
    p.add_argument("--rank-decay", type=float, default=0.85)
    args = p.parse_args()

    conn = connect()
    try:
        hits = retrieve(conn, args.text, args.top_k)
        result = score_hits(
            hits,
            args.top_k,
            args.similarity_floor,
            args.rank_decay
        )
        print("P11.9.4 SEMANTIC EVIDENCE")
        print("=" * 34)
        print(json.dumps({
            "query": args.text,
            "topK": args.top_k,
            "similarityFloor": args.similarity_floor,
            "rankDecay": args.rank_decay,
            "evidence": result,
            "matches": hits
        }, indent=2))
    finally:
        conn.close()

if __name__ == "__main__":
    main()
