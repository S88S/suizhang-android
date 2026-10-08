#!/usr/bin/env python3
"""Source-level regression checks for dialog handoff and form typography.
These checks do not replace Android screenshot/device instrumentation tests.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
build = (root / "app/build.gradle").read_text(encoding="utf-8")

def require(name: str, condition: bool) -> None:
    if not condition:
        raise AssertionError(f"FAIL {name}")
    print(f"PASS {name}")

def method(start: str, end: str) -> str:
    a = source.index(start)
    b = source.index(end, a)
    return source[a:b]

field = method("private LinearLayout field(", "private EditText edit(")
holding = method("private void showHoldingEditor(", "private interface CatalogPickCallback")
search = method("private void beginSecuritySearch(CatalogPickCallback callback, AlertDialog returnTo)", "private boolean isFundCategory(")
catalog = method("private void beginCatalogSearch(CatalogPickCallback callback, AlertDialog returnTo)", "private String defaultCurrency(")
dividend = method("private void showDividendDialog(", "private void showTransactionDialog(")
scroll = method("private ScrollView wrapForm(", "private LinearLayout field(")

require("TextInputLayout 使用唯一浮动标签，子 EditText hint 在挂接前清除", "CharSequence placeholder = e.getHint(); e.setHint(null);" in field and field.index("e.setHint(null)") < field.index("box.addView(e"))
require("区别于标签的辅助提示作为 placeholder 显示而非叠加 hint", "box.setPlaceholderText(placeholder)" in field and "!placeholder.toString().contentEquals(label)" in field)
require("每个输入控件及 TextInputLayout 使用 wrap_content 高度", "new LinearLayout.LayoutParams(-1, -2)" in field and "new LinearLayout.LayoutParams(-1, dp(" not in field)
require("表单滚动区限制最大高度并保留较小父窗口的约束", "class FormScrollView extends ScrollView" in scroll and "heightPixels * .78f" in scroll and "MeasureSpec.AT_MOST" in scroll)
require("表单窗口在键盘弹起时请求 adjustResize", "SOFT_INPUT_ADJUST_RESIZE" in source and "showFormDialog(d)" in holding and "showFormDialog(d)" in search)
require("证券搜索前隐藏父持仓表单", "if (returnTo != null && returnTo.isShowing()) returnTo.hide();" in search)
require("搜索取消/系统返回恢复父表单且结果交接不会提前恢复", "handingOff[0]" in search and "restoreParentDialog(returnTo)" in search)
require("搜索结果、无匹配、异常和离线回退都建立关闭恢复路径", "restoreParentAfterDismiss(noMatch, returnTo)" in search and "restoreParentAfterDismiss(choices, returnTo)" in search and "showSecuritySearchError(returnTo)" in search and "showCatalogResults(query, callback, returnTo)" in search)
require("离线搜索也隐藏父表单并在取消/返回恢复", "returnTo.hide()" in catalog and "restoreParentDialog(returnTo)" in catalog and "restoreParentAfterDismiss(choices, returnTo)" in catalog)
require("搜索选中/手动新增在结果窗关闭后打开新表单", "new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(null, pre))" in search)
require("持仓表单搜索回填保留原视图且回调引用单个编辑器实例", "editorDialog[0]" in holding and "beginSecuritySearch(pre ->" in holding and "beginCatalogSearch(pre ->" in holding)
require("分红金额预览只注册一次文本监听并单独复算", dividend.count("perShare.addTextChangedListener") == 1 and "update.run();" in dividend)
require("分红表单使用滚动窗体且保存逻辑仍仅有一次", "wrapForm(f)" in dividend and dividend.count("db.addDividend(") == 1)
require("应用版本已递增到 1.4.5-debug / versionCode 19", re.search(r"versionCode\s+19", build) is not None and re.search(r"versionName\s+'1\.4\.5'", build) is not None and "applicationIdSuffix '.debug'" in build)

print("通过：源码级对话框回归完成；不等同于真机布局、字体缩放或触屏流程验证。")
