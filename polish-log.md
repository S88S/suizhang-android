# Polish log — 穗账 Android 2.0.2

## Status

Native-source refinement is implemented, but screenshot-based visual polish is **not complete**. Huashu's approved-direction workflow requires reviewing rendered screens; source inspection and tests do not substitute for those screenshots. Do not describe the 2.0.2 design as visually accepted on a device.

## Source-level audit and applied refinements (not screenshot rounds)

The prior screen references are the user-provided earlier device capture `/home/ubuntu/upload/2dce743ab01c2604e60f27a3_40279.jpg` and the selected hybrid concept `/home/ubuntu/upload/adbf79667a72d9ceaaaa29de_40280.png`. Neither is a render of the current build.

1. **Overview hierarchy:** the annual estimate previously dominated while monthly cash flow was secondary. The overview now leads with “月均预计分红”, labels it as annual estimate divided by 12 and a planning reference, and places the annual total after it. No forecast calculation or currency conversion changed.
2. **Calendar year control spacing:** the “回到今年” button had avoidable vertical separation from the year. The extra top margin and button top padding were removed while preserving the 48dp minimum touch target.
3. **Holdings explanation density:** the long valuation/currency note occupied the page even when not needed. It now starts collapsed behind a 48dp disclosure row whose accessible name and chevron expose the expanded/collapsed state.

Source assertions were added or updated for these behaviors. They are static regressions, not pixel-level or accessibility-device verification.

## Runtime visual pass — pending

No Android device is connected in this execution (`adb devices` is empty), and the environment has no `/dev/kvm`. Earlier isolated emulator attempts did not enter the app, so they produced no usable screenshot. Therefore the following remain unverified: alignment and centering at runtime; clipping, wrapping, overlap, and large-text behavior; narrow-screen layout; light/dark appearance; actual bottom-tab selected state; chart guidance and more-menu folding; and screen-reader interaction. No first/last screenshot comparison exists, and Huashu's two screenshot-based polish rounds for a selected direction are not claimed as complete.

To finish this gate, capture the running native app on a device or usable emulator, inspect all four primary screens at normal and narrow widths and larger font settings, record the three most harmful findings, make the corrections, and repeat the screenshot review. Keep any real ledger information out of published screenshots; if example data is used, label it as fictional.
