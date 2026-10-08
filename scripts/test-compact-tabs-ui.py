#!/usr/bin/env python3
"""Source-level regressions for compact root tabs and account-scope continuity."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
build = (root / "app/build.gradle").read_text(encoding="utf-8")
database = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError(f"FAIL {label}")
    print(f"PASS {label}")

def section(start: str, end: str) -> str:
    begin = main.index(start)
    finish = main.index(end, begin)
    return main[begin:finish]

render = section("private void render()", "private com.google.android.material.bottomnavigation.BottomNavigationView bottomNav()")
holdings = section("private void showHoldings()", "private LinearLayout metric(")
calendar = section("private void showCalendar()", "private CardColumn calendarControlPanel()")
calendar_controls = section("private CardColumn calendarControlPanel()", "private MaterialButton calendarNavButton(")
calendar_context_button = section("private MaterialButton calendarContextButton(", "private void showCalendarYear()")
more = section("private void showMore()", "private void showStatsPage()")
account_card = section("private void showAccountCard()", "private void showTaxSettingsCard()")
stats = section("private void showStatsPage()", "private void showStats()")

require("品牌和账户 selector 仅在总览 header 创建", 'if ("home".equals(activeTab))' in render and render.index('if ("home".equals(activeTab))') < render.index('MaterialButton accountButton') and 'accountButton.setOnClickListener(v -> chooseAccount())' in render and "ic_expand_more_24" in render)
require("持仓、日历、更多 root 移除重复页头", all("pageTitle(" not in body for body in (holdings, calendar, more)))
require("深层统计页仍保留上下文标题", 'pageTitle("统计报表"' in stats)
require("持仓与日历显示紧凑的全局账户范围提示", 'accountScopeLabel()' in holdings and 'accountScopeLabel()' in calendar_controls)
require("更多页面不再提供第二个账户切换 selector", 'chooseAccount()' not in account_card and '切换账户' not in account_card and '当前筛选范围：' in account_card)
require("全局账户筛选仍进入持仓、月历和年度查询", 'db.holdings(selectedAccount)' in holdings and 'db.dividends(selectedAccount, ym)' in calendar and 'db.dividendsInYear(selectedAccount, year)' in main)
require("活动 tab、账户范围、日历视图和说明状态会保存恢复", 'outState.putString("ui_active_tab", activeTab)' in main and 'outState.putLong("ui_selected_account", selectedAccount)' in main and 'ui_calendar_help_expanded' in main and 'ui_calendar_year_view' in main)
require("日历说明默认折叠且只由显式点击切换", 'private boolean calendarHelpExpanded;' in main and 'calendarHelpExpanded = !calendarHelpExpanded; render();' in main)
require("日历说明交互目标至少 48dp 且有可访问展开状态", 'calendarContextButton("日历说明"' in calendar_controls and 'dimen(R.dimen.ds_touch_target_min)' in calendar_context_button and 'setCheckable(true)' in calendar_controls and '已展开，点击收起' in calendar_controls and '已折叠，点击展开' in calendar_controls)
require("折叠面板说明登记日、除息日、派息日和实收金额口径", '到账金额优先显示你填写的实收额' in main)
require("无视觉根页标题时仍设置 TalkBack 页面上下文", 'content.setAccessibilityPaneTitle(accessibilityPaneTitle())' in render and 'return "持仓"' in main and 'return "日历"' in main and 'return "更多"' in main)
require("系统栏、挖孔、手势区和 IME inset 被应用到根布局", 'WindowCompat.setDecorFitsSystemWindows(getWindow(), false)' in main and 'Type.systemBars()' in main and 'Type.displayCutout()' in main and 'Type.systemGestures()' in main and 'Type.ime()' in main and 'SOFT_INPUT_ADJUST_RESIZE' in main)
require("日历滚动区保留底部可达空间且不裁切 padding", 'scroller.setClipToPadding(false)' in render and 'dp("home".equals(activeTab) ? 96 : 32)' in render)
require("版本只升级到 1.4.5 / code 19", "versionName '1.4.5'" in build and re.search(r"versionCode\s+19\b", build) is not None)
require("包名不变且数据库 schema 升至 4", "applicationId 'cn.suizhang.ledger'" in build and "applicationIdSuffix '.debug'" in build and re.search(r"DB_VERSION\s*=\s*4", database) is not None and 'android:name=".MainActivity"' in manifest)
print("通过：compact-tabs 源码级回归完成；insets、字体缩放和末周可达性仍需设备屏幕验收。")
