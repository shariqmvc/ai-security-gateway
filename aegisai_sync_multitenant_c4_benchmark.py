import argparse, csv, json, threading, time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
import requests

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--url", required=True)
    p.add_argument("--body", required=True)
    p.add_argument("--tenant-a-key", required=True)
    p.add_argument("--tenant-b-key", required=True)
    p.add_argument("--tenant-a-name", default="POSTMAN-ISOLATION-A")
    p.add_argument("--tenant-b-name", default="POSTMAN-ISOLATION-B")
    p.add_argument("--rounds", type=int, default=10)
    p.add_argument("--timeout", type=float, default=120)
    p.add_argument("--csv", default="mt-c4-synchronized-same.csv")
    args = p.parse_args()

    with open(args.body, encoding="utf-8") as f:
        body = json.load(f)

    tenants = [
        (args.tenant_a_name, args.tenant_a_key),
        (args.tenant_b_name, args.tenant_b_key),
    ]

    rows = []

    for rnd in range(1, args.rounds + 1):
        barrier = threading.Barrier(4)

        def call(tenant, key, slot):
            barrier.wait()
            start_ns = time.time_ns()
            perf = time.perf_counter_ns()
            status = ""
            err = ""
            rid = ""
            try:
                r = requests.post(
                    args.url,
                    headers={"Content-Type":"application/json","X-API-Key":key},
                    json=body,
                    timeout=args.timeout
                )
                status = r.status_code
                rid = (r.headers.get("X-Request-ID")
                       or r.headers.get("X-Request-Id")
                       or r.headers.get("requestId") or "")
            except Exception as e:
                err = str(e)
            latency = (time.perf_counter_ns() - perf) / 1_000_000
            return {
                "round": rnd, "slot": slot, "tenant": tenant,
                "request_id": rid, "start_epoch_ns": start_ns,
                "latency_ms": round(latency,3), "status_code": status,
                "success": bool(status) and 200 <= int(status) < 300,
                "error": err
            }

        # Two workers per tenant = four simultaneous requests.
        jobs = [
            (tenants[0][0], tenants[0][1], "A1"),
            (tenants[0][0], tenants[0][1], "A2"),
            (tenants[1][0], tenants[1][1], "B1"),
            (tenants[1][0], tenants[1][1], "B2"),
        ]

        with ThreadPoolExecutor(max_workers=4) as ex:
            futures = [ex.submit(call, *job) for job in jobs]
            rr = [f.result() for f in as_completed(futures)]

        rows.extend(rr)
        skew = (max(x["start_epoch_ns"] for x in rr)
                - min(x["start_epoch_ns"] for x in rr)) / 1_000_000
        print(f"Round {rnd:02d} | 4-request start skew={skew:.3f} ms | " +
              " | ".join(f'{x["slot"]}={"OK" if x["success"] else "FAIL"} {x["latency_ms"]:.1f}ms'
                         for x in sorted(rr, key=lambda x:x["slot"])))

    fields = ["round","slot","tenant","request_id","start_epoch_ns",
              "latency_ms","status_code","success","error"]
    with open(args.csv, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(sorted(rows, key=lambda x:(x["round"],x["slot"])))

    ok = sum(x["success"] for x in rows)
    skews = []
    for rnd in range(1, args.rounds+1):
        rr = [x for x in rows if x["round"] == rnd]
        skews.append((max(x["start_epoch_ns"] for x in rr)
                      - min(x["start_epoch_ns"] for x in rr))/1_000_000)
    lats = sorted(x["latency_ms"] for x in rows)
    q = lambda p: lats[min(len(lats)-1, int((len(lats)-1)*p))]
    print("\nMT-C4-SYNC-SAME")
    print(f"Requests: {len(rows)}")
    print(f"Success: {ok}/{len(rows)} ({ok/len(rows)*100:.2f}%)")
    print(f"Average start skew: {sum(skews)/len(skews):.3f} ms")
    print(f"Max start skew: {max(skews):.3f} ms")
    print(f"P50: {q(.50):.3f} ms")
    print(f"P95: {q(.95):.3f} ms")
    print(f"P99: {q(.99):.3f} ms")
    print(f"Max: {max(lats):.3f} ms")
    print(f"CSV: {args.csv}")

if __name__ == "__main__":
    main()
