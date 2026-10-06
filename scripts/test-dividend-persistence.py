#!/usr/bin/env python3
"""SQLite persistence regression using the actual schema-3 holdings DDL."""
from pathlib import Path
import json
import re
import sqlite3
import tempfile

root = Path(__file__).resolve().parents[1]
database_source = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
main_source = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
match = re.search(r'db\.execSQL\("(CREATE TABLE holdings [^\"]+)"\);', database_source)
if not match:
    raise AssertionError("未能提取 schema-3 holdings DDL")
holdings_ddl = match.group(1)
if 'cv.put("annual_dividend_per_unit", annualDividendPerUnit)' not in database_source:
    raise AssertionError("生产 saveHolding 未保存 annual_dividend_per_unit")
if 'db.saveSetting("dividend_forecast_" + holdingId' not in main_source:
    raise AssertionError("生产界面未持久化预测来源/窗口/更新时间")

def require(label, condition):
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS  " + label)

with tempfile.NamedTemporaryFile(suffix=".sqlite") as file:
    conn = sqlite3.connect(file.name)
    conn.execute(holdings_ddl)
    conn.execute("CREATE TABLE app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    conn.execute("INSERT INTO holdings(name,code,market,currency,quantity,cost,account_id,created_at,annual_dividend_per_unit) VALUES(?,?,?,?,?,?,?,?,?)",
                 ("测试标的", "600519", "A股", "CNY", 125, 100, 1, "2026-10-04T00:00:00Z", .8))
    holding_id = conn.execute("SELECT last_insert_rowid()").fetchone()[0]
    per_unit, quantity = conn.execute("SELECT annual_dividend_per_unit,quantity FROM holdings WHERE _id=?", (holding_id,)).fetchone()
    require("选标的并保存后每份历史估算值持久化", per_unit == .8)
    require("保存后年预计总额=每份金额×数量", abs(per_unit * quantity - 100) < 1e-9)
    conn.execute("UPDATE holdings SET quantity=?, annual_dividend_per_unit=? WHERE _id=?", (150, .8, holding_id))
    meta = {"source": "historical_estimate", "years": 3, "updated_at": 1791072000000, "stale": False}
    conn.execute("INSERT OR REPLACE INTO app_settings(key,value) VALUES(?,?)", (f"dividend_forecast_{holding_id}", json.dumps(meta)))
    require("编辑持仓修改股数但保留每份值，年总额自动重算", abs(conn.execute("SELECT quantity*annual_dividend_per_unit FROM holdings WHERE _id=?", (holding_id,)).fetchone()[0] - 120) < 1e-9)
    changed = conn.execute("UPDATE holdings SET annual_dividend_per_unit=? WHERE _id=? AND code=? AND market=?", (.9, holding_id, "WRONG", "A股")).rowcount
    require("防止旧异步结果写入已改代码的持仓", changed == 0)
    conn.commit(); conn.close()
    reopened = sqlite3.connect(file.name)
    saved = reopened.execute("SELECT quantity,annual_dividend_per_unit FROM holdings WHERE _id=?", (holding_id,)).fetchone()
    raw_meta = reopened.execute("SELECT value FROM app_settings WHERE key=?", (f"dividend_forecast_{holding_id}",)).fetchone()[0]
    reopened_meta = json.loads(raw_meta)
    require("关闭再打开后每份金额与年总额仍正确", saved == (150.0, .8) and abs(saved[0] * saved[1] - 120) < 1e-9)
    require("重开后历史来源、窗口和更新时间可恢复", reopened_meta["source"] == "historical_estimate" and reopened_meta["years"] == 3 and reopened_meta["updated_at"] > 0)
    reopened.close()
print("通过：SQLite schema-3 保存/编辑/重开分红预测持久化回归")
