import argparse
import csv
import json
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

import requests


def parse_args():
    p = argparse.ArgumentParser()

    p.add_argument("--url", required=True)
    p.add_argument("--body", required=True)

    p.add_argument("--tenant-a-key", required=True)
    p.add_argument("--tenant-b-key", required=True)

    p.add_argument("--tenant-a-name", default="POSTMAN-ISOLATION-A")
    p.add_argument("--tenant-b-name", default="POSTMAN-ISOLATION-B")

    p.add_argument("--rounds", type=int, default=20)
    p.add_argument("--timeout", type=float, default=120)

    p.add_argument(
        "--csv",
        default="mt-synchronized-c2-same.csv"
    )

    return p.parse_args()


def percentile(values, p):
    if not values:
        return None

    values = sorted(values)

    index = (len(values) - 1) * p
    lower = int(index)
    upper = min(lower + 1, len(values) - 1)

    if lower == upper:
        return values[lower]

    return (
        values[lower]
        + (values[upper] - values[lower])
        * (index - lower)
    )


def load_body(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def execute_request(
    round_number,
    tenant_name,
    api_key,
    body,
    url,
    timeout,
    barrier
):
    # Wait until Tenant A and Tenant B are both ready.
    barrier.wait()

    # High-resolution local timestamp.
    perf_start = time.perf_counter_ns()
    epoch_start_ns = time.time_ns()

    started_at = datetime.now(timezone.utc).isoformat()

    status_code = None
    response_text = ""
    error = ""

    request_id = ""

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
        response_text = response.text[:1000]

        request_id = (
            response.headers.get("X-Request-ID")
            or response.headers.get("X-Request-Id")
            or response.headers.get("requestId")
            or ""
        )

    except requests.exceptions.Timeout as e:
        error = f"TIMEOUT: {e}"

    except requests.exceptions.RequestException as e:
        error = f"REQUEST_ERROR: {e}"

    except Exception as e:
        error = f"ERROR: {e}"

    perf_end = time.perf_counter_ns()
    epoch_end_ns = time.time_ns()

    latency_ms = (
        perf_end - perf_start
    ) / 1_000_000

    success = (
        status_code is not None
        and 200 <= status_code < 300
    )

    return {
        "round": round_number,
        "tenant": tenant_name,
        "request_id": request_id,
        "started_at": started_at,
        "start_epoch_ns": epoch_start_ns,
        "end_epoch_ns": epoch_end_ns,
        "latency_ms": round(latency_ms, 3),
        "status_code": status_code or "",
        "success": success,
        "error": error,
        "response_preview": response_text,
    }


def main():
    args = parse_args()

    body = load_body(args.body)

    tenants = [
        (
            args.tenant_a_name,
            args.tenant_a_key,
        ),
        (
            args.tenant_b_name,
            args.tenant_b_key,
        ),
    ]

    results = []

    benchmark_start = time.perf_counter()

    print()
    print("=" * 75)
    print("AegisAI SYNCHRONIZED MULTI-TENANT BENCHMARK")
    print("=" * 75)
    print(f"URL:              {args.url}")
    print(f"Tenant A:         {args.tenant_a_name}")
    print(f"Tenant B:         {args.tenant_b_name}")
    print("Provider:         OLLAMA")
    print("Model:            llama3.1:8b")
    print("Prompt:           SAME")
    print(f"Rounds:           {args.rounds}")
    print("Requests/round:   2")
    print(f"Total requests:   {args.rounds * 2}")
    print("Concurrency:      2")
    print(f"Timeout:          {args.timeout}s")
    print("=" * 75)
    print()

    for round_number in range(1, args.rounds + 1):

        # New barrier for every round.
        barrier = threading.Barrier(2)

        with ThreadPoolExecutor(max_workers=2) as executor:

            futures = []

            for tenant_name, api_key in tenants:

                futures.append(
                    executor.submit(
                        execute_request,
                        round_number,
                        tenant_name,
                        api_key,
                        body,
                        args.url,
                        args.timeout,
                        barrier,
                    )
                )

            round_results = [
                future.result()
                for future in as_completed(futures)
            ]

        results.extend(round_results)

        # Calculate start skew for this round.
        starts = [
            r["start_epoch_ns"]
            for r in round_results
        ]

        skew_ms = (
            max(starts) - min(starts)
        ) / 1_000_000

        print(
            f"Round {round_number:02d} | "
            f"start skew={skew_ms:.3f} ms | "
            + " | ".join(
                f"{r['tenant']}="
                f"{'OK' if r['success'] else 'FAIL'} "
                f"{r['latency_ms']:.1f}ms"
                for r in sorted(
                    round_results,
                    key=lambda x: x["tenant"]
                )
            )
        )

    total_elapsed = (
        time.perf_counter() - benchmark_start
    )

    # ---------------------------------------------------------------
    # Overall results
    # ---------------------------------------------------------------

    successes = [
        r for r in results
        if r["success"]
    ]

    latencies = [
        r["latency_ms"]
        for r in results
    ]

    start_skews = []

    for round_number in range(
        1,
        args.rounds + 1
    ):
        rr = [
            r for r in results
            if r["round"] == round_number
        ]

        if len(rr) == 2:
            starts = [
                r["start_epoch_ns"]
                for r in rr
            ]

            start_skews.append(
                (max(starts) - min(starts))
                / 1_000_000
            )

    print()
    print("=" * 75)
    print("OVERALL RESULT")
    print("=" * 75)

    print(f"Total requests:       {len(results)}")
    print(f"Successful:           {len(successes)}")
    print(f"Failed:               {len(results) - len(successes)}")

    if results:
        print(
            f"Success rate:         "
            f"{len(successes) / len(results) * 100:.2f}%"
        )

    print(
        f"Average start skew:   "
        f"{sum(start_skews) / len(start_skews):.3f} ms"
    )

    print(
        f"Maximum start skew:   "
        f"{max(start_skews):.3f} ms"
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
        f"Total benchmark time: "
        f"{total_elapsed:.3f}s"
    )

    # ---------------------------------------------------------------
    # Per-tenant
    # ---------------------------------------------------------------

    print()
    print("=" * 75)
    print("PER-TENANT RESULT")
    print("=" * 75)

    for tenant_name, _ in tenants:

        tenant_results = [
            r for r in results
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
            f"  Success:        "
            f"{len(tenant_success)}"
        )
        print(
            f"  Failure:        "
            f"{len(tenant_results) - len(tenant_success)}"
        )

        if tenant_results:
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
            f"  Max:            "
            f"{max(tenant_latencies):.3f} ms"
        )

    # ---------------------------------------------------------------
    # CSV
    # ---------------------------------------------------------------

    fieldnames = [
        "round",
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
            fieldnames=fieldnames
        )

        writer.writeheader()

        for result in sorted(
            results,
            key=lambda x: (
                x["round"],
                x["tenant"]
            )
        ):
            writer.writerow(result)

    print()
    print("=" * 75)
    print(f"CSV: {args.csv}")
    print("=" * 75)


if __name__ == "__main__":
    main()