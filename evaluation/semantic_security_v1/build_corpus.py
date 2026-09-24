from pathlib import Path
import json
from collections import Counter, defaultdict
ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/"data"/"semantic_security_v1"
REQUIRED={"id","text","label","labels","family","variant","language","source","severity"}
VALID={"BENIGN","PROMPT_INJECTION","JAILBREAK","SYSTEM_PROMPT_EXTRACTION","DATA_EXFILTRATION","INDIRECT_INJECTION","TOOL_MANIPULATION","RAG_POISONING","OBFUSCATED_ATTACK","MULTILINGUAL_ATTACK","HARD_BENIGN"}
splits={}
for name in ("train.jsonl","validation.jsonl","test.jsonl"):
    rows=[json.loads(x) for x in (DATA/name).read_text(encoding="utf-8").splitlines() if x.strip()]
    for r in rows:
        missing=REQUIRED-r.keys()
        if missing: raise ValueError(f"{name}: missing {sorted(missing)}")
        if r["label"] not in VALID: raise ValueError(f"{name}: invalid label {r['label']}")
    splits[name]=rows
allrows=sum(splits.values(),[])
assert len(allrows)==775
assert len({r["id"] for r in allrows})==775
assert len({r["text"] for r in allrows})==775
seen=defaultdict(set)
for s,rs in splits.items():
    for r in rs: seen[(r["label"],r["family"])].add(s)
leaks={k:v for k,v in seen.items() if len(v)>1}
if leaks: raise ValueError(f"family leakage: {leaks}")
for s,rs in splits.items():
    print(f"{s}: {len(rs)}")
    for k,v in sorted(Counter(r["label"] for r in rs).items()): print(f"  {k:28s} {v}")
print("\nFamily leakage: NONE")
print("Corpus validation: PASS")
