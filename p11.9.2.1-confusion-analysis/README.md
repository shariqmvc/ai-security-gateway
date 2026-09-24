# P11.9.2.1 — Semantic Retrieval Confusion Analysis

Run this after P11.9.2 baseline evaluation. It is diagnostic only and does not modify the production firewall.

Inputs the existing `p11.9.2-semantic-retrieval/results/retrieval_baseline.json` and produces:
- top-1 confusion matrix
- per-class precision/recall/F1
- attack-vs-benign metrics
- cross-class errors
- benign false positives
- attacks misread as benign
- similarity distributions

It also contains a separate latency benchmark that measures embedding and PGVector search independently.
