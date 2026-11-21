#!/usr/bin/env python3
"""Execute SQL emitted by CalendarQuery against SQLite's in-memory engine."""
import base64
from datetime import date, timedelta
import subprocess
import sqlite3
import sys
from pathlib import Path
from tempfile import TemporaryDirectory


def decode(value: str) -> str:
    return base64.b64decode(value.encode("ascii")).decode("utf-8")


temporary = None
if len(sys.argv) < 2:
    root = Path(__file__).resolve().parents[1]
    temporary = TemporaryDirectory()
    classes = Path(temporary.name)
    subprocess.run([
        "javac", "-encoding", "UTF-8", "-d", str(classes),
        str(root / "app/src/main/java/cn/suizhang/ledger/CalendarQuery.java"),
        str(root / "scripts/CalendarQueryTest.java"),
    ], check=True)
    emitted = subprocess.run(
        ["java", "-cp", str(classes), "cn.suizhang.ledger.CalendarQueryTest", "--emit"],
        check=True, capture_output=True, text=True,
    )
    fixture = classes / "calendar-query-fixture.tsv"
    fixture.write_text(emitted.stdout, encoding="utf-8")
else:
    fixture = Path(sys.argv[1])

queries = {}
for line in fixture.read_text(encoding="utf-8").splitlines():
    if not line.startswith("QUERY\t"):
        continue
    _, name, sql64, args64 = line.split("\t")
    args_text = decode(args64)
    queries[name] = (decode(sql64), args_text.split("\n") if args_text else [])

required = {"ALL", "ACCOUNT_1", "ACCOUNT_2"}
if queries.keys() < required:
    raise AssertionError(f"missing emitted query fixtures: {required - queries.keys()}")

conn = sqlite3.connect(":memory:")
conn.executescript("""
CREATE TABLE accounts (_id INTEGER PRIMARY KEY, name TEXT NOT NULL);
CREATE TABLE holdings (_id INTEGER PRIMARY KEY, name TEXT, code TEXT, tax_rate REAL, market TEXT, account_id INTEGER, tax_mode TEXT, quantity REAL);
CREATE TABLE dividends (_id INTEGER PRIMARY KEY, holding_id INTEGER, account_id INTEGER, pay_date TEXT, record_date TEXT, ex_date TEXT, total REAL, status TEXT, currency TEXT);
CREATE INDEX idx_dividends_paydate ON dividends(pay_date);
CREATE INDEX idx_dividends_recorddate ON dividends(record_date);
CREATE INDEX idx_dividends_exdate ON dividends(ex_date);
""")

# Reproduce the prior defect by appending the extra ')' to each query path.
for name, (sql, args) in queries.items():
    broken = sql.replace(" ORDER BY", ") ORDER BY", 1) if name == "ALL" else sql.replace(" AND d.account_id=?", ") AND d.account_id=?", 1)
    assert broken != sql, f"{name}: unable to construct the historical malformed query"
    try:
        conn.execute(broken, args).fetchall()
    except sqlite3.OperationalError as exc:
        assert "syntax error" in str(exc), f"{name}: unexpected SQLite error: {exc}"
    else:
        raise AssertionError(f"{name}: expected the historical extra parenthesis to fail")
print("PASS SQLite reproduces syntax errors from the historical extra parenthesis in all account paths")

# Empty ledgers and accounts with no matching month must return an empty result, not crash.
for name, (sql, args) in queries.items():
    assert conn.execute(sql, args).fetchall() == [], f"{name}: expected an empty calendar on a new ledger"
print("PASS SQLite executes all calendar filters against an empty ledger")

conn.executemany("INSERT INTO accounts VALUES (?, ?)", [(1, "账户一"), (2, "账户二")])
conn.executemany("INSERT INTO holdings VALUES (?, ?, ?, ?, ?, ?, ?, ?)", [
    (11, "证券一", "AAA", 0.1, "A股", 1, "auto", 100),
    (22, "证券二", "BBB", 0.1, "A股", 2, "auto", 200),
])
conn.executemany("INSERT INTO dividends VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", [
    (101, 11, 1, "2026-10-15", "2026-09-30", "2026-10-14", 10.0, "expected", "CNY"),
    (201, 22, 2, "2026-11-01", "2026-10-21", "2026-10-22", 20.0, "expected", "CNY"),
    (202, 22, 2, "2026-11-03", "2026-11-02", "2026-10-25", 30.0, "expected", "CNY"),
    (102, 11, 1, "2026-11-15", "2026-11-14", "2026-11-13", 40.0, "expected", "CNY"),
    (103, 11, 1, "2026-10-01", "2026-09-30", "2026-09-29", 5.0, "expected", "CNY"),
    (104, 11, 1, "2026-11-30", "2026-10-31", "2026-09-29", 6.0, "expected", "CNY"),
    (105, 11, 1, "2026-11-30", "2026-11-30", "2026-11-01", 7.0, "expected", "CNY"),
    (106, 999, 3, "2026-11-20", "2026-10-26", "2026-10-25", 8.0, "expected", "CNY"),
])

all_rows = conn.execute(*queries["ALL"]).fetchall()
account_1_rows = conn.execute(*queries["ACCOUNT_1"]).fetchall()
account_2_rows = conn.execute(*queries["ACCOUNT_2"]).fetchall()
assert {row[0] for row in all_rows} == {101, 201, 202, 103, 104, 106}, "all-account filter must include month milestones even for orphan dividends"
assert {row[0] for row in account_1_rows} == {101, 103, 104}, "account 1 filter must include the first and last day without leaking other accounts"
assert {row[0] for row in account_2_rows} == {201, 202}, "account 2 filter must match record and ex-date paths"
assert 105 not in {row[0] for row in all_rows}, "the first day of the next month must be excluded"
orphan = next(row for row in all_rows if row[0] == 106)
assert orphan[9] is None and orphan[15] is None, "missing holding/account metadata should remain blank, not suppress the dividend"
print("PASS SQLite preserves account/milestone boundaries and keeps orphan dividends visible with blank metadata")

# A sizeable ledger proves the range predicates can use each existing date index.
start = date(2000, 1, 1)
rows = []
for offset in range(20_000):
    value = (start + timedelta(days=offset)).isoformat()
    rows.append((10_000 + offset, 11, 1, value, value, value, 1.0, "expected", "CNY"))
conn.executemany("INSERT INTO dividends VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", rows)
for name, (sql, args) in queries.items():
    details = "\n".join(row[3] for row in conn.execute("EXPLAIN QUERY PLAN " + sql, args))
    for column, index in (("pay_date", "idx_dividends_paydate"),
                          ("record_date", "idx_dividends_recorddate"),
                          ("ex_date", "idx_dividends_exdate")):
        expected = f"SEARCH d USING INDEX {index} ({column}>? AND {column}<?)"
        assert expected in details, f"{name}: {column} range does not use {index}:\n{details}"
print("PASS SQLite query plan searches each existing date index with bounded ranges")
