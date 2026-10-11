#!/usr/bin/env python3
"""Guards for batch dividend-arrival confirmation, covering both entry points.

Filling in a received amount feeds adjustCostForDividend and silently rewrites holding
cost, so the batch path must never write received_amount. That invariant is behavioural,
not stylistic: a batch the user cannot itemise should not move money.

Two entry points exist and both route through the same database transaction:
  - overview page: countPendingReceived(rows) -> confirmMarkReceivedBatch(rows)
  - calendar page: pendingDueDividends(...)  -> confirmMarkReceivedBatch(rows, targetMonth)

The last block is a SQLite behaviour simulation of the exact guarded update shape; it does
not replace running the app on a device.
"""
from pathlib import Path
import os
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
confirm_overview = activity[activity.index("private void confirmMarkReceivedBatch(JSONArray rows)"):
                            activity.index("private ArrayList<JSONObject> pendingDueDividends(")]
confirm_calendar = activity[activity.index("private void confirmMarkReceivedBatch(JSONArray rows, YearMonth targetMonth)"):
                            activity.index("private YearMonth calendarMonth()")]

# --- shared database contract (both entry points depend on it) ---
require("批量路径只更新 status，不改 received_amount",
        'cv.put("status", "received")' in batch and "received_amount" not in batch)
require("批量路径不触发分红摊薄成本", "adjustCostForDividend" not in batch)
require("批量路径不写入实收金额，单笔路径仍然写入",
        "adjustCostForDividend" in database and "received_amount" in database)
require("只更新仍为 expected 的记录，重复调用幂等", "_id=? AND status='expected'" in batch)
require("所有更新位于单一 SQLite 事务中",
        "beginTransaction()" in batch and "setTransactionSuccessful()" in batch and "endTransaction()" in batch)
require("跳过空值、非正 ID 和重复 ID", "id == null || id <= 0 || !seen.add(id)" in batch)
require("空列表直接返回，不开事务", "if (ids == null || ids.isEmpty()) return 0;" in batch)
require("返回实际变更行数", "changed += db.update" in batch and "return changed;" in batch)

# --- overview entry point ---
require("总览页入口只在有未到账记录时出现，并展示待确认笔数",
        "countPendingReceived(rows)" in activity and "if (pendingCount > 0)" in activity)
require("总览页确认前逐笔列出标的与金额，并提示不会改动成本",
        "confirmMarkReceivedBatch(JSONArray rows)" in activity
        and "names.add(label" in activity
        and "请先核对券商流水" in activity
        and "不会改动持仓成本" in activity)
require("总览页与批量共用同一套成本说明文案",
        "不会触发分红摊薄成本" in activity and "不会用于分红摊薄成本" in activity)

# --- overview must not sweep in payouts that have not happened yet ---
# The year view lists all twelve months. Filtering on status alone would mark an announced
# December payout as received in January and inflate 今年已收 with money never paid.
rules = (root / "app/src/main/java/cn/suizhang/ledger/DividendBatchRules.java").read_text(encoding="utf-8")
require("总览页候选先过派息日，不能只看状态",
        'DividendBatchRules.isEligible(row.optString("status"), payDate, asOf)' in confirm_overview
        and 'row.optString("pay_date")' in confirm_overview)
require("总览页待确认笔数与确认列表使用同一判据",
        'DividendBatchRules.isEligible(row.optString("status"), row.optString("pay_date"), asOf)'
        in activity[activity.index("private int countPendingReceived("):activity.index("private void confirmMarkReceivedBatch(JSONArray rows)")])
require("总览页候选不再用状态取反判断",
        '!"received".equals(row.optString("status"))' not in confirm_overview)
require("到期规则提供不带月份的重载，供年度批次复用",
        "static boolean isEligible(String status, String payDate, LocalDate asOf)" in rules)
require("到期规则同时要求状态为 expected 且派息日不晚于今天",
        '"expected".equals(status)' in rules and "!payout.isAfter(asOf)" in rules)
require("年度批次说明写明未来派息不在其中",
        "未来派息不会包含在内" in confirm_overview)
require("总览页确认后调用批量事务方法并刷新",
        "db.markReceivedBatch(ids)" in confirm_overview and "render();" in confirm_overview)

# --- calendar entry point ---
require("日历按月统计已到期候选并在数量大于零时显示操作",
        "pendingDueDividends(batchEvents, cur, LocalDate.now()).size()" in calendar
        and '"批量确认已到账（"' in calendar)
require("日历批次确认只接收当前月候选，逐项展示日期、标的和记录金额",
        "pendingDueDividends(rows, targetMonth, LocalDate.now())" in confirm_calendar
        and 'shortDate(row.optString("pay_date"))' in confirm_calendar
        and 'row.optDouble("total")' in confirm_calendar)
require("日历确认提示区分记录金额与实收金额，并说明成本不变",
        "并非券商实收金额" in confirm_calendar and "不会填写实际到账金额或改动持仓成本" in confirm_calendar)

# --- SQLite behaviour simulation of the guarded update ---
# NamedTemporaryFile keeps the handle open, which Windows locks against sqlite3.connect.
# Close it first and clean up in a finally block instead.
tmp_dir = tempfile.mkdtemp()
db_path = os.path.join(tmp_dir, "batch-received.sqlite")
try:
    conn = sqlite3.connect(db_path)
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
    conn.close()
    require("SQLite 行为模拟中仅两条待收记录发生状态更新",
            changed == 2 and rows[1][0] == "received" and rows[2][0] == "received" and rows[3][0] == "received")
    require("批量更新保留实收字段原值/空值及持仓成本",
            rows == {1: ("received", None), 2: ("received", 12.5), 3: ("received", 9.0)} and cost == 100.0)
finally:
    try:
        os.remove(db_path)
    except OSError:
        pass
    os.rmdir(tmp_dir)

print("通过：批量到账状态、两套入口覆盖与金额/成本保护回归；SQLite 部分为行为模拟，不替代 Android 真机验证。")
