#!/usr/bin/env python3
"""Keep source-push CI from publishing APK artifacts."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
gradle_run = re.search(r"(?m)^\s*run:\s*\./gradlew\s+--no-daemon\s+([^\n]+)$", WORKFLOW)
gradle_tasks = set(gradle_run.group(1).split()) if gradle_run else set()


def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)


require(
    "CI builds Debug and Release, lints, and runs JUnit",
    {"assembleDebug", "assembleRelease", "lint", "testDebugUnitTest"} <= gradle_tasks,
)
require(
    "CI retains test reports for diagnosis",
    "name: Upload test reports" in WORKFLOW and "path: app/build/reports/tests/" in WORKFLOW,
)
require(
    "CI does not upload APK or AAB packages",
    re.search(r"(?im)^\s*-\s*name:.*\b(?:APK|AAB)\b", WORKFLOW) is None
    and re.search(
        r"(?im)^\s*(?:path|name):.*(?:app/build/outputs/(?:apk|bundle)|\.(?:apk|aab)\b)",
        WORKFLOW,
    ) is None,
)
require(
    "CI does not create or publish a GitHub Release",
    re.search(
        r"(?i)(?:softprops/action-gh-release|actions/create-release|actions/upload-release-asset|"
        r"ncipollo/release-action|\bgh\s+release\s+(?:create|upload)\b)",
        WORKFLOW,
    ) is None,
)

print("通过：源码推送工作流验证源码，但不上传 APK/AAB 或创建 Release。")
