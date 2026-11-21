#!/usr/bin/env python3
"""Keep source-push CI from publishing APK artifacts."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")


def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)


require(
    "CI still compiles, lints, and runs JUnit",
    "run: ./gradlew --no-daemon assembleDebug lint testDebugUnitTest" in WORKFLOW,
)
require(
    "CI retains test reports for diagnosis",
    "name: Upload test reports" in WORKFLOW and "path: app/build/reports/tests/" in WORKFLOW,
)
require(
    "CI does not upload a debug APK",
    re.search(r"(?im)^\s*-\s*name:\s*Upload\s+debug\s+APK\s*$", WORKFLOW) is None
    and re.search(r"(?im)^\s*path:\s*.*(?:app/build/outputs/apk|\.apk\b)", WORKFLOW) is None,
)

print("通过：源码推送工作流保留验证但不发布 APK。")
