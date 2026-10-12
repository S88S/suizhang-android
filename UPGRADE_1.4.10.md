# 1.4.10 复盘查漏：年度批量确认修正与防御性加固

本版来自一次全项目复盘（三路并行代码审计 + 回归复跑），不新增功能，只修缺陷与打磨交互。版本在 1.4.9 之上取 1.4.10。

## 修掉的真实缺陷

### 年度总览的批量确认会误标未来分红

年度总览列出全年 12 个月，「批量确认到账」原本只按状态过滤（`status != "received"`），**不看派息日**。于是 1 月点一次，会把 12 月已公告但未派息的分红一并标成"已到账"，虚增"今年已收""累计收益"和目标覆盖率。

月历入口本来就走 `DividendBatchRules.isEligible`，要求派息日不晚于今天；年度入口没有接这套规则——全仓库 `DividendBatchRules` 只有月历那一处调用。本版给 `DividendBatchRules` 增加不带月份的重载，年度入口与待确认笔数共用同一判据，并在确认弹窗里写明"仅列出已到派息日的待收记录，未来派息不会包含在内"，列表每行补上派息日。

数据库层 `markReceivedBatch` 未改动，仍只改 `status`、不动实收金额、不改持仓成本、单一事务、幂等。

### 被改过的 Excel 设置表可以让应用启动即崩

`effectiveTaxRate` 把 `settingDouble("tax_a_short")` **先**传给 `FinanceMath.aShareRecordDateTaxRate`（该方法对超出 0–1 的税率直接抛异常），**后**才 `clampRate`。而 Excel 备份把 `app_settings` 整表导出为"本地设置"分表，导入时只校验字段与关联编号、不校验取值范围。把 `tax_a_short` 改成 5 再导入，每次渲染都会抛异常。

本版把三档税率先钳制再传入；复利页的再投资比例与税率同样处理。

### 分红到账与成本摊薄不是原子写入

`markDividendReceived` 与 `addDividend` 都是两条独立 SQL：先更新分红，再改持仓成本。中途失败会留下"已标记到账但成本未摊薄"（或反之）的永久不一致。同类方法 `addTransaction`、`saveHolding`、`deleteHolding`、`markReceivedBatch` 都已有事务，这两处属遗漏。本版补上。

### 期初持仓缓存被两个线程同时写

`openingCache` 是普通 `HashMap`，后台线程（`dataExecutor`）在税务估算时写入，UI 线程在每次写库后 `clear()`。并发 `put` 可能损坏哈希表或死循环，表现为随机卡死而不是崩溃堆栈。改为 `ConcurrentHashMap`。

### 恢复备份时的内存溢出抓不住

`OutOfMemoryError` 是 `Error` 不是 `Exception`，恢复入口的 `catch (Exception e)` 抓不到，大备份导入直接闪退。本版单独捕获并提示"备份过大，已放弃恢复，原数据未更改"。导入本身仍在单一事务内，放弃即回滚，旧数据不受影响。详见 `KNOWN_ISSUES.md`。

### 其他

- `onDestroy` 由 `shutdownNow()` 改为先优雅停机再关库，避免后台任务拿着已关闭数据库的 Cursor 在无主线程看管的线程里抛异常。
- `setting()` 对存储的 NULL 值回退到默认值，不再让 `saved.split(",")` 空指针。
- 清掉 `insertRows` 里 `asset_index` 与其它表两个分支逐字相同的死分支。
- 删掉 `MarketDataClient.CacheChoice` 与 `chooseCached`。二者在生产代码里零调用——`quotes()`、`search()`、`dividends()` 各自内联了"回退到上次缓存"的逻辑；`chooseCached` 的三个时间形参也从未使用。它们唯一的调用方是 `scripts/MarketDataTest.java` 的三条断言，属于"绿灯覆盖着没人跑的代码"，一并移除（该套件由 15 条降至 12 条）。
- `render()` 自身兜底：渲染抛异常时记录日志并提示，不再带崩应用。此前专门写的 `renderSafely()` 定义了却从未被调用。

## 交互打磨

- **行情缺失区分原因**：六种情况原来都显示"行情未同步"，现在按原因分流——未填代码 / 该市场暂不支持行情查询 / 行情未同步。美股、基金、ETF 持仓不再显示成像是网络故障。
- **刷新失败在持仓页可见**：`snap.warning` 原来只在非持仓页 toast，持仓页静默。现在保留在页面顶部并带"重试"按钮。
- **刷新进行中给反馈**：手动刷新若上一次请求仍在飞行中，原本静默无操作，现在提示"行情正在刷新，请稍候"。
- **表单逐字段报错**：持仓与交易表单原来把成本、派息、税率、单价、手续费的错误全部挂在"持有数量"这一个框上。现在各自提示。同时新增 `hasNumber()`，避免 `-`、`1.` 这类半成品输入被 `parseNumber` 静默读成 0 后写库。
- **空态给出下一步**：支出目标、统计贡献榜的空态原来只有一行灰字，现在用带按钮的 `emptyCard`（此前定义了零次调用）；账户列表为空也补了说明。交易流水空态补充"买入/卖出会重算加权成本"的后果说明。
- **数值不再显示 NaN**：`percent()`、`amount()`、`compact()` 对非有限值显示"—"；金额格式显式改为四舍五入（原先是 `DecimalFormat` 默认的银行家舍入）。

## 测试与工程

- 新增 `scripts/test-defensive-guards.py`（21 条断言），冻结本版所有防御性修复。已做变异验证：改动源码后脚本确实失败并报出对应条目。
- `DividendBatchRules` 的到期规则新增 11 条 Java 断言（年度重载的边界：今天/明天/去年/明年/无效日期/空值），`scripts/DividendBatchRulesTest.java` 由 10 条增至 21 条，本地实跑通过。
- **提醒窗口规则可离线测试**：`DividendReminderWorker` 是唯一零测试的类（140 行）。把窗口判断抽成不依赖 Android 的纯类 `DividendMilestones`，新增 `DividendMilestonesJUnitTest`（12 个用例），覆盖 T+0 / T+7 / T+8 / T-1 四个边界。
- **CI 补上 6 个从未执行的 Java 套件**：`.github/workflows/android.yml` 原先只遍历 `scripts/test-*.py`，`scripts/` 下 6 个由 `.sh` 驱动的 Java 测试（FinanceMath / DividendForecast / DividendBatchRules / MarketData / CalendarQuery / CalendarYearSummary）在 CI 中零执行。新增一步遍历 `scripts/test-*.sh`，同时激活 `test-calendar-query.py`——它是全套唯一跑真实 SQLite 的脚本，因缺少 fixture 在 CI 里恒定 SKIP。
- 修掉 `test-market-data.sh` 里两条静默跳过：末尾三行 grep 原来包在 `if [ -x gradlew ]` 里，gradlew 不可执行时整段跳过并照样打印 PASS；`JAVA_HOME=...` 赋值后无人使用。

## 第二轮：数据一致性与外部输入硬化

第一轮改完后又做了一轮针对"数据库与不可信输入"的复查，以下均为实测确认的缺陷。

### 年度统计在年份小于 1000 时会漏数据

`incomeSummary` 用 `String.valueOf(year)` 拼年份，再交给 `substr(pay_date,1,4)` 过滤。年份一旦不是四位数（例如 999 年，`"999-01-01"` 只有三位），前缀比对永远失配，整年收入静默变 0。改为 `String.format(Locale.ROOT, "%04d", year)`。

### 两条高频外键没有索引

`transactions` 与 `dividends` 都按 `holding_id` 反复查询（持仓详情、税务估算、批量确认），却没有索引；数据量大时每条持仓走一次全表扫描。新增 `idx_transactions_holding`、`idx_dividends_holding`。

因为索引不改变 schema 语义，本版**没有**提升 `DB_VERSION`——那样会强制所有已安装用户跑一次迁移。改为在 `onOpen()` 里 `CREATE INDEX IF NOT EXISTS`（幂等），已安装的应用打开一次即补上，新建库仍由 `onCreate()` 建全。

### 资产搜索的通配符没有转义

`assetIndex` 把用户输入直接拼进 `LIKE '%...%'`，`%` 与 `_` 被当成通配符。搜 `_` 会命中任意单字符、搜 `%` 会命中全部持仓。补上 `ESCAPE '\'` 并转义这两个字符。

### 日历查询会漏掉"账户已删"的持仓

`CalendarQuery` 对 `holdings` 与 `accounts` 用 `JOIN`。持仓指向的账户若被删除或导入时缺行，整条持仓从日历里消失，且没有任何提示。改成 `LEFT JOIN`，持仓不再因账户缺行而丢失。

### 备份恢复的报错不带行号

`validateBackup` 只说"某字段非法"，Excel 里几百行时无从定位。现在报错带"第 N 条"；同时补上对 `market`、`currency`、`created_at` 三个 NOT NULL 列的校验（原先漏检，导入后由 SQLite 抛 NOT NULL 约束异常），以及 `source_key` 重复检测——重复会导致同一笔分红在导入后被记账两次。

### 关于 COALESCE 的一个自我更正

复查时怀疑 `incomeSummary` 用 `COALESCE(实际到账, 预估)` 会把"确实派发 0 元"的分红替换成预估值，虚增收入。写了 SQLite 模拟验证，结论是**不会**：`COALESCE` 只在 NULL 时替换，实到 0.0 是有效数值，原样保留。两处"修复"已回退，原逻辑不动，模拟用例改为断言正确行为并留在 `scripts/test-data-integrity.py` 里，避免以后有人重犯这个误判。

### 其他

- `ExcelBackup`：导入前校验 ZIP 文件头（`0x50 0x4B`），把非 xlsx 文件（改名的 txt、损坏的下载）挡在解析之前，提示"这不是 .xlsx 文件（缺少 ZIP 文件头）"；拒收含重复条目的压缩包；`schema_version` 改为引用 `LedgerDatabase.DB_VERSION`，不再写死常量。
- `MarketDataClient`：`quotes()` 拆成按 `QUOTE_BATCH_SIZE = 50` 分批请求，持仓多时不再一次打超长 URL；User-Agent 携带 `APP_VERSION`；`setUseCaches(false)` 关掉 `HttpURLConnection` 的响应缓存；失败时读取 `getErrorStream()`，服务端返回的原因不再被丢掉。
- `EasternFortuneHoldingParser`：名称过滤原先剔除"含数字"的 token，会把"红利ETF"这类正常持仓名误判为无关行丢掉。改为仅剔除数字开头且以 ETF/LOF/REIT 结尾的行。
- `DividendReminderWorker`：通知改用固定 `REMINDER_ID`，每日重复运行是替换而不是堆叠；窗口为空时 `cancel()` 掉上一次的通知，过期提醒不再留在通知栏。
- `query()` 的 `JSONException` 原先被完全吞掉，缺字段的行看起来跟正常行一样。现在记日志（含列名）。

## 兼容与验证

- 版本：`1.4.10-debug`，`versionCode 24`；最低 Android 10/API 29。
- 数据库 schema 仍为 4，不新增迁移、权限、数据源或后台任务。
- 保持暖麦金/炭灰主题、分红现金流重点、涨红跌绿、窄屏与大字号布局，不恢复"规划"底部 tab。
- 新增 `scripts/test-data-integrity.py`，冻结本轮修复（年份补零、索引、`LIKE` 转义、`LEFT JOIN`、备份校验、Excel 硬化、行情分批、User-Agent、错误流），并内含一段真实 SQLite 模拟验证 `COALESCE` 行为。
- 本轮回归：`scripts/test-*.py` 22 个脚本 21 通过 / 1 跳过（跳过的 `test-easternfortune-ocr.py` 需要本地 JDK，CI 已配置）；JUnit 6 个套件 39 条全通过；`scripts/test-*.sh` 4 个套件全通过；`assembleDebug` + `lint` + `testDebugUnitTest` BUILD SUCCESSFUL。
- 构建与静态回归通过**不等同于真机交互验收**；当前工作环境没有连接 Android 设备或模拟器，R8 运行时行为与本版界面改动仍未在真机上看到。
