# 穗账 Android —— 交接问询清单

> 背景：本仓库的 1.4.7 版本由另一位作者（AI 助手）完成。孙烨苏林（仓库所有者 S88S）
> 转由我接手改进，但作者身份与工程全貌均未直接交接。为避免误改，以下问题请原作者逐条答复。
>
> 答复方式：直接在本文档对应问题下追加答案即可；或在本仓库开issue 回答。

---

## 第一部分：阻塞级问题（不答清楚我不敢动核心代码）

### Q1. 公开仓库是完整工程，还是精简后的子集？

**我观察到的事实**：
- `app/src`共 41 个文件、4,868 行 Java
- `MainActivity.java` 单文件 2,291 行（211 KB），占全项目 47%
- `app/src/main/res/` 下 24 个 XML **几乎全是图标**（`ic_nav_home`、`ic_nav_calendar` 等），
  **没有 `res/layouts/` 目录**
- `app/build.gradle` 未声明任何布局或界面相关资源

**问题**：完整的 Activity 界面代码、布局 XML、字符串资源、主题样式，
现在在哪里？是尚未同步到公开仓库，还是已经废弃/重构掉了？

**为什么必须问**：如果界面代码不在此仓库，我无法对界面做任何改动或验证，
所有工作只能限于 `FinanceMath`、`LedgerDatabase` 等纯逻辑类。

---

### Q2. `MainActivity.java` 2,291 行是什么结构？

**我观察到的事实**：该文件占全项目 47%，包含 `MainActivity` 及其全部内部类。

**问题**：
1. 这 2,291 行里，界面构建代码、财务计算、数据访问各占多少？
2. 是否有拆分计划（Fragment / ViewModel / 独立类）？拆分过的历史记录在哪？
3. 若尚未拆分，是否接受拆分？拆分的边界你倾向怎么划？

**为什么必须问**：2,291 行的单文件是维护风险的集中点，但我需要知道这是"有意的技术选择"
还是"尚未处理的历史包袱"，才能判断该不该动、怎么动。

---

### Q3. `LedgerDatabase.java`（619 行）的 schema 4 迁移，是否有真实迁移测试？

**我观察到的事实**：
- `scripts/test-formula-alignment.py` 等脚本用 `sqlite3.connect(":memory:")` 建临时库验证SQL，
  但**同时**用 `source.index("private ...")` 读取 Java 源码文本做字符串断言
- `scripts/test-backup-safety.py` 的注释明确写着：
  *"This does not execute Android UI or the Android SQLite wrapper; APK runtime import
  still requires a device."*
- `KNOWN_ISSUES.md` 记录：一份 JSON 备份导入闪退**尚未复现，根因未确认**

**问题**：
1. schema 3 → 4 的迁移路径，是否在**真实 SQLite 库**上验证过？
   即：建一个 schema 3 的库，跑迁移，检查数据是否正确保留？
2. `importJson` 的事务回滚，是否在 Android `SQLiteDatabase` 上实测过？
   （Python 的 `sqlite3` 与 Android 的 `SQLiteDatabase` 行为可能不同）
3. 那份导致闪退的 JSON 备份：**它的结构长什么样**？
   是否有脱敏后的样本可以放进仓库做回归 fixture？
4. 闪退的堆栈日志是否还有？

**为什么必须问**：这是全项目唯一的**已知未修复缺陷**，而且正好落在"账本不丢数据"
这个最不能出错的地方。我需要知道现有测试到底覆盖到了哪一层。

---

### Q4. `ExcelBackup.java`（532 行）的 8张表，有没有真实读写往返测试？

**我观察到的事实**：`test-excel-backup.py` 全部是**源码文本断言**——
检查 `new Sheet("账户")` 是否存在、字段名是否出现、字符串常量是否匹配。
**没有任何一处真正执行 ExcelBackup 生成或解析 .xlsx 文件。**

**问题**：
1. 导出→导入往返（export → import → 再 export）是否在设备上实测过？
2. 导入时对公式（`=SUM(...)`）、超大数据、非法类型、ID 断链的拒绝逻辑，
   是运行时验证还是仅代码审查？
3. `app/build.gradle` 里**没有声明 Apache POI 依赖**，
   但README 说用了 .xlsx。**Excel 读写究竟用的什么库？**
   （若是运行时反射或自带精简实现，请说明）

**为什么必须问**：Excel 是 1.4.7 的**新增主功能**，也是用户唯一的数据迁移出口。
"文本断言全绿"和"文件真的能打开"是两件事。

---

## 第二部分：重要问题（影响我的改进方向选择）

### Q5. 为什么不用 JUnit？

**我观察到的事实**：`scripts/` 下有 6 个 Java 测试类（`FinanceMathTest` 等），
用 `javac` + `java` 直接跑。`app/src/test/` 目录不存在，`testDebugUnitTest` 输出 `NO-SOURCE`。

**问题**：
1. 是刻意选择（避免 Android Gradle 构建开销？还是为了能在无 SDK 环境跑）？
2. 如果我把这 6 个测试类迁到标准 `app/src/test/`（JUnit + Robolectric），
   你希望保留现在的脚本形态，还是替换？

**我的倾向**：**保留现有形态**。你的脚本不依赖 Android SDK，在任何有 JDK 的机器上都能跑，
这个优势不该丢。我已按这个思路补了 `test-finance-edge.sh`（同构，不依赖 SDK）。

---

### Q6. 界面布局有没有任何自动化验证？

**我观察到的事实**：README 反复强调"未进行运行时点按和屏幕截图验收"，
"表单继续使用限高滚动窗体、wrap-content 输入容器和系统键盘 resize，
源码检查了触控目标与动态文字规则，但不能替代小屏/大字体实机验证"。

**问题**：
1. 目前界面改动后，**靠什么手段确认没改坏**？纯人工肉眼？
2. 有没有截图存档？`KNOWN_ISSUES.md` 提到 `**/screenshot-holdings-*.json` 被 gitignore，
   这个 JSON 是什么？能否作为回归基线？
3. 是否接受引入 [Paparazzi](https://github.com/cashapp/paparazzi)？
   它能在 JVM 里渲染 XML 布局出 PNG，不需要真机——
   **但前提是仓库里有 `res/layouts/`**，而目前没有（见 Q1）。

---

### Q7. 美股分红和汇率，为什么放弃？

**我观察到的事实**：README 写"美股分红端点指定供应商字段时明确返回字段不存在，
去掉该字段重试仍读取超时，因此没有接入美股自动分红"；
"汇率端点抽样返回 USD 汇率 JSON，本轮没有接入"。

**问题**：
1. 是"接口本身不可用"还是"时间不够没做完"？
2. 如果接口修好了，你希望接入吗？
3. [akshare](https://github.com/akfamily/akshare)（22.8k★）有美股和汇率数据，
   但它是 Python 库，不能直接用在 Android 里。
   **能否接受"构建期用 Python 拉数据打进 assets 静态文件"的做法？**
   （来源与更新时间会照常标注，不违反"不伪装数据"原则）

---

### Q8. 数据源的合规底线是什么？

README 自己写着："测试响应成功不能证明服务稳定、准确、得到官方授权或允许商用/长期再分发；
**发行前应取得相应服务许可**。"

**问题**：
1. 穗账的发布形态是什么？**只在GitHub 传源码、不发APK**（当前状态），
   还是打算上F-Droid / Play / Gitee？
2. 如果不发布、只是自用，"取得许可"这条是否实际上不适用？
3. 你对"当前依赖东财/腾讯公开接口"这件事的风险判断是什么？

**为什么必须问**：这决定了我该不该投入精力做数据源冗余。
如果只是自用，做冗余的收益远低于自建同步。

---

## 第三部分：确认性问题（有明确是非，可快速答复）

### Q9. 三种成本算法的切换语义

README 说"算法切换以当前成本为新起点，不回算切换前的交易/分红"。

**问题**：切换算法时，**历史已确认的到账金额（`received_amount`）会被重算吗**？
我读`FinanceMath.java` 的理解是 `costAfterReceivedDividend` 只对
`COST_DIVIDEND_ADJUSTED` 生效，其余算法原样返回 unitCost。**对吗？**

### Q10. A股税率三档的日期边界

`aShareRecordDateTaxRate` 的实现：
- 持有期 ≤ 1 个月 → `shortRate`
- ≤ 1 年 → `mediumRate`
- \> 1 年 → `longRate`

**问题**：边界用的是 `!recordDate.isAfter(lot.acquiredOn.plusMonths(1))`，
即**恰好满 1 个月仍算短档，满 1 个月 1 天才算中档**。
这与实际税则表述（"持股超过 1 个月"）一致吗？有没有法律依据可以写进注释？

### Q11. `weightedBuyCost` 与 `costAfterBuy` 的区别是什么？

两个方法做同一件事（买入后加权成本），但参数校验不同：
`weightedBuyCost` 检查 `Double.isFinite` 不完整，`costAfterBuy` 检查更严。

**问题**：`weightedBuyCost` 现在还在被调用吗？还是已经是死代码？
（死代码的话我想删掉，但需要你确认没有反射或 XML 引用）

### Q12. `project()` 的 DRIP 模型是否有意简化？

当前模型每年算一次 `gross = opening × annualYield`，**没有按月或按季度复利**。

**问题**：这是"年化收益率已经涵盖复利"的简化，还是尚未实现更精细的模型？
如果是简化，README 里应该说明，否则用户可能误以为是月复利。

### Q13. MainActivity 里的"待确认"状态如何传播？

多处强调"未知时显示税务未确认，不表示免税"、"未知金额显示暂无数据，不伪装为零"。

**问题**：这些"未知"状态在数据模型里是用 `NaN`、`null` 还是额外的 boolean 字段表达？
**有没有统一约定？** 我想确保新增代码遵守同一套语义。

---

## 第四部分：我的改进计划（供你否决）

基于以上，我会按此优先级推进。**如果你觉得某项不该做，直接说。**

| 优先级 | 事项 | 依据 |
|---|---|---|
| P0 | 为 `LedgerDatabase` 补**真实 SQLite 迁移测试**（schema 3 → 4） | Q3；现有只测源码文本 |
| P0 | 为 `ExcelBackup` 补**真实 .xlsx 往返测试** | Q4；1.4.7 新增主功能无运行时验证 |
| P1 | 复现 JSON 导入闪退，拿到根因 | Q3；唯一的已知未修复缺陷 |
| P1 | `MainActivity.java` 拆分 | Q2；单文件占比过高 |
| P2 | 引入 Paparazzi 做布局截图回归 | Q6；**取决于 Q1 答复** |
| P2 | 数据源冗余或改为构建期静态数据 | Q7、Q8；取决于发布形态 |
| P3 | `git-cliff` 自动生成 CHANGELOG | 手写 9 份 UPGRADE 文档易漏 |
| P3 | `detekt` 静态检查 | 补足无静态分析的缺口 |

**已经做掉的部分**（已推分支 `chore/docs-structure-and-finance-edge-tests`，
PR 尚未创建，等确认）：

- `docs/` 产物归类：16 份文档按 releases / design / compliance 三类归位，
  同步更新 README 15 处链接与 4 个脚本的硬编码路径
- `FinanceMathEdgeTest.java`：新增 120 项边界断言（空批次、浮点容差、闰年、
  DRIP 行自洽性等既有测试未覆盖的边界），配套 `test-finance-edge.sh`，**不依赖 Android SDK**
- **修复一处真实缺陷**：`FinanceMath.annualExpense` 缺少 `!Double.isFinite(amount)` 校验，
  传入 `Infinity` / `NaN` 会静默返回无效值并污染下游统计。
  同文件其余方法均已做此校验，判定为遗漏。**改动仅 1 行。**
- `.gitattributes`：锁定 `*.sh` 为 LF，避免 Windows 检出 CRLF 导致脚本不可执行

---

## 第五部分：环境信息（可能需要你协助）

我在这台机器上已完成：JDK 21 安装、`javac` 直跑全部纯计算回归
（53 + 120 项全部通过）、Git 推送。

**仍缺**：Android SDK 未安装，因此
`./gradlew clean assembleDebug lint` **无法运行**。
上述 P0 优先级里的迁移测试和 Excel 往返测试，**都需要 Android SDK 或 Robolectric**。

**问题**：
1. 完整工程里是否已有 Android SDK？路径在哪？
2. 迁移测试能否在 **Robolectric**（JVM 内模拟 Android SQLite）下做？
   如果你之前试过并发现不可行，请告诉我，我换方案。
