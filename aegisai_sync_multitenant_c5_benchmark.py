import argparse
import csv
import json
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

import requests


def percentile(values, p):
    if not values:
        return 0

    values = sorted(values)

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
    round_number,
    slot,
    tenant_name,
    api_key,
    body,
    url,
    timeout,
    barrier
):
    # All five workers wait here.
    barrier.wait()

    start_perf_ns = time.perf_counter_ns()
    start_epoch_ns = time.time_ns()

    started_at = datetime.now(timezone.utc).isoformat()

    status_code = ""
    request_id = ""
    error = ""
    response_preview = ""

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
        response_preview = response.text[:1000]

        request_id = (
            response.headers.get("X-Request-ID")
            or response.headers.get("X-Request-Id")
            or response.headers.get("requestId")
            or ""
        )

    except requests.exceptions.Timeout as exc:
        error = f"TIMEOUT: {exc}"

    except requests.exceptions.RequestException as exc:
        error = f"REQUEST_ERROR: {exc}"

    except Exception as exc:
        error = f"ERROR: {exc}"

    end_perf_ns = time.perf_counter_ns()
    end_epoch_ns = time.time_ns()

    latency_ms = (
        end_perf_ns - start_perf_ns
    ) / 1_000_000

    success = (
        status_code != ""
        and 200 <= int(status_code) < 300
    )

    return {
        "round": round_number,
        "slot": slot,
        "tenant": tenant_name,
        "request_id": request_id,
        "started_at": started_at,
        "start_epoch_ns": start_epoch_ns,
        "end_epoch_ns": end_epoch_ns,
        "latency_ms": round(latency_ms, 3),
        "status_code": status_code,
        "success": success,
        "error": error,
        "response_preview": response_preview,
    }


def main():

    parser = argparse.ArgumentParser(
        description="AegisAI synchronized C5 multi-tenant benchmark"
    )

    parser.add_argument("--url", required=True)
    parser.add_argument("--body", required=True)

    parser.add_argument(
        "--tenant-a-key",
        required=True
    )

    parser.add_argument(
        "--tenant-b-key",
        required=True
    )

    parser.add_argument(
        "--tenant-a-name",
        default="POSTMAN-ISOLATION-A"
    )

    parser.add_argument(
        "--tenant-b-name",
        default="POSTMAN-ISOLATION-B"
    )

    parser.add_argument(
        "--rounds",
        type=int,
        default=10
    )

    parser.add_argument(
        "--timeout",
        type=float,
        default=120
    )

    parser.add_argument(
        "--csv",
        default="mt-c5-synchronized-same.csv"
    )

    args = parser.parse_args()

    with open(
        args.body,
        "r",
        encoding="utf-8"
    ) as f:
        body = json.load(f)

    print()
    print("=" * 80)
    print("AEGISAI MT-C5-SYNC-SAME")
    print("=" * 80)
    print(f"URL:               {args.url}")
    print(f"Tenant A:          {args.tenant_a_name}")
    print(f"Tenant B:          {args.tenant_b_name}")
    print("Provider:          OLLAMA")
    print("Model:             llama3.1:8b")
    print("Prompt:            SAME")
    print("Requests / round:  5")
    print("Tenant A / round:  3")
    print("Tenant B / round:  2")
    print(f"Rounds:            {args.rounds}")
    print(f"Total requests:    {args.rounds * 5}")
    print(f"Timeout:           {args.timeout}s")
    print("=" * 80)
    print()

    all_results = []

    benchmark_start = time.perf_counter()

    for round_number in range(
        1,
        args.rounds + 1
    ):

        # Exactly five workers synchronize here.
        barrier = threading.Barrier(5)

        jobs = [
            (
                "A1",
                args.tenant_a_name,
                args.tenant_a_key,
            ),
            (
                "A2",
                args.tenant_a_name,
                args.tenant_a_key,
            ),
            (
                "A3",
                args.tenant_a_name,
                args.tenant_a_key,
            ),
            (
                "B1",
                args.tenant_b_name,
                args.tenant_b_key,
            ),
            (
                "B2",
                args.tenant_b_name,
                args.tenant_b_key,
            ),
        ]

        round_results = []

        with ThreadPoolExecutor(
            max_workers=5
        ) as executor:

            futures = []

            for slot, tenant, key in jobs:

                futures.append(
                    executor.submit(
                        execute_request,
                        round_number,
                        slot,
                        tenant,
                        key,
                        body,
                        args.url,
                        args.timeout,
                        barrier,
                    )
                )

            for future in as_completed(futures):
                result = future.result()
                round_results.append(result)

        all_results.extend(round_results)

        # Calculate actual client-side synchronization skew.
        starts = [
            r["start_epoch_ns"]
            for r in round_results
        ]

        skew_ms = (
            max(starts) - min(starts)
        ) / 1_000_000

        print(
            f"Round {round_number:02d} | "
            f"5-way start skew={skew_ms:.3f} ms | "
            +
            " | ".join(
                f"{r['slot']}="
                f"{'OK' if r['success'] else 'FAIL'} "
                f"{r['latency_ms']:.1f}ms"
                for r in sorted(
                    round_results,
                    key=lambda x: x["slot"]
                )
            )
        )

    benchmark_elapsed = (
        time.perf_counter() - benchmark_start
    )

    # ---------------------------------------------------------
    # Overall statistics
    # ---------------------------------------------------------

    total = len(all_results)

    successful = [
        r for r in all_results
        if r["success"]
    ]

    failed = [
        r for r in all_results
        if not r["success"]
    ]

    latencies = [
        r["latency_ms"]
        for r in all_results
    ]

    skews = []

    for round_number in range(
        1,
        args.rounds + 1
    ):

        rr = [
            r for r in all_results
            if r["round"] == round_number
        ]

        starts = [
            r["start_epoch_ns"]
            for r in rr
        ]

        skews.append(
            (
                max(starts) - min(starts)
            ) / 1_000_000
        )

    print()
    print("=" * 80)
    print("OVERALL RESULT")
    print("=" * 80)

    print(f"Total requests:       {total}")
    print(f"Successful:           {len(successful)}")
    print(f"Failed:               {len(failed)}")

    if total:
        print(
            f"Success rate:         "
            f"{len(successful) / total * 100:.2f}%"
        )

    print(
        f"Average start skew:   "
        f"{sum(skews) / len(skews):.3f} ms"
    )

    print(
        f"P95 start skew:       "
        f"{percentile(skews, .95):.3f} ms"
    )

    print(
        f"Maximum start skew:   "
        f"{max(skews):.3f} ms"
    )

    print(
        f"P50 latency:          "
        f"{percentile(latencies, .50):.3f} ms"
    )

    print(
        f"P95 latency:          "
        f"{percentile(latencies, .95):.3f} ms"
    )

    print(
        f"P99 latency:          "
        f"{percentile(latencies, .99):.3f} ms"
    )

    print(
        f"Maximum latency:      "
        f"{max(latencies):.3f} ms"
    )

    print(
        f"Benchmark time:       "
        f"{benchmark_elapsed:.3f}s"
    )

    # ---------------------------------------------------------
    # Per-tenant
    # ---------------------------------------------------------

    print()
    print("=" * 80)
    print("PER-TENANT RESULT")
    print("=" * 80)

    for tenant_name in [
        args.tenant_a_name,
        args.tenant_b_name,
    ]:

        tenant_results = [
            r for r in all_results
            if r["tenant"] == tenant_name
        ]

        tenant_success = [
            r for r in tenant_results
            if r["success"]
        ]

        tenant_latencies = [
            r["latency_ms"]
            for r in tenant_results
        ]

        print()
        print(tenant_name)

        print(
            f"  Requests:       "
            f"{len(tenant_results)}"
        )

        print(
            f"  Successful:     "
            f"{len(tenant_success)}"
        )

        print(
            f"  Failed:         "
            f"{len(tenant_results) - len(tenant_success)}"
        )

        print(
            f"  Success rate:   "
            f"{len(tenant_success) / len(tenant_results) * 100:.2f}%"
        )

        print(
            f"  P50:            "
            f"{percentile(tenant_latencies, .50):.3f} ms"
        )

        print(
            f"  P95:            "
            f"{percentile(tenant_latencies, .95):.3f} ms"
        )

        print(
            f"  P99:            "
            f"{percentile(tenant_latencies, .99):.3f} ms"
        )

        print(
            f"  Max:            "
            f"{max(tenant_latencies):.3f} ms"
        )

    # ---------------------------------------------------------
    # Failure details
    # ---------------------------------------------------------

    if failed:

        print()
        print("=" * 80)
        print("FAILURES")
        print("=" * 80)

        for r in failed:

            print(
                f"Round {r['round']} "
                f"{r['slot']} "
                f"{r['tenant']} | "
                f"status={r['status_code']} | "
                f"latency={r['latency_ms']:.3f}ms | "
                f"{r['error']}"
            )

    # ---------------------------------------------------------
    # CSV
    # ---------------------------------------------------------

    fields = [
        "round",
        "slot",
        "tenant",
        "request_id",
        "started_at",
        "start_epoch_ns",
        "end_epoch_ns",
        "latency_ms",
        "status_code",
        "success",
        "error",
        "response_preview",
    ]

    with open(
        args.csv,
        "w",
        newline="",
        encoding="utf-8"
    ) as f:

        writer = csv.DictWriter(
            f,
            fieldnames=fields
        )

        writer.writeheader()

        for result in sorted(
            all_results,
            key=lambda x: (
                x["round"],
                x["slot"]
            )
        ):
            writer.writerow(result)

    print()
    print("=" * 80)
    print(f"CSV written: {args.csv}")
    print("=" * 80)


if __name__ == "__main__":
    main()