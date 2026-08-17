#!/usr/bin/env python3
"""Summarize gateway overhead from aegisai-performance.log."""

import re
import statistics
import sys
from pathlib import Path

pattern = re.compile(
    r"event=REQUEST_COMPLETED .*?totalLatencyMs=(\d+) "
    r"providerLatencyMs=(\d+|null) gatewayOverheadMs=(\d+|null) outcome=(\S+)"
)

path = Path(sys.argv[1] if len(sys.argv) > 1 else "logs/aegisai-performance.log")
values = []
rows = []

for line in path.read_text(errors="replace").splitlines():
    m = pattern.search(line)
    if not m:
        continue
    total, provider, gateway, outcome = m.groups()
    if gateway == "null":
        continue
    gateway_ms = int(gateway)
    values.append(gateway_ms)
    rows.append((int(total), None if provider == "null" else int(provider), gateway_ms, outcome))

if not values:
    print("No REQUEST_COMPLETED records with gatewayOverheadMs found.")
    sys.exit(1)

values_sorted = sorted(values)
def percentile(p):
    index = min(len(values_sorted) - 1, max(0, round((p / 100) * (len(values_sorted) - 1))))
    return values_sorted[index]

print(f"samples={len(values)}")
print(f"min={min(values)}ms")
print(f"p50={percentile(50)}ms")
print(f"p95={percentile(95)}ms")
print(f"p99={percentile(99)}ms")
print(f"max={max(values)}ms")
print(f"avg={statistics.mean(values):.2f}ms")
