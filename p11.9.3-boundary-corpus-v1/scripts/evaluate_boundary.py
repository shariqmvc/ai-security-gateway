import argparse
import json
import os
import time
from collections import Counter
from pathlib import Path

import requests
import psycopg2

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data" / "boundary_v1.jsonl"
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
    p.add_argument("--top-k", type=int, default=5)
    p.add_argument("--limit", type=int, default=0)
    args = p.parse_args()

    rows = [json.loads(x) for x in DATA.read_text(encoding="utf-8").splitlines() if x.strip()]
    if args.limit:
        rows = rows[:args.limit]

    conn = psycopg2.connect(
        host=os.getenv("PGHOST", "localhost"),
        port=int(os.getenv("PGPORT", "5433")),
        dbname=os.getenv("PGDATABASE", "aegisai"),
        user=os.getenv("PGUSER", "postgres"),
        password=os.getenv("PGPASSWORD", "postgres"),
    )

    results = []
    try:
        for i,row in enumerate(rows,1):
            t0=time.perf_counter()
            vec=embed(row["text"])
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT corpus_id, primary_label, family, similarity
                    FROM search_personal_security_semantic_examples(%s::vector,%s)
                    """,(vec,args.top_k)
                )
                hits=cur.fetchall()
            latency=(time.perf_counter()-t0)*1000
            top=hits[0] if hits else None
            results.append({
                "id":row["id"],
                "expected":row["label"],
                "contrast":row["contrast"],
                "family":row["family"],
                "predicted":top[1] if top else None,
                "similarity":float(top[3]) if top else 0.0,
                "topK":[{"label":h[1],"family":h[2],"similarity":float(h[3])} for h in hits],
                "latencyMs":latency
            })
            if i==1 or i%10==0 or i==len(rows):
                print(f"{i}/{len(rows)} evaluated")

        exact=sum(r["predicted"]==r["expected"] for r in results)
        boundary=sum(r["predicted"]==r["contrast"] for r in results)
        family_correct=sum(
            bool(r["topK"]) and r["family"] in {x["family"] for x in r["topK"]}
            for r in results
        )
        report={
            "examples":len(results),
            "top1Accuracy":exact/len(results),
            "contrastHitRate":boundary/len(results),
            "familyInTopKRate":family_correct/len(results),
            "results":results
        }
        out=ROOT/"results"/"boundary_evaluation.json"
        out.parent.mkdir(exist_ok=True)
        out.write_text(json.dumps(report,indent=2),encoding="utf-8")
        print("\nP11.9.3 boundary evaluation")
        print("="*35)
        print(f"Examples:             {len(results)}")
        print(f"Top-1 expected label: {exact/len(results):.4f}")
        print(f"Top-1 contrast label: {boundary/len(results):.4f}")
        print(f"Family appears Top-K: {family_correct/len(results):.4f}")
        print(f"Saved: {out}")
    finally:
        conn.close()

if __name__=="__main__":
    main()
