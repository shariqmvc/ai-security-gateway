import argparse
import csv
import json
import statistics
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

import requests


def percentile(values, p):
    if not values:
        return 0.0

    values = sorted(values)

    if len(values) == 1:
        return float(values[0])

    index = (len(values) - 1) * (p / 100.0)
    lower = int(index)
    upper = min(lower + 1, len(values))

    if lower == upper:
        return float(values[lower])

    fraction = index - lower

    return (
        values[lower]
        + (values[upper] - values[lower]) * fraction
    )


def now_iso():
    return datetime.now(timezone.utc).isoformat()


def load_bodies(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def build_request(
    url,
    api_key,
    tenant_name,
    body,
    request_number,
    round_number,
    tenant_label,
    timeout,
):
    """
    Execute one request.

    IMPORTANT:
    The server-generated X-Request-ID is captured from the response.
    This allows exact correlation with aegisai-performance.log.
    """

    client_start_ns = time.perf_counter_ns()
    client_start_wall = now_iso()

    client_request_id = str(uuid.uuid4())

    headers = {
        "X-API-Key": api_key,
        "Content-Type": "application/json",

        # Diagnostic correlation ID.
        # The gateway may preserve this or generate its own requestId.
        "X-Benchmark-Request-ID": client_request_id,
    }

    result = {
        "benchmark_request_id": client_request_id,
        "server_request_id": "",
        "tenant": tenant_name,
        "tenant_label": tenant_label,
        "round": round_number,
        "request_number": request_number,
        "client_start": client_start_wall,
        "client_end": "",
        "client_latency_ms": 0.0,
        "http_status": "",
        "success": False,
        "error_type": "",
        "error": "",
        "response_bytes": 0,
    }

    try:
        response = requests.post(
            url,
            headers=headers,
            json=body,
            timeout=timeout,
        )

        client_end_ns = time.perf_counter_ns()

        result["client_end"] = now_iso()

        result["client_latency_ms"] = round(
            (client_end_ns - client_start_ns) / 1_000_000,
            3,
        )

        result["http_status"] = response.status_code
        result["success"] = 200 <= response.status_code < 300
        result["response_bytes"] = len(response.content)

        # Try all likely request-ID headers.
        server_request_id = (
            response.headers.get("X-Request-ID")
            or response.headers.get("X-Request-Id")
            or response.headers.get("X-Correlation-ID")
            or response.headers.get("X-Correlation-Id")
            or ""
        )

        result["server_request_id"] = server_request_id

        if not result["success"]:
            result["error_type"] = "HTTP_ERROR"
            result["error"] = response.text[:1000]

    except requests.exceptions.Timeout as exc:

        client_end_ns = time.perf_counter_ns()

        result["client_end"] = now_iso()

        result["client_latency_ms"] = round(
            (client_end_ns - client_start_ns) / 1_000_000,
            3,
        )

        result["error_type"] = "CLIENT_TIMEOUT"
        result["error"] = str(exc)

    except requests.exceptions.RequestException as exc:

        client_end_ns = time.perf_counter_ns()

        result["client_end"] = now_iso()

        result["client_latency_ms"] = round(
            (client_end_ns - client_start_ns) / 1_000_000,
            3,
        )

        result["error_type"] = "REQUEST_EXCEPTION"
        result["error"] = str(exc)

    except Exception as exc:

        client_end_ns = time.perf_counter_ns()

        result["client_end"] = now_iso()

        result["client_latency_ms"] = round(
            (client_end_ns - client_start_ns) / 1_000_000,
            3,
        )

        result["error_type"] = type(exc).__name__
        result["error"] = str(exc)

    return result


def main():

    parser = argparse.ArgumentParser(
        description=(
            "AegisAI two-tenant concurrency benchmark v2 "
            "with request-ID correlation."
        )
    )

    parser.add_argument(
        "--url",
        required=True,
    )

    parser.add_argument(
        "--body",
        required=True,
        help="JSON file containing tenantA and tenantB request bodies",
    )

    parser.add_argument(
        "--tenant-a-key",
        required=True,
    )

    parser.add_argument(
        "--tenant-b-key",
        required=True,
    )

    parser.add_argument(
        "--tenant-a-name",
        default="POSTMAN-ISOLATION-A",
    )

    parser.add_argument(
        "--tenant-b-name",
        default="POSTMAN-ISOLATION-B",
    )

    parser.add_argument(
        "--rounds",
        type=int,
        default=25,
        help="Number of A/B rounds. 25 = 50 total requests.",
    )

    parser.add_argument(
        "--concurrency",
        type=int,
        default=2,
        help="Client-side concurrency.",
    )

    parser.add_argument(
        "--timeout",
        type=float,
        default=120,
    )

    parser.add_argument(
        "--csv",
        default="c2-v2.csv",
    )

    args = parser.parse_args()

    bodies = load_bodies(args.body)

    if "tenantA" not in bodies:
        raise ValueError(
            "Body JSON must contain tenantA"
        )

    if "tenantB" not in bodies:
        raise ValueError(
            "Body JSON must contain tenantB"
        )

    tenant_a_body = bodies["tenantA"]
    tenant_b_body = bodies["tenantB"]

    jobs = []

    request_number = 0

    for round_number in range(1, args.rounds + 1):

        # Tenant A
        request_number += 1

        jobs.append(
            {
                "url": args.url,
                "api_key": args.tenant_a_key,
                "tenant_name": args.tenant_a_name,
                "body": tenant_a_body,
                "request_number": request_number,
                "round_number": round_number,
                "tenant_label": "A",
                "timeout": args.timeout,
            }
        )

        # Tenant B
        request_number += 1

        jobs.append(
            {
                "url": args.url,
                "api_key": args.tenant_b_key,
                "tenant_name": args.tenant_b_name,
                "body": tenant_b_body,
                "request_number": request_number,
                "round_number": round_number,
                "tenant_label": "B",
                "timeout": args.timeout,
            }
        )

    print()
    print("=" * 72)
    print("AegisAI Multitenant Benchmark v2")
    print("=" * 72)
    print(f"URL:              {args.url}")
    print(f"Rounds:           {args.rounds}")
    print(f"Total requests:   {len(jobs)}")
    print(f"Client concurrency:{args.concurrency}")
    print(f"Timeout:          {args.timeout}s")
    print(f"CSV:              {args.csv}")
    print()
    print("Request-ID correlation enabled")
    print("=" * 72)
    print()

    results = []

    benchmark_start_ns = time.perf_counter_ns()

    with ThreadPoolExecutor(
        max_workers=args.concurrency
    ) as executor:

        futures = [
            executor.submit(
                build_request,
                **job
            )
            for job in jobs
        ]

        for future in as_completed(futures):

            result = future.result()

            results.append(result)

            status = (
                result["http_status"]
                if result["http_status"]
                else result["error_type"]
            )

            print(
                f"[{result['request_number']:03d}] "
                f"Tenant={result['tenant_label']} "
                f"Round={result['round']:02d} "
                f"status={status} "
                f"latency={result['client_latency_ms']:.0f}ms "
                f"requestId={result['server_request_id'] or 'NOT_RETURNED'}"
            )

    benchmark_end_ns = time.perf_counter_ns()

    total_benchmark_ms = (
        benchmark_end_ns - benchmark_start_ns
    ) / 1_000_000

    results.sort(
        key=lambda x: x["request_number"]
    )

    fieldnames = [
        "benchmark_request_id",
        "server_request_id",
        "tenant",
        "tenant_label",
        "round",
        "request_number",
        "client_start",
        "client_end",
        "client_latency_ms",
        "http_status",
        "success",
        "error_type",
        "error",
        "response_bytes",
    ]

    with open(
        args.csv,
        "w",
        newline="",
        encoding="utf-8",
    ) as f:

        writer = csv.DictWriter(
            f,
            fieldnames=fieldnames,
        )

        writer.writeheader()

        writer.writerows(results)

    successful = [
        r
        for r in results
        if r["success"]
    ]

    failed = [
        r
        for r in results
        if not r["success"]
    ]

    latencies = [
        r["client_latency_ms"]
        for r in results
    ]

    successful_latencies = [
        r["client_latency_ms"]
        for r in successful
    ]

    print()
    print("=" * 72)
    print("RESULTS")
    print("=" * 72)

    print(
        f"Total requests:    {len(results)}"
    )

    print(
        f"Successful:        {len(successful)}"
    )

    print(
        f"Failed:            {len(failed)}"
    )

    print(
        f"Success rate:      "
        f"{(len(successful) / len(results) * 100):.2f}%"
    )

    print(
        f"Benchmark elapsed: {total_benchmark_ms:.0f} ms"
    )

    if latencies:

        print()
        print("Client latency (all requests):")

        print(
            f"  Mean:  {statistics.mean(latencies):.2f} ms"
        )

        print(
            f"  P50:   {percentile(latencies, 50):.2f} ms"
        )

        print(
            f"  P95:   {percentile(latencies, 95):.2f} ms"
        )

        print(
            f"  P99:   {percentile(latencies, 99):.2f} ms"
        )

        print(
            f"  Max:   {max(latencies):.2f} ms"
        )

    if successful_latencies:

        print()
        print("Client latency (successful only):")

        print(
            f"  Mean:  "
            f"{statistics.mean(successful_latencies):.2f} ms"
        )

        print(
            f"  P50:   "
            f"{percentile(successful_latencies, 50):.2f} ms"
        )

        print(
            f"  P95:   "
            f"{percentile(successful_latencies, 95):.2f} ms"
        )

        print(
            f"  P99:   "
            f"{percentile(successful_latencies, 99):.2f} ms"
        )

        print(
            f"  Max:   "
            f"{max(successful_latencies):.2f} ms"
        )

    print()
    print("Tenant results:")

    for tenant_label in ["A", "B"]:

        tenant_results = [
            r
            for r in results
            if r["tenant_label"] == tenant_label
        ]

        tenant_success = [
            r
            for r in tenant_results
            if r["success"]
        ]

        tenant_latencies = [
            r["client_latency_ms"]
            for r in tenant_results
        ]

        if not tenant_results:
            continue

        print(
            f"  Tenant {tenant_label}: "
            f"{len(tenant_success)}/{len(tenant_results)} "
            f"successful"
        )

        print(
            f"    Mean: "
            f"{statistics.mean(tenant_latencies):.2f} ms"
        )

        print(
            f"    P50:  "
            f"{percentile(tenant_latencies, 50):.2f} ms"
        )

        print(
            f"    P95:  "
            f"{percentile(tenant_latencies, 95):.2f} ms"
        )

    if failed:

        print()
        print("FAILED REQUESTS:")

        for result in failed:

            print(
                f"  request={result['request_number']} "
                f"tenant={result['tenant_label']} "
                f"round={result['round']} "
                f"status={result['http_status']} "
                f"type={result['error_type']} "
                f"latency={result['client_latency_ms']:.0f}ms"
            )

            if result["error"]:
                print(
                    f"    {result['error'][:500]}"
                )

    print()
    print("=" * 72)
    print(
        f"Results written to: {args.csv}"
    )
    print("=" * 72)
    print()


if __name__ == "__main__":
    main()