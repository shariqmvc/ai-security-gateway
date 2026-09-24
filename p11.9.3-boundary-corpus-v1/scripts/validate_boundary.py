import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "data" / "boundary_v1.jsonl"

REQUIRED = {"id","text","label","contrast","family","variant"}
ALLOWED = {
    "BENIGN","HARD_BENIGN","PROMPT_INJECTION","JAILBREAK",
    "SYSTEM_PROMPT_EXTRACTION","DATA_EXFILTRATION","INDIRECT_INJECTION",
    "TOOL_MANIPULATION","RAG_POISONING","OBFUSCATED_ATTACK",
    "MULTILINGUAL_ATTACK"
}

def main():
    rows = [json.loads(x) for x in PATH.read_text(encoding="utf-8").splitlines() if x.strip()]
    ids = [r["id"] for r in rows]
    assert len(ids) == len(set(ids)), "Duplicate IDs"
    for r in rows:
        missing = REQUIRED - r.keys()
        assert not missing, f"{r.get('id')}: missing {missing}"
        assert r["label"] in ALLOWED
        assert r["contrast"] in ALLOWED
        assert r["label"] != r["contrast"]
    families = Counter(r["family"] for r in rows)
    print(f"Boundary examples: {len(rows)}")
    print("Labels:")
    for k,v in Counter(r["label"] for r in rows).most_common():
        print(f"  {k}: {v}")
    print("Families:")
    for k,v in families.most_common():
        print(f"  {k}: {v}")
    print("Validation: PASS")

if __name__ == "__main__":
    main()
