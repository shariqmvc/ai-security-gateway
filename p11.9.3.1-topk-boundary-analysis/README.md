# P11.9.3.1 — Top-K Boundary Analysis

Purpose:
Measure whether the intended semantic security category exists anywhere in
the Top-K nearest-neighbor neighborhood, even when it is not the nearest
single example.

This package queries the existing P11.9.2 530-example train index.
It does NOT insert the boundary corpus into PGVector and does NOT change
production AIRouter behavior.

Metrics:
- Top-1 expected label hit
- Top-3 expected label hit
- Top-5 expected label hit
- Top-1 contrast label hit
- Top-3 contrast label hit
- Top-5 contrast label hit
- expected-vs-contrast similarity margin
- per-boundary-family results

The analysis deliberately maps boundary families to their indexed security
taxonomy instead of comparing the synthetic boundary-family strings to
training family names.

## Self-contained corpus

`data/boundary_v1.jsonl` is bundled in this package. No dependency on the
previous P11.9.3 directory is required.
