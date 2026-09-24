import os
import sys
import requests
import psycopg2

OLLAMA = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
MODEL = os.getenv("OLLAMA_EMBED_MODEL", "nomic-embed-text")

def main():
    print("P11.9.2 environment check")
    print("=" * 32)

    try:
        r = requests.get(f"{OLLAMA}/api/tags", timeout=10)
        r.raise_for_status()
        models = [m.get("name", "") for m in r.json().get("models", [])]
        print(f"Ollama: PASS ({OLLAMA})")
        print("Models:")
        for m in models:
            print(f"  {m}")
        if not any(m == MODEL or m.startswith(MODEL + ":") for m in models):
            print(f"WARNING: {MODEL} was not found in Ollama.")
    except Exception as e:
        print(f"Ollama: FAIL - {e}")

    try:
        conn = psycopg2.connect(
            host=os.getenv("PGHOST", "localhost"),
            port=int(os.getenv("PGPORT", "5433")),
            dbname=os.getenv("PGDATABASE", "aegisai"),
            user=os.getenv("PGUSER", "postgres"),
            password=os.getenv("PGPASSWORD", "postgres"),
            connect_timeout=5,
        )
        with conn.cursor() as cur:
            cur.execute("SELECT version();")
            print("PostgreSQL: PASS")
            print("  " + cur.fetchone()[0].splitlines()[0])
            cur.execute("SELECT extname FROM pg_extension WHERE extname='vector';")
            print("pgvector:", "PASS" if cur.fetchone() else "FAIL")
        conn.close()
    except Exception as e:
        print(f"PostgreSQL: FAIL - {e}")

if __name__ == "__main__":
    main()
