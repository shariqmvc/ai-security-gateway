import argparse
import json
import os
import statistics
import time
from pathlib import Path

import requests
import psycopg2

ROOT = Path(__file__).resolve().parents[1]
TEST = ROOT / "data" / "test.jsonl"
BOUNDARY = ROOT / "data" / "boundary_v1.jsonl"
OLLAMA = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
MODEL = os.getenv("OLLAMA_EMBED_MODEL", "nomic-embed-text")

ATTACKS = {
    "PROMPT_INJECTION","JAILBREAK","SYSTEM_PROMPT_EXTRACTION",
    "DATA_EXFILTRATION","INDIRECT_INJECTION","TOOL_MANIPULATION",
    "RAG_POISONING","OBFUSCATED_ATTACK","MULTILINGUAL_ATTACK"
}
BENIGN = {"BENIGN","HARD_BENIGN"}

CONCEPT_MAP = {
    "PROMPT_INJECTION": {"PROMPT_INJECTION","INDIRECT_INJECTION"},
    "INDIRECT_INJECTION": {"INDIRECT_INJECTION","PROMPT_INJECTION"},
    "RAG_POISONING": {"RAG_POISONING","INDIRECT_INJECTION"},
    "HARD_BENIGN": {"BENIGN","HARD_BENIGN"},
    "BENIGN": {"BENIGN","HARD_BENIGN"},
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

def score(hits, floor, decay):
    raw = {}
    for rank, h in enumerate(hits, 1):
        w = max(0.0, (float(h[3]) - floor) / (1.0-floor))
        raw[h[1]] = raw.get(h[1], 0.0) + w * (decay ** (rank-1))
    ordered = sorted(raw.items(), key=lambda x:x[1], reverse=True)
    total=sum(raw.values())
    return {
        "top": ordered[0][0] if ordered else None,
        "raw": dict(ordered),
        "normalized": {k:v/total for k,v in ordered} if total else {},
        "margin": ordered[0][1]-ordered[1][1] if len(ordered)>1 else (ordered[0][1] if ordered else 0)
    }

def run_dataset(conn, path, top_k, floor, decay):
    rows=[json.loads(x) for x in path.read_text(encoding="utf-8").splitlines() if x.strip()]
    out=[]
    for i,row in enumerate(rows,1):
        vec=embed(row["text"])
        with conn.cursor() as cur:
            cur.execute(
                """SELECT corpus_id,primary_label,family,similarity
                   FROM search_personal_security_semantic_examples(%s::vector,%s)""",
                (vec,top_k)
            )
            hits=cur.fetchall()
        s=score(hits,floor,decay)
        out.append({
            "id":row["id"], "expected":row["label"],
            "contrast":row.get("contrast"),
            "top":s["top"], "raw":s["raw"],
            "normalized":s["normalized"], "margin":s["margin"],
            "hits":[{"label":h[1],"family":h[2],"similarity":float(h[3])} for h in hits]
        })
        if i==1 or i%10==0 or i==len(rows):
            print(f"{i}/{len(rows)} evaluated")
    return out

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--top-k",type=int,default=5)
    p.add_argument("--similarity-floor",type=float,default=0.65)
    p.add_argument("--rank-decay",type=float,default=0.85)
    args=p.parse_args()

    conn=psycopg2.connect(
        host=os.getenv("PGHOST","localhost"),
        port=int(os.getenv("PGPORT","5433")),
        dbname=os.getenv("PGDATABASE","aegisai"),
        user=os.getenv("PGUSER","postgres"),
        password=os.getenv("PGPASSWORD","postgres"),
    )
    try:
        print("Evaluating independent 120-example test set")
        test=run_dataset(conn,TEST,args.top_k,args.similarity_floor,args.rank_decay)
        print("\nEvaluating 35-example boundary corpus")
        boundary=run_dataset(conn,BOUNDARY,args.top_k,args.similarity_floor,args.rank_decay)

        n=len(test)
        exact=sum(x["top"]==x["expected"] for x in test)
        attack=[x for x in test if x["expected"] in ATTACKS]
        benign=[x for x in test if x["expected"] in BENIGN]
        attack_detect=sum(x["top"] in ATTACKS for x in attack)
        benign_fp=sum(x["top"] in ATTACKS for x in benign)

        bn=len(boundary)
        b_expected=sum(
            x["top"] in CONCEPT_MAP.get(x["expected"],{x["expected"]})
            for x in boundary
        )
        b_contrast=sum(
            x["top"] in CONCEPT_MAP.get(x["contrast"],{x["contrast"]})
            for x in boundary
        )

        print("\nP11.9.4 SEMANTIC EVIDENCE SCORER")
        print("="*40)
        print(f"Test examples:                 {n}")
        print(f"Test Top-1 evidence accuracy:  {exact/n:.4f}")
        print(f"Test attack detection recall:  {attack_detect/len(attack):.4f}")
        print(f"Test benign false-positive:    {benign_fp/len(benign):.4f}")
        print(f"Boundary examples:             {bn}")
        print(f"Boundary expected Top-1:       {b_expected/bn:.4f}")
        print(f"Boundary contrast Top-1:       {b_contrast/bn:.4f}")

        out=ROOT/"results"
        out.mkdir(exist_ok=True)
        payload={
            "config":{
                "topK":args.top_k,
                "similarityFloor":args.similarity_floor,
                "rankDecay":args.rank_decay
            },
            "test":{
                "examples":n,
                "top1Accuracy":exact/n,
                "attackRecall":attack_detect/len(attack),
                "benignFalsePositiveRate":benign_fp/len(benign),
                "results":test
            },
            "boundary":{
                "examples":bn,
                "expectedTop1":b_expected/bn,
                "contrastTop1":b_contrast/bn,
                "results":boundary
            }
        }
        path=out/"evidence_evaluation.json"
        path.write_text(json.dumps(payload,indent=2),encoding="utf-8")
        print(f"Saved: {path}")
    finally:
        conn.close()

if __name__=="__main__":
    main()
