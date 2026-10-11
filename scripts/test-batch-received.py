#!/usr/bin/env python3
"""Static and SQLite behavioral guards for due-only batch receipt confirmation."""
from pathlib import Path
import sqlite3
import tempfile

root = Path(__file__).resolve().parents[1]
database = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")

def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS  " + label)

batch = database[database.index("int markReceivedBatch("):database.index("void deleteHolding(long id)")]
calendar = activity[activity.index("private void showCalendar()"):activity.index("private void showCalendarYear()")]
confirm = activity[activity.index("private void confirmMarkReceivedBatch("):activity.index("private YearMonth calendarMonth()")]

require("批量路径只更新 status，不改 received_amount", 'cv.put("status", "received")' in batch and "received_amount" not in batch)
require("批量路径不触发分红摊薄成本", "adjustCostForDividend" not in batch)
require("只更新仍为 expected 的记录，重复调用幂等", "_id=? AND status='expected'" in batch)
require("所有更新位于单一 SQLite 事务中", "beginTransaction()" in batch and "setTransactionSuccessful()" in batch and "endTransaction()" in batch)
require("跳过空值、非正 ID 和重复 ID", "id == null || id <= 0 || !seen.add(id)" in batch)
require("日历按月统计已到期候选并在数量大于零时显示操作", "pendingDueDividends(batchEvents, cur, LocalDate.now()).size()" in calendar and '"批量确认已到账（"' in calendar)
require("批次确认只接收当前月候选，逐项展示日期、标的和记录金额", "pendingDueDividends(rows, targetMonth, LocalDate.now())" in confirm and "shortDate(row.optString(\"pay_date\"))" in confirm and "row.optDouble(\"total\")" in confirm)
require("确认提示区分记录金额与实收金额，并说明成本不变", "并非券商实收金额" in confirm and "不会填写实际到账金额或改动持仓成本" in confirm)
require("UI 确认后调用批量事务方法并刷新", "db.markReceivedBatch(ids)" in confirm and "render();" in confirm)

with tempfile.NamedTemporaryFile(suffix=".sqlite") as file:
    conn = sqlite3.connect(file.name)
    conn.execute("CREATE TABLE dividends (_id INTEGER PRIMARY KEY, status TEXT NOT NULL, received_amount REAL)")
    conn.execute("CREATE TABLE holdings (_id INTEGER PRIMARY KEY, cost REAL NOT NULL)")
    conn.executemany("INSERT INTO dividends VALUES(?,?,?)", [(1, "expected", None), (2, "expected", 12.5), (3, "received", 9.0)])
    conn.execute("INSERT INTO holdings VALUES(1, 100.0)")
    conn.commit()
    # Exercise the exact guarded update shape used by the production transaction.
    conn.execute("BEGIN")
    changed = sum(conn.execute("UPDATE dividends SET status=? WHERE _id=? AND status='expected'", ("received", item)).rowcount for item in [1, 2, 3, 1])
    conn.commit()
    rows = {row[0]: (row[1], row[2]) for row in conn.execute("SELECT _id,status,received_amount FROM dividends")}
    cost = conn.execute("SELECT cost FROM holdings WHERE _id=1").fetchone()[0]
    require("SQLite 行为模拟中仅两条待收记录发生状态更新", changed == 2 and rows[1][0] == "received" and rows[2][0] == "received" and rows[3][0] == "received")
    require("批量更新保留实收字段原值/空值及持仓成本", rows == {1: ("received", None), 2: ("received", 12.5), 3: ("received", 9.0)} and cost == 100.0)
    conn.close()

print("通过：批量到账状态、UI 范围与金额/成本保护回归；SQLite 部分为行为模拟，不替代 Android 真机验证。")
