#!/usr/bin/env python3
"""Regression guards for the dividend-first overview and restrained add actions."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")

def section(start: str, end: str) -> str:
    return source[source.index(start):source.index(end, source.index(start))]

def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)

home = section("private void showHome()", "private void addTaxToggle()")
render = section("private void render()", "private com.google.android.material.bottomnavigation.BottomNavigationView bottomNav()")
quick = section("private void showHoldingQuickActions()", "private void addTaxToggle()")
holdings = section("private void showHoldings()", "private LinearLayout metric(")
calendar = section("private void showCalendar()", "private void addDividendRow(")
accounts = section("private void showAccountCard()", "private void showTaxSettingsCard()")

require("版本号只升至 1.4.0 / versionCode 14", "versionName '1.4.0'" in gradle and "versionCode 14" in gradle)
require("FAB 只在首页固定于导航栏上方右下角", 'if ("home".equals(activeTab))' in render and 'Gravity.END | Gravity.BOTTOM' in render and 'fabParams.setMargins(0, 0, dimen(R.dimen.ds_page_gutter), dp(18))' in render)
require("首页有滚动底部留白，避免内容遮在 FAB 下", 'dp("home".equals(activeTab) ? 96 : 32)' in render)
require("FAB 可访问名称和提示语明确", 'setContentDescription("新增持仓：手动新增或识别券商截图")' in render and 'setTooltipText("新增持仓")' in render)
require("FAB 图标使用本地矢量资源", 'setImageResource(R.drawable.ic_add)' in render and (root / "app/src/main/res/drawable/ic_add.xml").is_file())
require("新增菜单通过标准可取消对话框呈现两种操作", 'setItems(new String[]{"手动新增持仓", "识别券商截图"}' in quick and 'setNegativeButton("取消", null)' in quick)
require("手动表单与现有本地截图 OCR 流程均被复用", 'showHoldingEditor(null, null)' in quick and 'chooseBrokerScreenshot()' in quick and 'startLocalScreenshotOcr(uri)' in source)
require("首页年度估算先于持仓清单显示", home.index("预计年分红") < home.index("查看全部持仓"))
require("首页前四项按年分红估算从高到低排序", 'Collections.sort(ranked, (a, b) -> Double.compare(annualOf(b, false), annualOf(a, false)))' in home and 'Math.min(4, ranked.size())' in home)
require("首页标的项展示预估金额、持有量、来源和可用时的 YoC", 'amount(holding.optString("currency"), gross) + "/年"' in home and '"持有 " + compact(quantity)' in home and 'forecastSourceLabel(holding)' in home and 'YoC " + percent(gross / cost)' in home)
require("无年分红数据使用暂无数据而非伪造零金额", 'gross > 0d ? amount(holding.optString("currency"), gross) + "/年" : "暂无数据"' in home and 'hasEstimate ? formatAmounts(annual) : "暂无数据"' in home)
require("首页保留下一笔预期与通往日历的入口", 'sectionHeader("下一笔预期", "分红日历"' in home and 'db.nextDividends(selectedAccount' in home)
require("首页补充展示成本收益率与已确认到账", '成本收益率（YoC）' in home and '累计已确认到账' in home)
require("空持仓总览明确引导使用角落加号但不重复放大按钮", 'emptyInfoCard("还没有持仓"' in home and 'actionButton("＋ 新增持仓"' not in home)
require("持仓页仍可新增，但入口已降级为次级并共用两种方式弹层", 'actionButton("新增持仓", false, () -> showHoldingQuickActions())' in holdings)
require("分红日历手工录入为次级而非主色新增按钮", 'actionButton("手工录入", false' in calendar)
require("账户新增和证券搜索均降低主操作强调", 'actionButton("新增账户", false' in accounts and 'actionButton("在线搜索证券", false' in source)
require("分红取数失败与无完整历史记录各有空态", 'fetch_unavailable' in source and 'no_history' in source and '历史分红数据获取失败 · 可手工填写' in source and '无可用分红记录 · 不代表零分红' in source)

print("通过：1.3.8 分红优先总览/新增入口源码级回归；不替代 Android 设备视觉测试")
