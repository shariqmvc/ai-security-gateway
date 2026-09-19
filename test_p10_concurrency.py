import requests
import concurrent.futures
import json
import time

BASE_URL = "http://localhost:8080"

SESSION_TOKEN = "arp_qXWgJYbYxJKLtLu8bLIu5dx1x74mroGcwww_2RyDQSQ"

URL = f"{BASE_URL}/api/chat"

HEADERS = {
    "Authorization": f"Bearer {SESSION_TOKEN}",
    "Content-Type": "application/json",
}

PAYLOAD = {
    "prompt": (
        "Explain AIRouter architecture, provider abstraction, "
        "routing, caching, observability, security, RAG, "
        "billing and quota management."
    ),
    "provider": "OLLAMA",
    "model": "llama3.2:3b",
    "billingMode": "FREE"
}


def send_chat(request_no):
    start = time.perf_counter()

    try:
        response = requests.post(
            URL,
            headers=HEADERS,
            json=PAYLOAD,
            timeout=120
        )

        elapsed = time.perf_counter() - start

        try:
            body = response.json()
        except Exception:
            body = response.text

        return {
            "request": request_no,
            "status": response.status_code,
            "elapsed": round(elapsed, 3),
            "body": body
        }

    except Exception as e:
        return {
            "request": request_no,
            "status": "ERROR",
            "elapsed": round(time.perf_counter() - start, 3),
            "body": str(e)
        }


def main():
    print()
    print("=" * 70)
    print("AIRouter Personal v1 - P10 Concurrent Request Test")
    print("=" * 70)
    print(f"URL: {URL}")
    print("Expected max-concurrent-requests: 1")
    print()

    overall_start = time.perf_counter()

    # IMPORTANT:
    # Both requests are submitted before either result is collected.
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:

        future1 = executor.submit(send_chat, 1)
        future2 = executor.submit(send_chat, 2)

        result1 = future1.result()
        result2 = future2.result()

    total_elapsed = time.perf_counter() - overall_start

    results = [result1, result2]

    print("-" * 70)

    for result in results:
        print()
        print(f"REQUEST {result['request']}")
        print(f"HTTP STATUS : {result['status']}")
        print(f"TIME        : {result['elapsed']} sec")
        print("RESPONSE    :")

        if isinstance(result["body"], dict):
            print(json.dumps(
                result["body"],
                indent=2,
                ensure_ascii=False
            ))
        else:
            print(result["body"])

        print("-" * 70)

    print()
    print(f"TOTAL TIME: {round(total_elapsed, 3)} sec")
    print()

    statuses = [r["status"] for r in results]

    # Expected with max-concurrent-requests = 1
    if statuses.count(200) == 1 and statuses.count(429) == 1:

        rejected = next(
            r for r in results
            if r["status"] == 429
        )

        print("✅ P10 CONCURRENCY TEST PASSED")
        print("One request was accepted and one was rejected.")

        if isinstance(rejected["body"], dict):
            print(
                "Rejected message:",
                rejected["body"].get("message")
            )

    elif statuses.count(200) == 2:

        print("⚠️ BOTH REQUESTS RETURNED 200")
        print()
        print("Possible reasons:")
        print("1. Requests did not overlap.")
        print("2. max-concurrent-requests is not enabled.")
        print("3. The concurrency check is not being enforced.")

    elif statuses.count(429) == 2:

        print("⚠️ BOTH REQUESTS RETURNED 429")
        print("Check whether another quota is blocking the requests.")

    else:

        print("⚠️ UNEXPECTED RESULT")
        print("Inspect the responses above.")


if __name__ == "__main__":
    main()