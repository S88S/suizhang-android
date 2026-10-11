# 1.4.9 双入口批量确认与工程化加固

本版由两条并行开发线合并而成（本地线 14 个提交 + 发布线 v1.4.8），版本号在 1.4.7 与 1.4.8 之上取 1.4.9。

## 批量确认到账：两个入口

总览页与月历页各自提供一个「批量确认到账」入口，候选范围不同：

- **总览页**：统计全部尚未到账的记录，有待确认项时显示按钮并携带笔数。
- **月历页**：仅统计当前显示月份、状态仍为 `expected`、且派息日为今天或更早的记录，未来派息不会出现在批次中。

两者最终调用同一个数据库方法 `markReceivedBatch`，因此行为完全一致：只将 `status='expected'` 的记录更新为 `received`，**不写入实收金额、不覆盖已有实收值、不修改持仓成本**。对采用分红摊薄成本的持仓，如需用净实收金额影响成本，仍须在月历逐笔补录。

数据库层同时对传入 id 去重，并只更新仍为 `expected` 的行，因此重复调用、重复 id 均为幂等操作。批量更新在单一 SQLite 事务内完成。

## 指标设置页

原「汇总指标设置」由 `MaterialAlertDialog` 多选弹窗改为独立可滚动页面：顶部是实时预览卡片（复用首页同一套 `homeMetricValues` 与指标栅格渲染，保证预览与实际显示不漂移），下方按**已选指标**（带 1–6 序号、显示计算口径与当前取值、点击取消）与**可选指标**（带 + 号、点击加入）分区。可选 0–6 项，超过 6 项会提示并拒绝。指标名称统一为「成本股息率 / 市值股息率 / 累计收益」等口径明确的表述。

本轮**不含**「转发截图」。

## 界面与交互修复

- 底部导航改用 `MiuixBottomNavigatorView`，消除 Material 与 Miuix 混用。Tab 数量、文案与「总览、持仓、日历、更多」结构不变。
- 恢复「规划」页入口：1.3.8 移除导航项时 `showPlan`/`showGoals`、expense_goals 表与 DRIP 设置均仍在，功能完整但无法触达，现从「更多」页重新可达。
- 底部导航分隔线此前误用 `ds_card_stroke_width`（该令牌为 0dp），始终不渲染。新增专用 `ds_nav_divider_height`。
- 深色/浅色切换不重绘：颜色在 `onCreate` 快照为 int 字段，且 `configChanges` 遗漏 `uiMode`。已补 `uiMode` 并改用 `WindowInsetsControllerCompat`；新增 `values-night/styles.xml`，修复 `windowLightStatusBar` 硬编码 true 导致深色下系统栏图标不可见。

## 工程化

- 新增 GitHub Actions 流水线：`assembleDebug`、`lint`、`testDebugUnitTest`，并上传测试报告与 debug APK。回归脚本失败**不再被忽略**（此前该步骤带 `continue-on-error`，脚本全挂 CI 也是绿的）。
- 新增 JUnit 测试源集，5 个测试类共 27 个用例，覆盖税务、成本口径、股息率、A股登记日分段、批次完整性、截图字段归属、日历 SQL 参数化、年度汇总与分红预测。
- release 开启 R8 代码压缩与资源裁剪（1.4.8 的 63.1 MB 降至约 52.7 MB）。保留规则覆盖 Worker、WorkerFactory、SQLiteOpenHelper 与自定义 View 的三个构造器。
- SQLite 开启 WAL，渲染路径可在写事务进行时读取；`onCreate`/`onUpgrade` 加 `synchronized`，避免冷启动时交错。
- 截图导入路径经 `JAVA_HOME` 定位 JDK，缺失时给出可操作提示而非 `FileNotFoundError`。

## 兼容与验证

- 版本：`1.4.9-debug`，`versionCode 23`；最低 Android 10/API 29。
- 数据库 schema 保持 **4**，不新增迁移。
- 权限仅两项：`INTERNET` 与 `POST_NOTIFICATIONS`。后者用于 WorkManager 分红到账提醒，在 Android 13+ 运行时申请；不新增相机或相册权限。
- 新增 WorkManager 分红里程碑提醒（登记日/除息日/派息日 7 天窗口）。只读本地账本，持仓数量、成本、账户数据均不出设备。
- 保持暖麦金/炭灰主题、分红现金流重点、涨红跌绿、窄屏与大字号布局，不恢复「规划」底部 tab。
- Excel `.xlsx` 账本工作簿、旧版 JSON 导入兼容不变。
- 20 个 Python 回归脚本在 Windows 与 Ubuntu 双平台通过；`assembleDebug`、`lint`、`testDebugUnitTest` 通过。
- **构建与静态回归通过不等同于真机交互验收。** R8 混淆后的运行时行为必须在真机冒烟验证，重点覆盖分红提醒、备份恢复与截图导入。

## 本次提供的安装包

`suizhang-android-release-1.4.9.apk`（38 MB，R8 压缩后，`versionCode 23`），v2 签名验证通过。

**该包用一次性测试密钥签名，与未来正式签名的包不兼容。** Android 要求每次更新使用同一密钥，因此：

- 可与已装的 debug 版（`cn.suizhang.ledger.debug`）共存，互不影响
- 不可覆盖安装在此前用其他密钥签名的 release 版；如已装有旧 release 版，需先卸载
- 正式发布前需按 [`RELEASE_SIGNING.md`](RELEASE_SIGNING.md) 生成正式密钥并重新打包；测试密钥本身已在打包完成后删除

安装前请先在旧版中导出一份 Excel 或 JSON 账本备份。

