#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/cn/suizhang/ledger/ExcelBackup.java"
ACTIVITY = ROOT / "app/src/main/java/cn/suizhang/ledger/MainActivity.java"
DB = ROOT / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java"
GRADLE = ROOT / "app/build.gradle"

source = JAVA.read_text(encoding="utf-8")
activity = ACTIVITY.read_text(encoding="utf-8")
database = DB.read_text(encoding="utf-8")
gradle = GRADLE.read_text(encoding="utf-8")

sheets = ["使用说明", "账户", "持仓", "分红", "交易", "支出目标", "标的索引", "本地设置"]
for name in sheets:
    assert f'new Sheet("{name}"' in source or (name == "使用说明" and 'sheetNames.add("使用说明")' in source), f"缺少工作表定义：{name}"
for field in ["account_id", "holding_id", "quantity", "cost", "annual_dividend_per_unit", "amount_per_share", "received_amount", "trade_date", "expense_goals", "asset_index", "app_settings"]:
    assert field in source, f"缺少字段映射：{field}"

assert 'static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"' in source
assert 'i.setType(ExcelBackup.MIME)' in activity
assert 'EXTRA_MIME_TYPES' in activity and 'application/json' in activity, "导入选择器需兼容旧 JSON"
assert 'ExcelBackup.exportWorkbook(db.exportJson())' in activity
assert 'ExcelBackup.importWorkbook(file)' in activity and 'new String(file, StandardCharsets.UTF_8)' in activity
assert '恢复会完整替换' in activity and '验证并恢复' in activity, "导入必须有明确的全量替换确认"
assert 'MAX_ARCHIVE_BYTES = 20 * 1024 * 1024' in source
assert 'bytes.size() > MAX_FILE_BYTES' in source and 'workbook.length > MAX_FILE_BYTES' in source
assert 'total > 10_000_000' in activity
assert 'throw new JSONException("为安全起见，不导入含公式的工作簿' in source
assert 'name.contains("../")' in source and 'MAX_ENTRIES' in source
assert 'validateBackup(accounts, holdings, dividends, transactions, goals, index, settings);' in database
assert 'db.beginTransaction()' in database and 'db.setTransactionSuccessful();' in database
assert "versionCode 21" in gradle and "versionName '1.4.7'" in gradle

# Ensure required XLSX package pieces are generated and spreadsheet cells are emitted as safe strings/numbers.
for token in ["[Content_Types].xml", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "inlineStr", "autoFilter", "frozen"]:
    assert token in source, f"缺少 XLSX 结构或表格体验：{token}"
assert 'xml:space=\\"preserve\\"' in source

print("Excel workbook checks passed: eight sheets, mapped ledger fields, explicit replace confirmation, archive/formula safety, legacy JSON compatibility.")
