# 1.4.12 评审整改（代码质量与健壮性）

1.4.11 上线后，请第三方对代码做了三份独立评审（架构、安全工程、整体评估）。本版**不新增业务功能**，只针对其中"有道理、改动有界"的项做整改，并对"无道理、或属于大重构/超出本会话范围"的项逐条说明为何不改。schema 保持 **5**，无新迁移。

## 处置原则

- **有道理且改动有界** → 修，并补源码级回归守卫与单测。
- **大重构 / 超出本会话范围 / 与既有设计决策冲突** → 不改，在本文"有意未做"一节如实说明，不假装处理。
- 三份评审基于 **1.4.10** 写作，部分条目已被 1.4.11（交易/分红编辑删除 + 成本重放）自然消除，文中已标注。

## 已修复

### 正确性

- **R1 建仓日的时区 bug**：`onUpgrade` 与旧备份导入对 `created_at` 用 `substr(created_at,1,10)` 截断当业务日。`created_at` 是 UTC 审计时间戳，在本地时区 00:00–08:00 这段会被回退一天。改为 `date(created_at,'localtime')`。已用 Python sqlite3 验证：UTC `2025-05-01T23:30:00Z` 在本机（UTC+8）得到 `2025-05-02`，而非截断得到的 `2025-05-01`；跨年边界 `2025-12-31T21:00:00Z` → `2026-01-01` 也正确。（`LedgerDatabase.java` + `scripts/test-cost-replay.py` 守卫）
- **R2 负实收金额崩溃**：`FinanceMath.costAfterReceivedDividend` 此前对负实收（手续费/税费超过派息额的净到账为负）抛异常，而 `CostReplay` 重算会走到它，极端数据会让重算抛错。`actualNetReceipt` 为负在数学上是"成本回升"，属合法输入，现仅拒绝非有限值。配套把 MainActivity 写库点用 try/catch 包住，异常转为提示而非崩溃。（`FinanceMath.java`、`MainActivity.java` + `FinanceMathJUnitTest.java` 用例）

### 数据完整性

- **M2 导入白名单化**：`insertRows` 此前按备份 JSON 的键名直接拼 `ContentValues`，未知字段/未知表名会被静默写入或抛非描述性错误。现维护每张表的已知列名白名单，未知键名抛 `备份含未知字段`、未知表抛 `备份含未知表`。（`LedgerDatabase.java` + `scripts/test-defensive-guards.py` 守卫）
- **M3 禁止降级**：此前未覆写 `onDowngrade`，低版本 APK 误连高版本库时 Android 默认抛不透明的 `SQLiteException` 并可能丢数据。现显式拒绝并给出可读提示（`数据库已为 vN，无法降级到 vM`）。
- **M4 公开分红写入原子化**：`addPublicDividend` 的"查存在 + 写入"原本非原子，并发下两个调用都看到"不存在"后会各自插入，唯一索引 `source_key` 会把其中一个静默丢弃（返回 -1）。现包进事务并用 `insertWithOnConflict(...CONFLICT_IGNORE)`，存在则更新、不存在才插入。（`LedgerDatabase.java` + 守卫）
- **M5 导出一致性快照**：`exportJson` 原本 7 条 `SELECT` 不在事务内，导出中途有写入时可能得到跨表不一致的快照。现用读事务包住全部查询。（`LedgerDatabase.java` + 守卫）
- **M9 手工分红登记数量**：手工分红此前从不写 `record_quantity`，展示层恒显"登记股数待校正"。现 `addDividend`/`updateDividend` 写入录入时的持仓数量快照；展示层对无快照的旧手工记录改为"按录入时持仓估算"（其总额本就由当时持仓数量推算，比"待校正"更准确）。（`LedgerDatabase.java`、`MainActivity.java` + 守卫）

### 数值稳健性

- **M10 消除重复 + 有限性校验**：`weightedBuyCost` 原本与 `costAfterBuy` 各写一套加权均价公式（漂移风险），改为直接委托 `costAfterBuy(COST_WEIGHTED_AVERAGE, ...)`。`sellQuantity` 此前对 `NaN` 输入会静默透传成 `NaN`，现加 `isFinite` 校验，非法输入抛异常。（`FinanceMath.java` + `FinanceMathJUnitTest.java` 用例）
- **M12 分红预测年份识别**：`DividendForecast.extractYear` 的正则 `(?:19|20)\d{2}` 在 **2100 年及以后**匹配不到年份，会把 2100+ 的报告年当成"无年份"丢弃。改为 4 位数字 + 范围校验（1900–今年+1），并拒收夹带的 4 位数字（如 `AB12CD34`）。（`DividendForecast.java` + `DividendForecastJUnitTest.java` 用例）

### 隐私 / 构建

- **🟡-2 通知锁屏隐藏**：分红提醒通知含持仓名称，此前未设可见性，锁屏可能暴露。现加 `VISIBILITY_SECRET`。（`DividendReminderWorker.java` + 守卫）
- **🟡-10 删除无效 ProGuard 规则**：`-keepclassmembers class org.json.**` 是无效规则——`org.json` 来自 `android.jar`，R8 不会裁剪它；原注释"字段名是磁盘备份格式的反射依赖"推理也不成立（备份格式依赖的是代码里的 JSON 键字符串字面量）。已删除并更正注释。（`app/proguard-rules.pro` + `scripts/test-r8-rules.py` 守卫）
- **🟡-15 CI 跑 release 构建**：GitHub Actions 此前只跑 `assembleDebug` + `lint` + 单测，release/R8 路径从未在 CI 验证。现加 `assembleRelease`，使混淆期才暴露的问题（重命名类、漏 keep 规则）在 CI 而非真机失败。
- **🟡-16 CI schema 检查改为约定而非锁死**：原检查 `grep DB_VERSION=4` + `! grep 'oldVersion < 5'`，schema 升到 5 后反而会红，等于把版本"永远锁在 4"。现改为：断言 `DB_VERSION` 存在，且 `onUpgrade` 含有对应 `oldVersion < N` 分支——**每次升 schema 必须在代码里配迁移**，而不是冻结数字。（`.github/workflows/android.yml`）

## 有意未做（及理由）

- **P0-1 联网开关**：评估摘要把"无联网开关"列为 P0。但本应用定位是**本地优先、数据不出设备**，且用户此前明确选择"发代码"（不上架、不发网络），仓库 `README.md` 已诚实声明此设计。加一个"联网总开关"与产品定位相悖，故不改。
- **P0-2 / P0-3 自动备份 + 备份加密**：评估把"无自动备份""备份明文"列为 P0。自动备份涉及后台调度、存储权限、对用户设备的副作用，是**结构性改动**而非小步修补；备份加密同样需要密钥管理方案（否则密钥与密文同处一设备并无增益）。二者均不在本会话"小步整改"范围内，列为后续规划，不假装处理。
- **R3 缓存竞态**：评估指 `clearOpeningCache` 与并发读存在竞态。本版已通过 `synchronized` 的 `onUpgrade`/`onCreate` 与事务削弱了风险，但彻底消除需要重审缓存生命周期，属于需要真机压测的中等改动，本版未做（M9 之外的点亦未一并处理，避免范围蔓延）。
- **R4 OCR 回调守卫**：评估指 `showBrokerOcrReview` 的 OCR 回调在 `Activity` 已 `finish` 时仍可能执行。确属遗漏，但本版聚焦"有边界、低风险"的清单；OCR 回调守卫单独列为后续，本次未并入，以免改动未经验证的路径。
- **R5 / M6 / M7 / M8 上帝类、并行数组、中文字符串落库等结构性重构**：评估建议拆分 `LedgerDatabase`、消除"并行数组"风格、把界面中文文案与落库字符串解耦。这些是**大型重构**，会触碰大量已验证路径，风险与收益不匹配于一次整改。明确不做，待专项重构。
- **M1 改用 BigDecimal**：评估建议金额计算用 `BigDecimal` 替代 `double`。本应用金额量级小、税率/单价精度需求不高，`double` 在现有 41 例重放对照与单测下表现稳定；切换为 `BigDecimal` 是跨层改动且会牵动序列化与 UI。明确不做。
- **国际化、应用锁、加密备份、CSV/QIF/OFX 导出、XIRR、图表**：均为新功能或独立大项，不在"评审整改"语义内，本文不处理。

## 测试与验证

- 新增/调整的单元测试：`FinanceMathJUnitTest`（负实收抬高成本、卖出数量有限性）、`DividendForecastJUnitTest`（年份范围校验）。
- 新增源码级回归守卫：`scripts/test-cost-replay.py`（R1）、`scripts/test-defensive-guards.py`（M2/M3/M4/M5/M9/🟡-2）、`scripts/test-r8-rules.py`（🟡-10）。
- 构建验证：`assembleDebug` + `assembleRelease`（验证 R8 可跑通）+ `lint` + `testDebugUnitTest` 全绿。
- 真机交互验证仍缺失（无连接设备/可用模拟器），与 1.4.9–1.4.11 同，详见 `KNOWN_ISSUES.md`。

## 兼容与版本

- 版本：`1.4.12`，`versionCode 26`；最低 Android 10/API 29。
- 数据库 schema 保持 **5**，无迁移；旧版（schema < 5）导入时 R1 的 `date(created_at,'localtime')` 同样适用。
