#!/usr/bin/env python3
"""Parser fixtures and source-level import/privacy regression for Eastmoney-style screenshots."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
main_path = root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java"
parser_path = root / "app/src/main/java/cn/suizhang/ledger/EasternFortuneHoldingParser.java"
source = main_path.read_text(encoding="utf-8")
parser = parser_path.read_text(encoding="utf-8")
build = (root / "app/build.gradle").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
database = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")

def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)

def section(text: str, start: str, end: str) -> str:
    return text[text.index(start):text.index(end, text.index(start))]

with tempfile.TemporaryDirectory(prefix="easternfortune-parser-") as tmp:
    tmp = Path(tmp)
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp), str(parser_path), str(root / "scripts/EasternFortuneHoldingParserTest.java")], check=True)
    subprocess.run(["java", "-cp", str(tmp), "cn.suizhang.ledger.EasternFortuneHoldingParserTest"], check=True)

ocr_path = section(source, "private void chooseBrokerScreenshot()", "private void beginCatalogSearch()")
review = section(source, "private void showBrokerOcrReview(EasternFortuneHoldingParser.Result", "private void beginCatalogSearch()")
editor = section(source, "private void showHoldingEditor(", "private interface CatalogPickCallback")
require("本机 ML Kit 元素边界框输入解析器", "getElements()" in ocr_path and "element.getBoundingBox()" in ocr_path and "new EasternFortuneHoldingParser().parse(fragments, imageWidth, imageHeight)" in ocr_path)
require("详情页与多标的列表走独立分类及解析", "parseDetail" in parser and "parseList" in parser and "POSITION_LIST" in parser)
require("旧版全屏数字顺序正则已删除", "captureNumber(" not in source and "class OcrFields" not in source)
require("完整 OCR 原文不在预览中回显", "识别原文" not in source and "Text raw" not in review and "raw.setTextIsSelectable" not in review)
require("OCR 冲突明确标记待核对", "存在冲突 · 待核对，请手动填写" in review)
require("来源值按字段显示，准确状态与待确认状态可区分", "原标签/来源：" in review and "标签明确 · 仍需确认" in review and "按列位匹配 · 仍需确认" in review and "未识别 · 请手动填写" in review)
require("多股票列表先展示逐只候选，单项可编辑确认", "showBrokerOcrListReview" in review and "每次只进入一只持仓表单" in review and "showBrokerOcrSingleReview" in review)
require("OCR 失败和无法读取图片仍可进入空白手工核对", "addOnFailureListener" in ocr_path and "parseBrokerScreenshot(null" in ocr_path and "空白核对页手工录入" in ocr_path)
require("未解析日期保持空白并要求显式选有效日期", 'EditText date = dateEdit("", false)' in review and 'ocr_date_unparsed' in review and 'openedOn.setError("请选择有效日期")' in editor and "截图未识别；请选择" in editor)
require("历史交易/已到账记录只提示不导入", "历史交易/已到账记录不会从本次持仓识别导入" in review and "db.addTransaction(" not in ocr_path and "db.addDividend(" not in ocr_path)
require("预览不记录或展示股东号及原始整页文本", "不显示股东号" in review and "Text raw" not in review)
require("截图识别不新增权限，schema 4 仍不存 OCR 原文", "DB_VERSION = 4" in database and "uses-permission" in manifest and manifest.count("uses-permission") == 1 and "OCR" not in database)
require("本轮版本仅升至 1.4.5 / code 19", "versionName '1.4.5'" in build and "versionCode 19" in build)
print("通过：东方财富截图导入路径源码/结构回归完成；源码检查不替代 Android 设备目视测试。")
