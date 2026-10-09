# 文档索引

穗账的说明文档按用途归入三类，避免版本记录堆积在仓库根目录。

## releases/ — 版本发布记录

每个正式版本一份升级说明，记录该版本的行为变化、边界与验收范围。

| 文件 | 说明 |
|---|---|
| [`UPGRADE_1.4.7.md`](UPGRADE_1.4.7.md) | 1.4.7 Excel 账本工作簿（当前版本） |
| [`UPGRADE_1.4.6.md`](UPGRADE_1.4.6.md) | 总览与日历精修、可选指标自定义 |
| [`UPGRADE_1.4.5.md`](UPGRADE_1.4.5.md) | 麦金色主题与 Miuix 风格更新 |
| [`UPGRADE_1.4.4.md`](UPGRADE_1.4.4.md) | A 股税批完整性修复 |
| [`UPGRADE_1.4.3.md`](UPGRADE_1.4.3.md) | 日历与公式对齐 |
| [`UPGRADE_1.4.2.md`](UPGRADE_1.4.2.md) | 材料控件与涨红跌绿 |
| [`UPGRADE_1.4.1.md`](UPGRADE_1.4.1.md) | 日历控件与分红视觉精修 |
| [`UPGRADE_1.4.0.md`](UPGRADE_1.4.0.md) | 1.4.0 验收边界 |
| [`CHANGE-SUMMARY-1.4.0.md`](CHANGE-SUMMARY-1.4.0.md) | 1.4.0 变更总览 |

## design/ — 设计口径与计算边界

改动界面令牌或财务算法前，先读对应文件。

| 文件 | 说明 |
|---|---|
| [`DESIGN.md`](DESIGN.md) | 设计准则与可抽取令牌。界面实际取值以 `values/colors.xml`、`values-night/colors.xml`、`values/dimens.xml` 为准，改动须同步维护并运行 `scripts/test-design-tokens.py` |
| [`FORMULA_ALIGNMENT_1.4.3.md`](FORMULA_ALIGNMENT_1.4.3.md) | 成本与收益公式口径 |
| [`TAX_COVERAGE_1.4.4.md`](TAX_COVERAGE_1.4.4.md) | A 股税批覆盖判定边界 |

## compliance/ — 数据源、许可与已知问题

发行前必读。这三份文件共同说明"哪些数据可信、哪些不能信、哪些还有问题"。

| 文件 | 说明 |
|---|---|
| [`data-source-validation.md`](data-source-validation.md) | 数据源抽样记录：实际查询 URL、响应状态、字段结构与已知限制 |
| [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) | 第三方组件来源、版本与许可 |
| [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) | **未修复问题的唯一权威清单** |

---

## 新增版本文档的约定

1. 升级说明写入 `releases/`，命名为 `UPGRADE_<版本号>.md`
2. 同步在根目录 [`README.md`](../README.md) 的版本条目中追加一行摘要并链接
3. 若涉及公式、税务或设计令牌变更，**同时**更新 `design/` 下对应文件
4. 若引入新的第三方组件或数据源，**同时**更新 `compliance/` 下两份文件

## 回归脚本

文档与源码的一致性由`scripts/` 下的回归脚本保证，涉及上述文件时须运行：

```bash
python3 scripts/test-design-tokens.py      # DESIGN.md 令牌与 Android 资源一致
python3 scripts/test-formula-alignment.py  # 公式口径与源码实现一致
python3 scripts/test-overview-metrics.py   # 总览指标定义一致
python3 scripts/test-calendar-redesign.py  # 日历版式与设计文档一致
python3 scripts/test-calendar-polish.py    # 第三方许可声明同步
```

> **Windows 提示**：`test-formula-alignment.py`、`test-dividend-persistence.py` 使用
> `tempfile.NamedTemporaryFile` 后直接交给 SQLite，在 Windows 上会因文件被独占而报
> `unable to open database file`；`test-easternfortune-ocr.py` 依赖 PATH 中的 `javac`。
> 这些脚本在 Linux/macOS 下正常，本机复现失败不代表代码有问题。
