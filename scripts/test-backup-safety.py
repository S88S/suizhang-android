#!/usr/bin/env python3
"""Host-side guard test for the import validation/transaction ordering.
This does not execute Android UI or the Android SQLite wrapper; APK runtime import
still requires a device. It checks the production source ordering and SQLite's
rollback behavior on a malformed second write.
"""
from pathlib import Path
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
start = source.index("void importJson(String json)")
end = source.index("private JSONArray requiredArray", start)
method = source[start:end]
assert method.index("validateBackup(accounts") < method.index("db.beginTransaction()"), "全量校验必须先于写事务"
assert method.index("db.beginTransaction()") < method.index('db.delete("accounts"'), "替换必须处于数据库事务中"
assert method.index('db.delete("accounts"') < method.index("insertRows(db, \"accounts\""), "恢复必须在清理后写入"
assert method.index("insertRows(db, \"app_settings\"") < method.index("db.setTransactionSuccessful()"), "只有所有写入完成后才提交"

# Simulate a malformed later row after the existing data has been deleted and a
# partial new row inserted: rollback must restore the pre-existing account/data.
con = sqlite3.connect(":memory:")
con.execute("CREATE TABLE ledger(id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
con.execute("INSERT INTO ledger VALUES(1, 'existing')")
con.commit()
try:
    con.execute("BEGIN")
    con.execute("DELETE FROM ledger")
    con.execute("INSERT INTO ledger VALUES(2, 'partial')")
    con.execute("INSERT INTO ledger VALUES(2, 'corrupt duplicate id')")
    con.commit()
    raise AssertionError("malformed restore unexpectedly committed")
except sqlite3.IntegrityError:
    con.rollback()
rows = con.execute("SELECT id,value FROM ledger").fetchall()
assert rows == [(1, "existing")], f"失败恢复覆盖了原数据: {rows}"
print("通过：导入全量验证在事务前；异常写入会回滚并保留既有数据（结构护栏 + SQLite 回滚模拟）")
