import argparse
import csv
import json
import statistics
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

import requests


def parse_args():
    parser = argparse.ArgumentParser(
        description="AegisAI two-tenant concurrent benchmark"
    )

    parser.add_argument(
        "--url",
        required=True,
        help="AegisAI API endpoint"
    )

    parser.add_argument(
        "--body",
        required=True,
        help="JSON request body file"
    )

    parser.add_argument(
        "--tenant-a-key",
        required=True,
        help="API key for Tenant A"
    )

    parser.add_argument(
        "--tenant-b-key",
        required=True,
        help="API key for Tenant B"
    )

    parser.add_argument(
        "--tenant-a-name",
        default="TENANT-A",
        help="Tenant A label"
    )

    parser.add_argument(
        "--tenant-b-name",
        default="TENANT-B",
        help="Tenant B label"
    )

    parser.add_argument(
        "--requests-per-tenant",
        type=int,
        default=10,
        help="Requests per tenant"
    )

    parser.add_argument(
        "--concurrency",
        type=int,
        default=2,
        help="Maximum simultaneous requests"
    )

    parser.add_argument(
        "--timeout",
        type=float,
        default=120,
        help="HTTP timeout in seconds"
    )

    parser.add_argument(
        "--csv",
        default="aegisai-multitenant-c2-same.csv",
        help="Output CSV"
    )

    parser.add_argument(
        "--different-prompts",
        action="store_true",
        help="Use different request bodies for Tenant A and Tenant B"
    )

    return parser.parse_args()


def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_bodies(path, different_prompts):
    body_a = load_json(path)

    if not different_prompts:
        return body_a, body_a

    # Optional support for a body file containing:
    #
    # {
    #   "tenantA": {...},
    #   "tenantB": {...}
    # }
    #
    # If those keys don't exist, use the same body for both.
    if isinstance(body_a, dict):
        if "tenantA" in body_a and "tenantB" in body_a:
            return body_a["tenantA"], body_a["tenantB"]

    print(
        "WARNING: --different-prompts was specified but request.json "
        "does not contain tenantA/tenantB bodies. Using the same body."
    )

    return body_a, body_a


def percentile(values, p):
    if not values:
        return None

    values = sorted(values)

    if len(values) == 1:
        return values[0]

    index = (len(values) - 1) * p
    lower = int(index)
    upper = min(lower + 1, len(values) - 1)

    if lower == upper:
        return values[lower]

    weight = index - lower

    return (
        values[lower]
        + (values[upper] - values[lower]) * weight
    )


def execute_request(
    request_number,
    tenant_name,
    api_key,
    body,
    url,
    timeout
):
    request_id = None

    start = time.perf_counter()
    started_at = datetime.now(timezone.utc).isoformat()

    status_code = None
    response_body = ""
    error = ""

    try:
        response = requests.post(
            url,
            headers={
                "Content-Type": "application/json",
                "X-API-Key": api_key,
            },
            json=body,
            timeout=timeout,
        )

        status_code = response.status_code
        response_body = response.text[:1000]

        # Try to capture a gateway request ID if returned in headers.
        request_id = (
            response.headers.get("X-Request-ID")
            or response.headers.get("X-Request-Id")
            or response.headers.get("requestId")
        )

    except requests.exceptions.Timeout as exc:
        error = f"TIMEOUT: {exc}"

    except requests.exceptions.RequestException as exc:
        error = f"REQUEST_ERROR: {exc}"

    except Exception as exc:
        error = f"ERROR: {exc}"

    elapsed_ms = (time.perf_counter() - start) * 1000

    success = status_code is not None and 200 <= status_code < 300

    return {
        "request_number": request_number,
        "tenant": tenant_name,
        "request_id": request_id or "",
        "started_at": started_at,
        "status_code": status_code or "",
        "success": success,
        "latency_ms": round(elapsed_ms, 2),
        "error": error,
        "response_preview": response_body,
    }


def main():
    args = parse_args()

    body_a, body_b = load_bodies(
        args.body,
        args.different_prompts
    )

    tenants = [
        {
            "name": args.tenant_a_name,
            "key": args.tenant_a_key,
            "body": body_a,
        },
        {
            "name": args.tenant_b_name,
            "key": args.tenant_b_key,
            "body": body_b,
        },
    ]

    jobs = []

    request_number = 0

    for tenant in tenants:
        for _ in range(args.requests_per_tenant):
            request_number += 1

            jobs.append(
                (
                    request_number,
                    tenant["name"],
                    tenant["key"],
                    tenant["body"],
                )
            )

    total_requests = len(jobs)

    print()
    print("=" * 70)
    print("AegisAI Multi-Tenant Concurrency Benchmark")
    print("=" * 70)
    print(f"URL:                  {args.url}")
    print(f"Tenant A:             {args.tenant_a_name}")
    print(f"Tenant B:             {args.tenant_b_name}")
    print(f"Requests / tenant:    {args.requests_per_tenant}")
    print(f"Total requests:       {total_requests}")
    print(f"Concurrency:          {args.concurrency}")
    print(f"Timeout:              {args.timeout}s")
    print(
        f"Prompt mode:          "
        f"{'DIFFERENT' if args.different_prompts else 'SAME'}"
    )
    print("=" * 70)
    print()

    results = []

    benchmark_start = time.perf_counter()

    with ThreadPoolExecutor(
        max_workers=args.concurrency
    ) as executor:

        futures = []

        for request_number, tenant_name, api_key, body in jobs:
            futures.append(
                executor.submit(
                    execute_request,
                    request_number,
                    tenant_name,
                    api_key,
                    body,
                    args.url,
                    args.timeout,
                )
            )

        for future in as_completed(futures):
            result = future.result()
            results.append(result)

            status = (
                "SUCCESS"
                if result["success"]
                else f"FAILED ({result['status_code'] or result['error']})"
            )

            print(
                f"[{result['tenant']}] "
                f"request={result['request_number']:03d} "
                f"{status:<25} "
                f"latency={result['latency_ms']:.2f} ms"
            )

    total_elapsed = time.perf_counter() - benchmark_start

    # ------------------------------------------------------------------
    # Summary
    # ------------------------------------------------------------------

    total = len(results)
    successes = sum(r["success"] for r in results)
    failures = total - successes

    latencies = [
        r["latency_ms"]
        for r in results
    ]

    success_latencies = [
        r["latency_ms"]
        for r in results
        if r["success"]
    ]

    throughput = (
        total / total_elapsed
        if total_elapsed > 0
        else 0
    )

    print()
    print("=" * 70)
    print("OVERALL RESULT")
    print("=" * 70)

    print(f"Requests:       {total}")
    print(f"Successes:      {successes}")
    print(f"Failures:       {failures}")
    print(
        f"Success rate:   {(successes / total) * 100:.2f}%"
        if total
        else "Success rate:   0%"
    )
    print(f"Total time:     {total_elapsed:.2f}s")
    print(f"Throughput:     {throughput:.4f} req/s")

    if latencies:
        print(f"Overall P50:    {percentile(latencies, .50):.2f} ms")
        print(f"Overall P95:    {percentile(latencies, .95):.2f} ms")
        print(f"Overall P99:    {percentile(latencies, .99):.2f} ms")
        print(f"Overall Max:    {max(latencies):.2f} ms")

    if success_latencies:
        print()
        print("Successful requests:")
        print(
            f"Success P50:    "
            f"{percentile(success_latencies, .50):.2f} ms"
        )
        print(
            f"Success P95:    "
            f"{percentile(success_latencies, .95):.2f} ms"
        )
        print(
            f"Success P99:    "
            f"{percentile(success_latencies, .99):.2f} ms"
        )
        print(
            f"Success Max:    "
            f"{max(success_latencies):.2f} ms"
        )

    # ------------------------------------------------------------------
    # Per-tenant summary
    # ------------------------------------------------------------------

    print()
    print("=" * 70)
    print("PER-TENANT RESULT")
    print("=" * 70)

    for tenant_name in [
        args.tenant_a_name,
        args.tenant_b_name,
    ]:

        tenant_results = [
            r
            for r in results
            if r["tenant"] == tenant_name
        ]

        tenant_success = sum(
            r["success"]
            for r in tenant_results
        )

        tenant_latencies = [
            r["latency_ms"]
            for r in tenant_results
        ]

        print()
        print(f"Tenant: {tenant_name}")
        print(f"  Requests:     {len(tenant_results)}")
        print(f"  Successes:    {tenant_success}")
        print(
            f"  Failures:     "
            f"{len(tenant_results) - tenant_success}"
        )

        if tenant_results:
            print(
                f"  Success rate: "
                f"{tenant_success / len(tenant_results) * 100:.2f}%"
            )

        if tenant_latencies:
            print(
                f"  P50:          "
                f"{percentile(tenant_latencies, .50):.2f} ms"
            )
            print(
                f"  P95:          "
                f"{percentile(tenant_latencies, .95):.2f} ms"
            )
            print(
                f"  Max:          "
                f"{max(tenant_latencies):.2f} ms"
            )

    # ------------------------------------------------------------------
    # CSV
    # ------------------------------------------------------------------

    fieldnames = [
        "request_number",
        "tenant",
        "request_id",
        "started_at",
        "status_code",
        "success",
        "latency_ms",
        "error",
        "response_preview",
    ]

    with open(
        args.csv,
        "w",
        newline="",
        encoding="utf-8",
    ) as f:

        writer = csv.DictWriter(
            f,
            fieldnames=fieldnames
        )

        writer.writeheader()

        for result in sorted(
            results,
            key=lambda x: x["request_number"]
        ):
            writer.writerow(result)

    print()
    print("=" * 70)
    print(f"CSV written to: {args.csv}")
    print("=" * 70)


if __name__ == "__main__":
    main()