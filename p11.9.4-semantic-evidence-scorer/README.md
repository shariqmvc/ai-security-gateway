# P11.9.4 — Semantic Evidence Scorer

Offline experiment only.

Purpose:
Aggregate Top-K PGVector semantic neighbors into label-level evidence instead
of treating the single nearest neighbor as the classification decision.

Production AIRouter code is NOT modified.

Inputs:
- Existing P11.9.2 530-example train index in PostgreSQL/PGVector
- Original P11.9.1 independent test set (120 examples)
- P11.9.3 boundary corpus (35 examples)

Scoring:
For each retrieved neighbor with cosine similarity s, convert similarity to
a bounded evidence weight:

    weight = max(0, (s - floor) / (1 - floor))

Then apply rank decay:

    contribution = weight * rank_decay^(rank-1)

The scorer sums contributions by operational label and normalizes only for
presentation. These are evidence scores, NOT calibrated probabilities.

Default parameters:
- top-k = 5
- similarity floor = 0.65
- rank decay = 0.85

These parameters are experimental and must not be used for production
blocking.

The evaluator reports:
- Top-1 label accuracy
- top-k evidence label hit
- attack-vs-benign evidence detection
- benign false-positive rate
- boundary expected-label evidence hit
- boundary contrast-label evidence hit
- score margins
