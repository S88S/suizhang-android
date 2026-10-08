#!/usr/bin/env python3
"""Regression checks for removing only the planning tab, not stored ledger data."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
menu_path = root / "app/src/main/res/menu/bottom_navigation.xml"
strings_path = root / "app/src/main/res/values/strings.xml"
activity_path = root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java"
database_path = root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java"
menu_text = menu_path.read_text(encoding="utf-8")
strings_text = strings_path.read_text(encoding="utf-8")
activity = activity_path.read_text(encoding="utf-8")
database = database_path.read_text(encoding="utf-8")
android_id = "{http://schemas.android.com/apk/res/android}id"

def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError(f"FAIL {label}")
    print(f"PASS {label}")

items = ET.parse(menu_path).getroot().findall("item")
item_ids = [item.attrib[android_id].split("/")[-1] for item in items]
require("底部菜单恰为总览、持仓、日历、更多四项并保持顺序", item_ids == ["nav_home", "nav_holdings", "nav_calendar", "nav_more"])
require("菜单与主界面不再引用规划导航资源", "nav_plan" not in menu_text and "nav_plan" not in activity)
require("规划不再有可触达的 tab 路由", 'case "plan":' not in activity)
require("四栏显示标签且关闭移动缩放以均匀排布", "LABEL_VISIBILITY_LABELED" in activity and "setItemHorizontalTranslationEnabled(false)" in activity)
require("规划专属菜单文案和图标资源引用已移除", "nav_plan" not in strings_text and not (root / "app/src/main/res/drawable/ic_nav_plan.xml").exists())
require("SQLite schema 升至 4 且不删除支出目标/备份", re.search(r"DB_VERSION\s*=\s*4", database) is not None)
require("支出目标表及其完整 JSON 备份字段仍保留", 'CREATE TABLE IF NOT EXISTS expense_goals' in database and 'root.put("expense_goals"' in database)
require("DRIP 历史设置仍保留在既有设置表/完整备份中", '"drip_yield"' in database and 'root.put("app_settings"' in database)
print("通过：规划导航移除回归；本测试检查资源/路由和数据保留，不替代设备屏幕测试。")
