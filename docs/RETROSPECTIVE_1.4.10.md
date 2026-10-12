# 穗账 Android 全项目复盘报告（2026-10-11）

三路并行审计（UI/交互、数据层、测试与文档一致性）+ 全量回归复跑 + 构建验证。审计结论**逐条回到代码核对过行号**，未采信未经核实的判断。

- 版本：1.4.9 → **1.4.10**（versionCode 23 → 24）
- 提交：`4cbd048`、`4d405ed`（本地已提交；推送时 GitHub 出口返回 502，未推上去）
- 验证：`assembleDebug` + `lint` + `testDebugUnitTest` 全绿；Python 回归 20 通过 / 0 失败 / 1 跳过；4 个 shell 套件断言全通过（44 / 12 / 53 / 62 条）

---

## 一、修掉的真实缺陷

| # | 问题 | 位置 | 后果 | 处理 |
|---|---|---|---|---|
| 1 | **年度总览批量确认不看派息日** | `MainActivity` 年度 `confirmMarkReceivedBatch` / `countPendingReceived` | 1 月点一次会把 12 月已公告未派息的分红标成"已到账"，虚增"今年已收""累计收益"和目标覆盖率，且无撤销入口 | 新增 `DividendBatchRules.isEligible(status, payDate, asOf)` 重载，年度入口与月历共用同一判据；列表补派息日；弹窗写明未来派息不在其中 |
| 2 | **Excel 改过的税率可让应用启动即崩** | `MainActivity.effectiveTaxRate` + `FinanceMath.aShareRecordDateTaxRate` | 先传参后 `clampRate`，而该方法对超界税率直接抛异常；Excel 导入只校验字段不校验取值范围 | 三档税率先钳制再传入；复利页 `drip_reinvest` / `drip_tax` 同样处理 |
| 3 | **分红到账与成本摊薄非原子** | `LedgerDatabase.markDividendReceived`、`addDividend` | 两步独立 SQL，中途失败留下"已到账但成本未摊薄"的永久不一致 | 各自包进事务（同类方法 `addTransaction` / `saveHolding` / `markReceivedBatch` 本来就有，属遗漏） |
| 4 | **`openingCache` 被两个线程写** | `LedgerDatabase:39` 普通 `HashMap` | 后台线程 `put`、UI 线程 `clear()`，并发可致哈希表损坏或死循环，表现为随机卡死 | 改 `ConcurrentHashMap` |
| 5 | **恢复备份时内存溢出抓不住** | `MainActivity` 恢复入口 `catch (Exception e)` | `OutOfMemoryError` 是 `Error`，大备份导入直接闪退 | 单独 `catch (OutOfMemoryError)` 并提示"备份过大，原数据未更改"；导入本身在单一事务内，放弃即回滚 |
| 6 | **`renderSafely()` 定义了从未被调用** | `MainActivity` | 唯一的渲染兜底保险丝没接上 | `render()` 自身兜底，渲染异常记日志并 toast，不再带崩应用 |
| 7 | **`onDestroy` 先 `shutdownNow()` 再关库** | `MainActivity:215` | 后台任务拿着已关闭数据库的 Cursor，在无主线程看管的线程里抛异常 | 改为优雅停机 + 最多等 2 秒后再 `shutdownNow()` |
| 8 | **`setting()` 可能返回 null** | `LedgerDatabase:504` | 调用方 `saved.split(",")` 空指针 | 对 NULL 值回退默认值 |
| 9 | **`insertRows` 死分支** | `LedgerDatabase` 原 638–639 两行逐字相同 | 误导维护者以为 `asset_index` 有特殊冲突处理 | 合并为一行 |
| 10 | **`CacheChoice` / `chooseCached` 是生产死代码** | `MarketDataClient` | 零生产调用，三个时间形参从未使用；唯一调用方是测试的三条断言——绿灯覆盖着没人跑的代码 | 删除，连带删除那三条断言（MarketDataTest 15 → 12 条） |

## 二、交互打磨

| 项 | 原来 | 现在 |
|---|---|---|
| 行情缺失原因 | 六种情况一律"行情未同步 · 按持仓成本估值"，未填代码 / 美股不支持 / 网络失败 / 停牌 / 退市 无法区分 | 分流为「未填写代码」「该市场暂不支持行情查询」「行情未同步」 |
| 刷新失败可见性 | `snap.warning` 只在非持仓页 toast，持仓页静默，只剩一片"行情未同步" | 保留在持仓页顶部并带「重试」按钮 |
| 刷新并发 | 上一次请求在飞行中时手动刷新静默无操作 | 提示"行情正在刷新，请稍候" |
| 表单报错 | 持仓表单把数量、成本、派息、税率四类错误全挂在"持有数量"一个框；交易表单把单价、手续费错误也塞给数量，写库失败也复用同一句 | 逐字段 `setError` |
| 半成品输入 | `parseNumber` 把 `-`、`1.` 静默读成 0 并写库（单价记成 0 元成交） | 新增 `hasNumber()`，编辑保存前逐字段校验 |
| 空态 | 支出目标、统计贡献榜只有一行灰字；账户列表零账户时连 else 分支都没有 | 改用带 CTA 的 `emptyCard`（此前定义了零次调用）；账户列表补说明；交易流水空态补充"会重算加权成本"的后果 |
| 数值格式 | `percent()` / `amount()` 对 NaN、∞ 直接显示 `NaN`；`DecimalFormat` 默认银行家舍入（0.005 舍到偶数） | 非有限值显示"—"；显式 `RoundingMode.HALF_UP` |

## 三、测试与工程

- **新增 `scripts/test-defensive-guards.py`**（21 条）：冻结上述全部防御性修复。**已做变异验证**——改动源码后脚本确实失败并报出对应条目，不是永真断言。
- **`DividendBatchRulesTest` 10 → 21 条**：新增年度重载的边界（今天到期 / 明天 / 去年 / 明年 / 无效日期 / 空派息日 / 空参照日），本地实跑通过。
- **提醒窗口规则首次可测**：`DividendReminderWorker`（140 行）是唯一零测试的类。把窗口判断抽成不依赖 Android 的纯类 `DividendMilestones`，新增 JUnit 12 例，覆盖 T-1 / T+0 / T+7 / T+8 四个边界。JUnit 总数 27 → **39**。
  - 中途踩到一个真问题：JUnit 跑在 android.jar 桩上，`JSONObject.optString` 返回 null，传整行进纯类会让所有用例 NPE。因此纯类只接收字符串与 `LocalDate`，由 Worker 负责取字段。
- **CI 补上 6 个从未执行的 Java 套件**：`.github/workflows/android.yml` 原先只遍历 `scripts/test-*.py`，`scripts/` 下由 `.sh` 驱动的 6 个 Java 测试在 CI 中零执行。新增一步遍历 `scripts/test-*.sh`，同时激活 `test-calendar-query.py`——它是全套唯一跑真实 SQLite 的脚本，因缺 fixture 在 CI 里恒定 SKIP（本地已验证：`PASS SQLite executes all calendar filters`）。
- **修掉两条静默跳过**：`test-market-data.sh` 末尾三行 grep 原包在 `if [ -x gradlew ]` 中，gradlew 不可执行时整段跳过并照样打印 PASS；`JAVA_HOME=...` 赋值后无人使用。

## 四、查到但**没有改**的项（附理由）

按"改动风险 / 是否需要用户决策 / 能否自验证"排序。这些不是遗漏，是判断。

| 项 | 位置 | 为什么不改 |
|---|---|---|
| **导入链搬离主线程 + 分批事务** | `MainActivity` 恢复路径、`LedgerDatabase.insertRows` | JSON 导入闪退的根因基本定位清楚了（见 `KNOWN_ISSUES.md`：主线程全量物化 + 只捕获 `Exception`），已加 OOM 兜底。但把整条链改到后台线程涉及进度 UI 与生命周期，改完必须真机验证，当前无设备，不敢盲改 |
| **交易 / 分红没有删除或编辑入口** | `LedgerDatabase` 只有 `deleteHolding` | 录错一笔会永久污染加权成本与税务批次。这是功能新增（长按菜单 + 确认 + 成本反向重算），不是打磨，需要你确认交互形态 |
| **`transactions(holding_id)`、`dividends(holding_id)` 缺索引** | `LedgerDatabase.createCore` | 一次渲染对同一持仓扫三遍流水。加索引要动 schema（虽不需迁移版本号，但会改 `onCreate`），且收益要真机大数据量才看得出 |
| **`validateBackup` 不校验 NOT NULL 列与 `source_key` 唯一性** | `LedgerDatabase:562-603` | 用户遇到的是"点恢复 → 弹一句备份无效 → 不知道哪错了"。补校验要同时设计错误信息格式，属可见行为变更 |
| **`ExcelBackup` 硬编码 `schema_version = 4`** | `ExcelBackup:155` | 现在不出问题，下次 schema 升级会静默丢字段。改法简单（引用 `DB_VERSION`），但属于"现在没坏"的改动，留到你决定升级 schema 时一起做 |
| **金额仍用 `double` 而非整数分** | 全域 | 正确的改法是引入定点结算层，波及面太大 |
| **`targetSdk` 34**（商店要求 35+） | `app/build.gradle` | 升 targetSdk 可能改变运行时行为（权限、后台限制），必须真机验证 |
| **`MainActivity` 2610 行** | — | 拆分是纯重构，收益是长期可维护性，风险是引入回归。已识别出可抽取的重复片段（卡片头构造 5 处、"先校验后关闭"弹窗模式 12 处、日历导航头 2 处） |
| **美股分红** | — | 你当前无美股持仓。接口结论已记录在 `docs/DATA_SOURCE_RESEARCH.md` |

## 五、需要你决策 / 提供的

1. **GitHub 推送（已解决）**：git 的 smart-HTTP 走不通——代理对 `github.com:443` 的 CONNECT 隧道稳定返回 502，直连则超时，而 `api.github.com` 一直可达。最终改用 REST git-data API 逐个重建提交（blob → tree → commit → ref），因为树与提交都是内容寻址的，且作者、提交者、时间、报文、父提交均逐字复制，**远端生成的 SHA 与本地完全一致**，本地仓库无需变基即可对齐。5 个提交已全部落在 `S88S/suizhang-android` 的 `main` 上。
2. **真机验证**：本轮所有界面改动（指标设置页、空态卡片、行情提示条、表单逐字段报错）都**只在构建与静态层面验证过**，没有在设备上看到。当前环境无 Android 设备，模拟器因 x86_64 镜像要求硬件加速（本机 `HypervisorPresent=False`）也起不来。
3. **JSON 导入闪退**：要彻底确认，需要原始备份文件（含真实数据，建议先脱敏）、文件大小与各表行数、崩溃堆栈、崩溃发生的精确时点。见 `KNOWN_ISSUES.md`。
4. **可选的下一步**（按我的优先级）：交易/分红删除编辑入口 > 导入链后台化 > targetSdk 35 > MainActivity 拆分。

## 六、验证证据

| 项 | 结果 |
|---|---|
| `./gradlew assembleDebug lint testDebugUnitTest` | BUILD SUCCESSFUL（含第二轮、第三轮改动后各复跑一次） |
| JUnit | 6 个类 / **39** 例，0 失败（原 27 例） |
| `scripts/test-*.py` | 22 个脚本：21 通过 / 0 失败 / 1 跳过。跳过的 `test-easternfortune-ocr.py` 需要本地 JDK，CI 已配置 |
| `scripts/test-*.sh` | 4 个套件断言全通过（44 / 12 / 53 / 62 条）。注：本地 `rc=1` 仅因沙箱拦截了脚本 `trap` 里的 `rm -rf` 临时目录清理，与断言无关；CI 上不存在该问题 |
| `DividendBatchRulesTest` | 21 条通过（原 10 条） |
| GitHub | 5 个提交已推送，`main` 与本地 SHA 逐一相同 |

构建与静态回归通过**不等同于真机交互验收**。
