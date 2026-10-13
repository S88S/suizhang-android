#!/usr/bin/env python3
"""Guard the R8 keep rules that runtime reflection depends on.

Minification renames classes, so anything instantiated by name fails at
runtime with ClassNotFoundException rather than at compile time. These
assertions cover the reflective entry points this project actually has.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
rules = (root / "app/proguard-rules.pro").read_text(encoding="utf-8")
build = (root / "app/build.gradle").read_text(encoding="utf-8")
database = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")


def require(label: str, ok: bool) -> None:
    if not ok:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)


require("release 开启 R8 并同时收缩资源",
        "minifyEnabled true" in build
        and "shrinkResources true" in build
        and "proguard-rules.pro" in build)

require("Worker 子类保留类名与 (Context, WorkerParameters) 构造器",
        "-keep class * extends androidx.work.Worker" in rules
        and "<init>(android.content.Context, androidx.work.WorkerParameters)" in rules
        and "androidx.work.ListenableWorker" in rules)

require("ListenableWorker.Factory 实现保留无参构造器",
        "androidx.work.ListenableWorkerFactory" in rules)

require("SQLiteOpenHelper 子类保留全部构造器",
        "-keep class * extends android.database.sqlite.SQLiteOpenHelper" in rules
        and "<init>(...);" in rules)

require("自定义 View 保留三参构造器（XML 反射实例化）",
        "-keep public class * extends android.view.View" in rules
        and "public <init>(android.content.Context);" in rules
        and "public <init>(android.content.Context, android.util.AttributeSet);" in rules)

require("崩溃报告保留行号但隐藏源文件名",
        "-keepattributes SourceFile,LineNumberTable" in rules
        and "-renamesourcefileattribute SourceFile" in rules)

require("混淆规则未放宽到全量保留（否则等于没开）",
        not any(line.strip() in ("-dontobfuscate", "-dontshrink", "-keep class cn.suizhang.ledger.** { *; }")
                for line in rules.splitlines()))

require("数据库 schema 为 5（混淆不改结构）",
        "DB_VERSION = 5" in database and "oldVersion < 6" not in database)

require("无效的 org.json keep 规则已移除（org.json 来自 android.jar，R8 不会裁剪）",
        "-keepclassmembers class org.json.**" not in rules
        and "org.json ships with the Android runtime" in rules)

print("通过：R8 混淆配置与 keep 规则回归完成；反射入口的保留只能证明规则存在，真机仍需冒烟验证。")