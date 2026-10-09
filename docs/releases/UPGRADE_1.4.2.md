# 穗账 Android 1.4.2-debug

**版本：** `1.4.2-debug`（`versionCode 16`）
**应用 ID：** `cn.suizhang.ledger.debug`
**构建：** Android SDK Platform 36、AGP 8.13.2、Gradle Wrapper 8.13
**最低系统：** Android 10 / API 29（目标 API 34）

## 本次改动

- 品牌基准色从绿色改为中国红；行情继续遵循 A 股“涨红跌绿”，分红指标使用红色强调。登记日、除权/除息日、派息日等业务状态色保持独立。
- 启动器图标同步改为红色底色，不再保留旧森林绿品牌底色。
- 日历控件组改为复用现有 Material Components for Android 1.14.0 的卡片、单选切换组和 Material 按钮；压低冗余层级，以浅红轨道、红色选中态和克制留白整理“月历/年度总览”“日历说明”“账户范围”关系。
- 切换、月份/年份导航、“回到当前”、账户范围提示、同步和手工录入等位置使用本地 VectorDrawable 图标；不加载在线图标或图标字体，不增加 Compose/新 UI 框架依赖。
- 外部截图圈选仅表示需调整的区域，不复刻为 App 中的圈线、边框或装饰图形。

月历/年度总览仍为单选；日历说明仍可展开/收起；账户范围仍由总览的全局账户选择控制，日历范围提示不成为第二个筛选器；同步、手工录入与分红详情行为不变。

## 数据与安装兼容

- `applicationId` 与上一 debug 版本相同，版本号递增到 1.4.2 / code 16。
- SQLite schema 仍为 3；未修改数据库、分红/行情计算、数据源、账本备份协议、权限或业务服务代码。关键数据库、财务计算、日历规则、分红预测与 Manifest 文件已与上一源码逐一比对，内容未变。
- 新 APK 的 debug 签名证书 SHA-256 与 1.4.1-debug APK 一致；APK 签名验证和 zipalign 检查通过，因此适用于覆盖安装当前同证书的 debug 包。它不是 Play 商店生产签名包。安装前仍建议备份账本；不要卸载旧版或清除应用数据。

## 验证记录

- `./gradlew --no-daemon clean assembleDebug lint testDebugUnitTest`：**成功**，97 个 Gradle actionable tasks；`testDebugUnitTest` 为 `NO-SOURCE`（项目没有 JUnit 测试源）。
- 项目自带全量回归脚本：**全部通过**，涵盖财务计算、行情/分红解析、日历与年度 SQL、分红预测和持久化、备份回滚、对话框、UI 静态护栏、安全与 OCR 结构化夹具。
- 设计令牌回归：56 项主题颜色、33 项尺寸、22 组文字对比度均通过；OCR 虚构结构化夹具 64 项断言通过。
- Android lint 仍报告 13 条非致命告警；构建不因这些告警失败。完整日志和逐项测试结果分别见 `delivery/build-validation-1.4.2.log`、`delivery/test-results-1.4.2.txt`。
- 当前没有连接 Android 真机或模拟器，未执行真实屏幕渲染、触摸操作、字体缩放或原位覆盖安装；源码级回归和 APK 签名校验不能代替设备目视验收。
