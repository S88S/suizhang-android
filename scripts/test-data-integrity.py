#!/usr/bin/env python3
"""Guards for data-layer and network correctness found in the 1.4.10 retrospective.

Each check maps to a defect that produced wrong numbers rather than a crash: a year filter that
silently dropped rows, a COALESCE that stood in a real 0, a LIKE pattern that matched everything,
and a User-Agent that had not moved since 1.3. Source assertions only — they prove the shape of
the query or guard, not that SQLite produced the right number on a device.
"""
from pathlib import Path
import re
import sqlite3
import tempfile
import os

root = Path(__file__).resolve().parents[1]
database = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
calendar = (root / "app/src/main/java/cn/suizhang/ledger/CalendarQuery.java").read_text(encoding="utf-8")
client = (root / "app/src/main/java/cn/suizhang/ledger/MarketDataClient.java").read_text(encoding="utf-8")
excel = (root / "app/src/main/java/cn/suizhang/ledger/ExcelBackup.java").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")


def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS  " + label)


# --- year filtering must not depend on how many digits the year has ---
income = database[database.index("JSONArray incomeSummary("):database.index("JSONArray receivedAllTime(")]
require("年度汇总用四位年份比较，与 pay_date 的截取宽度一致",
        'String.format(java.util.Locale.ROOT, "%04d", year)' in income
        and "substr(d.pay_date,1,4)=?" in income)
require("年度汇总不再用 String.valueOf(year)",
        "String.valueOf(year)" not in income)

# Note: COALESCE(received_amount, total) was checked against SQLite and is correct as written —
# it substitutes only on NULL, so a dividend that really paid 0 is kept at 0. Rewriting it as
# CASE WHEN ... IS NOT NULL would be a no-op. Left alone on purpose; the year padding below is
# the real defect, and the simulation at the bottom proves both claims.

# --- indexes that used to be missing ---
require("交易与分红补上 holding_id 索引",
        "idx_transactions_holding" in database and "idx_dividends_holding" in database)
require("已安装的库也能补上索引，无需升 schema",
        "CREATE INDEX IF NOT EXISTS" in database and "public synchronized void onOpen(SQLiteDatabase db)" in database)
require("schema 为 5，新增期初成本列以支持编辑后重算", re.search(r"DB_VERSION\s*=\s*5", database) is not None)

# --- LIKE wildcards ---
require("标的索引搜索转义 % 和 _，不再一条通配匹配全表",
        'replace("%", "\\\\%")' in database and 'ESCAPE' in database)

# --- calendar must not hide rows the totals still count ---
require("日历改用 LEFT JOIN，避免统计算进去、日历里却看不见",
        "LEFT JOIN holdings h" in calendar and "LEFT JOIN accounts a" in calendar
        and "JOIN holdings h ON" not in calendar.replace("LEFT JOIN holdings h ON", ""))

# --- backup validation ---
validate = database[database.index("private void validateBackup("):database.index("private void validateLinkedRows(")]
require("校验失败信息带上第几条记录",
        re.search(r'第 "\s*\+\s*\(i \+ 1\)', validate) is not None)
require("校验 NOT NULL 列：持仓的市场/币种/创建时间",
        "缺少市场、币种或创建时间" in validate)
require("校验 NOT NULL 列：分红的币种/创建时间",
        "缺少币种或创建时间" in validate)
require("校验分红 source_key 不重复，避免唯一索引在写入阶段炸掉整次恢复",
        "来源编号重复" in validate)
require("读取结果缺字段不再静默丢弃",
        'android.util.Log.w("Suizhang", "备份/查询结果缺少字段："' in database)

# --- Excel round-trip ---
require("Excel 导入校验 ZIP 文件头", "0x50" in excel and "0x4B" in excel)
require("Excel 导入拒绝重复 zip 条目",
        "Excel 文件内含重复条目" in excel)
require("Excel 写入的 schema 版本取自数据库常量，不再硬编码 4",
        'root.put("schema_version", LedgerDatabase.DB_VERSION)' in excel
        and 'root.put("schema_version", 4)' not in excel)

# --- network ---
require("行情请求分批，不再把所有代码拼进一个 URL",
        "QUOTE_BATCH_SIZE" in client and "start += QUOTE_BATCH_SIZE" in client)
require("User-Agent 版本号不再停在 1.3，且与 Gradle 版本一致",
        "APP_VERSION" in client
        and 'Suizhang-Android/" + APP_VERSION' in client
        and 'APP_VERSION = "1.4.11"' in client
        and "versionName '1.4.11'" in gradle)
require("HTTP 错误带上响应体片段，不再只有一个状态码",
        "errorBody(connection)" in client and "getErrorStream()" in client)
require("连接禁用缓存", "setUseCaches(false)" in client)

# --- SQLite behaviour: prove the COALESCE and year-format claims above ---
tmp_dir = tempfile.mkdtemp()
db_path = os.path.join(tmp_dir, "integrity.sqlite")
try:
    conn = sqlite3.connect(db_path)
    conn.execute("CREATE TABLE dividends (_id INTEGER PRIMARY KEY, pay_date TEXT NOT NULL, status TEXT NOT NULL,"
                 " currency TEXT NOT NULL, total REAL NOT NULL, received_amount REAL)")
    conn.executemany("INSERT INTO dividends (pay_date,status,currency,total,received_amount) VALUES(?,?,?,?,?)", [
        ("1000-01-05", "received", "CNY", 100.0, None),   # no amount recorded -> falls back to estimate 100
        ("1000-02-05", "received", "CNY", 250.0, 0.0),    # really paid 0
        ("0999-01-05", "received", "CNY", 70.0, 70.0),    # only reachable with a padded year
    ])
    conn.commit()

    # COALESCE substitutes only on NULL, so the dividend that really paid 0 stays 0 and the one
    # with no amount recorded falls back to its estimate. 0 + 100 = 100, never 0 + 250.
    coalesce_sum = conn.execute("SELECT SUM(COALESCE(received_amount,total)) FROM dividends"
                                " WHERE status='received' AND substr(pay_date,1,4)=?", ("1000",)).fetchone()[0]
    unpadded = conn.execute("SELECT COUNT(*) FROM dividends WHERE substr(pay_date,1,4)=?", ("999",)).fetchone()[0]
    padded = conn.execute("SELECT COUNT(*) FROM dividends WHERE substr(pay_date,1,4)=?", ("%04d" % 999,)).fetchone()[0]
    conn.close()

    require("COALESCE 只替换 NULL，实收 0 不会被估计值顶替（合计 100 而非 350）", coalesce_sum == 100.0)
    require("三位年份不补零会漏掉 0999 年的记录", unpadded == 0)
    require("用 %04d 补零后 0999 年的记录能被取到", padded == 1)
finally:
    try:
        os.remove(db_path)
    except OSError:
        pass
    os.rmdir(tmp_dir)

print("通过：数据层与网络正确性回归；SQLite 部分为行为模拟，不替代 Android 真机验证。")
