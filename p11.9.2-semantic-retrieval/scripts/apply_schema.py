import os
from pathlib import Path
import psycopg2

ROOT = Path(__file__).resolve().parents[1]

def db():
    return psycopg2.connect(
        host=os.getenv("PGHOST", "localhost"),
        port=int(os.getenv("PGPORT", "5433")),
        dbname=os.getenv("PGDATABASE", "aegisai"),
        user=os.getenv("PGUSER", "postgres"),
        password=os.getenv("PGPASSWORD", "postgres"),
    )

def execute_file(conn, path):
    sql = path.read_text(encoding="utf-8")
    with conn.cursor() as cur:
        cur.execute(sql)
    conn.commit()

def main():
    conn = db()
    try:
        execute_file(conn, ROOT / "sql/001_create_semantic_security_table.sql")
        execute_file(conn, ROOT / "sql/002_create_semantic_search_function.sql")
        print("P11.9.2 schema: PASS")
    finally:
        conn.close()

if __name__ == "__main__":
    main()
