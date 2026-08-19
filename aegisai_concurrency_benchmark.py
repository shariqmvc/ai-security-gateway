#!/usr/bin/env python3
"""
AegisAI B.5-P4.2 Runtime Concurrency Benchmark

Standard-library-only HTTP concurrency benchmark for the AegisAI gateway.

Examples (PowerShell / CMD):
    python aegisai_concurrency_benchmark.py ^
      --url http://localhost:8080/v1/chat/completions ^
      --body request.json ^
      --concurrency 10 ^
      --requests 100 ^
      --header "Authorization: Bearer YOUR_TOKEN"

Git Bash:
    python aegisai_concurrency_benchmark.py \
      --url http://localhost:8080/v1/chat/completions \
      --body request.json \
      --concurrency 10 \
      --requests 100 \
      --header "Authorization: Bearer YOUR_TOKEN"

The script measures client-observed end-to-end latency. Correlate its
request IDs with logs/aegisai-performance.log when the gateway exposes
the request ID in an HTTP response header.

No AegisAI production code is modified by this script.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import csv
import json
import math
import statistics
import threading
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass, asdict
from pathlib import Path


@dataclass
class Result:
    sequence: int
    request_id: str
    status_code: int | None
    latency_ms: float
    success: bool
    error: str = ""


def percentile(values: list[float], p: float) -> float:
    if not values:
        return float("nan")
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]

    rank = (len(ordered) - 1) * p / 100.0
    lower = math.floor(rank)
    upper = math.ceil(rank)

    if lower == upper:
        return ordered[lower]

    fraction = rank - lower
    return ordered[lower] + (ordered[upper] - ordered[lower]) * fraction


def parse_headers(items: list[str]) -> dict[str, str]:
    headers = {}
    for item in items:
        if ":" not in item:
            raise ValueError(
                f"Invalid header '{item}'. Expected: Header-Name: value"
            )
        name, value = item.split(":", 1)
        headers[name.strip()] = value.strip()
    return headers


def load_body(path: str | None, inline: str | None) -> bytes | None:
    if path and inline:
        raise ValueError("Use either --body or --body-json, not both.")

    if path:
        return Path(path).read_bytes()

    if inline:
        # Validate that the supplied value is valid JSON, while preserving
        # the exact UTF-8 representation sent over HTTP.
        json.loads(inline)
        return inline.encode("utf-8")

    return None


def build_request(
    url: str,
    body: bytes | None,
    headers: dict[str, str],
    request_id_header: str,
    request_id: str,
) -> urllib.request.Request:
    request_headers = dict(headers)
    if body is not None and not any(
        key.lower() == "content-type" for key in request_headers
    ):
        request_headers["Content-Type"] = "application/json"

    # A unique client request ID makes correlation easier in gateway logs.
    request_headers[request_id_header] = request_id

    return urllib.request.Request(
        url=url,
        data=body,
        headers=request_headers,
        method="POST",
    )


def execute_one(
    sequence: int,
    start_gate: threading.Event,
    url: str,
    body: bytes | None,
    headers: dict[str, str],
    request_id_header: str,
    timeout: float,
) -> Result:
    request_id = str(uuid.uuid4())

    # Synchronize worker start so concurrency is closer to simultaneous
    # rather than staggered thread submission.
    start_gate.wait()

    request = build_request(
        url=url,
        body=body,
        headers=headers,
        request_id_header=request_id_header,
        request_id=request_id,
    )

    started = time.perf_counter()

    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            # Read the response body so the connection is fully consumed and
            # returned to the underlying HTTP stack where applicable.
            response.read()
            status = response.status

        elapsed = (time.perf_counter() - started) * 1000.0
        return Result(
            sequence=sequence,
            request_id=request_id,
            status_code=status,
            latency_ms=elapsed,
            success=200 <= status < 300,
        )

    except urllib.error.HTTPError as exc:
        # HTTPError is also a valid HTTP response. Record its status and
        # latency instead of treating it as a transport exception.
        try:
            exc.read()
        except Exception:
            pass

        elapsed = (time.perf_counter() - started) * 1000.0
        return Result(
            sequence=sequence,
            request_id=request_id,
            status_code=exc.code,
            latency_ms=elapsed,
            success=False,
            error=f"HTTP {exc.code}",
        )

    except Exception as exc:
        elapsed = (time.perf_counter() - started) * 1000.0
        return Result(
            sequence=sequence,
            request_id=request_id,
            status_code=None,
            latency_ms=elapsed,
            success=False,
            error=f"{type(exc).__name__}: {exc}",
        )


def run_benchmark(args: argparse.Namespace) -> list[Result]:
    body = load_body(args.body, args.body_json)
    headers = parse_headers(args.header)

    print(f"URL:         {args.url}")
    print(f"Concurrency: {args.concurrency}")
    print(f"Requests:    {args.requests}")
    print(f"Timeout:     {args.timeout:.1f}s")
    print()
    print("Starting synchronized workers...")

    gate = threading.Event()
    results: list[Result] = []

    benchmark_started = time.perf_counter()

    with concurrent.futures.ThreadPoolExecutor(
        max_workers=args.concurrency
    ) as executor:
        futures = [
            executor.submit(
                execute_one,
                sequence=i,
                start_gate=gate,
                url=args.url,
                body=body,
                headers=headers,
                request_id_header=args.request_id_header,
                timeout=args.timeout,
            )
            for i in range(1, args.requests + 1)
        ]

        # Release all currently queued workers at once.
        gate.set()

        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())

    total_elapsed_ms = (time.perf_counter() - benchmark_started) * 1000.0

    results.sort(key=lambda r: r.sequence)
    print_summary(results, total_elapsed_ms, args.concurrency)

    if args.csv:
        write_csv(args.csv, results)
        print(f"\nCSV written to: {args.csv}")

    return results


def print_summary(
    results: list[Result],
    total_elapsed_ms: float,
    concurrency: int,
) -> None:
    latencies = [r.latency_ms for r in results]
    successes = [r for r in results if r.success]
    failures = [r for r in results if not r.success]

    if not latencies:
        print("No results.")
        return

    successful_latencies = [r.latency_ms for r in successes]

    print("\n" + "=" * 64)
    print("AegisAI B.5-P4.2 CONCURRENCY BENCHMARK")
    print("=" * 64)
    print(f"Requests:            {len(results)}")
    print(f"Concurrency:         {concurrency}")
    print(f"Successful:          {len(successes)}")
    print(f"Failed:              {len(failures)}")
    print(f"Error rate:          {len(failures) / len(results) * 100:.2f}%")
    print(f"Wall-clock time:     {total_elapsed_ms:.2f} ms")

    if total_elapsed_ms > 0:
        throughput = len(results) / (total_elapsed_ms / 1000.0)
        print(f"Throughput:          {throughput:.3f} req/s")

    print("\nClient-observed latency:")
    print(f"  Min:               {min(latencies):.2f} ms")
    print(f"  P50:               {percentile(latencies, 50):.2f} ms")
    print(f"  P95:               {percentile(latencies, 95):.2f} ms")
    print(f"  P99:               {percentile(latencies, 99):.2f} ms")
    print(f"  Max:               {max(latencies):.2f} ms")
    print(f"  Mean:              {statistics.mean(latencies):.2f} ms")

    if successful_latencies:
        print("\nSuccessful-request latency:")
        print(f"  P50:               {percentile(successful_latencies, 50):.2f} ms")
        print(f"  P95:               {percentile(successful_latencies, 95):.2f} ms")
        print(f"  P99:               {percentile(successful_latencies, 99):.2f} ms")

    if failures:
        print("\nFailure breakdown:")
        breakdown: dict[str, int] = {}
        for result in failures:
            key = result.error or f"HTTP {result.status_code}"
            breakdown[key] = breakdown.get(key, 0) + 1

        for error, count in sorted(
            breakdown.items(), key=lambda item: (-item[1], item[0])
        ):
            print(f"  {count:>4} × {error}")

    print("=" * 64)


def write_csv(path: str, results: list[Result]) -> None:
    with open(path, "w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "sequence",
                "request_id",
                "status_code",
                "latency_ms",
                "success",
                "error",
            ],
        )
        writer.writeheader()
        for result in results:
            writer.writerow(asdict(result))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="AegisAI concurrent HTTP benchmark"
    )

    parser.add_argument(
        "--url",
        required=True,
        help="AegisAI POST endpoint",
    )
    parser.add_argument(
        "--body",
        help="Path to JSON request body file",
    )
    parser.add_argument(
        "--body-json",
        help="Inline JSON request body",
    )
    parser.add_argument(
        "--concurrency",
        type=int,
        default=10,
        help="Maximum concurrent requests (default: 10)",
    )
    parser.add_argument(
        "--requests",
        type=int,
        default=100,
        help="Total requests to execute (default: 100)",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=120.0,
        help="Client-side timeout per request in seconds (default: 120)",
    )
    parser.add_argument(
        "--header",
        action="append",
        default=[],
        help="HTTP header. Repeat for multiple headers.",
    )
    parser.add_argument(
        "--request-id-header",
        default="X-Request-ID",
        help="Header used for benchmark request correlation (default: X-Request-ID)",
    )
    parser.add_argument(
        "--csv",
        help="Optional CSV output path",
    )

    args = parser.parse_args()

    if args.concurrency < 1:
        parser.error("--concurrency must be >= 1")
    if args.requests < 1:
        parser.error("--requests must be >= 1")
    if args.timeout <= 0:
        parser.error("--timeout must be > 0")

    return args


def main() -> None:
    args = build_parser()
    run_benchmark(args)


if __name__ == "__main__":
    main()
