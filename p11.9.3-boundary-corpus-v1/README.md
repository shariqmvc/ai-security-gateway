# P11.9.3 — Semantic Boundary / Contrastive Evaluation

Purpose:
Create a focused boundary corpus from the actual P11.9.2.1 failure modes.

This is an OFFLINE evaluation phase.
It does not modify the production firewall.

Boundary targets:
1. HARD_BENIGN vs PROMPT_INJECTION
2. HARD_BENIGN vs DATA_EXFILTRATION
3. INDIRECT_INJECTION vs SYSTEM_PROMPT_EXTRACTION
4. RAG_POISONING vs PROMPT_INJECTION
5. Attack vs benign discussion
6. Multilingual/obfuscated semantic boundaries

The corpus contains contrastive examples designed around the observed
confusion classes, rather than simply adding more obvious attack examples.

Workflow:
1. validate the generated boundary corpus
2. embed boundary examples using nomic-embed-text
3. query against the existing P11.9.2 train index
4. measure whether the boundary examples retrieve the intended category
5. do not alter production code or thresholds
