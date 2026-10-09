# 穗账 Android

> **文档导航**：全部说明文档按用途归类在 [`docs/`](docs/README.md)，分为版本发布记录（`docs/releases/`）、设计口径与计算边界（`docs/design/`）、数据源与许可（`docs/compliance/`）三类。

**当前源码：1.4.7-debug（versionCode 21）**。延续 1.4.6 的暖麦金/炭灰界面与可选总览指标，新增标准 Excel `.xlsx` 完整账本工作簿，按账户、持仓、分红、交易、支出目标、标的索引和本地设置分表，附编辑说明；支持导入工作簿并保留旧版 JSON 导入兼容。导入有明确的全量替换确认和事务校验，工作簿不会自动上传。包名 `cn.suizhang.ledger.debug`、最低 Android 10/API 29。**公开仓库只同步源码和文档，不包含 APK 或用户私有账本数据。**公式与税务边界见 [`FORMULA_ALIGNMENT_1.4.3.md`](docs/design/FORMULA_ALIGNMENT_1.4.3.md)，升级说明见 [`UPGRADE_1.4.7.md`](docs/releases/UPGRADE_1.4.7.md)，设计准则见 [`DESIGN.md`](docs/design/DESIGN.md)，已知问题见 [`KNOWN_ISSUES.md`](docs/compliance/KNOWN_ISSUES.md)。

## 主要功能

- **1.4.7 Excel 账本工作簿**：一键导出/导入 `.xlsx`，包含使用说明和 7 张数据表；保留各表 ID 关联，便于在 Excel/WPS 查看、筛选与编辑。导入前会提示全量替换，结构或账本关系验证失败时原数据不变；兼容读取旧版 JSON 备份。无新增权限、外部服务或数据库迁移。详见 [`UPGRADE_1.4.7.md`](docs/releases/UPGRADE_1.4.7.md)。
- **1.4.6 总览与日历精修**：总览改用炭灰主卡，年度分红金额更突出；十二项可选指标中可自定义展示 0–6 项，默认选中成本息率、市值息率、浮动盈亏、净投入、持仓只数和盈亏率，支持恢复默认。多币种不换算；市值/盈亏指标遵从最新行情可用性。日历移除说明与重复账户范围提示，修正分段选中态下沿圆角，并统一月份/年份导航与“回到今天/今年”的轻量样式。数据库 schema 与账本数据格式不变。详见 [`UPGRADE_1.4.6.md`](docs/releases/UPGRADE_1.4.6.md)。
- **1.4.5 麦金色与 Miuix 风格更新**：浅色暖麦金、深色明金主题统一应用和 HiMiuix 控件色；卡片采用 HiMiuix `MiuixCardView`，已选穗升图标 A 配合金色底；日历登/除/派缩写改为三色圆点与完整图例；公开同步按钮在窄屏/大字号下按字号增高或改为纵向排列。界面字体改为本地 Noto Sans SC Regular；OFL 1.1 许可随包附带，字体静态文件约 8.33 MB，不从网络加载。数据库 schema 与账本数据格式不变。详见 [`UPGRADE_1.4.5.md`](docs/releases/UPGRADE_1.4.5.md)。
- **1.4.4 A 股税批完整性修复**：自动税率仅在同一登记日的有效 FIFO 批次合计覆盖重放持仓量、当前日期另与账面持仓量相符时启用；历史分红卡读取时也会重核 A 股自动模式记录，避免沿用旧 tax_known；缺少/无效初始日期或交易日期时显示税务未确认。原税率区间和批次加权公式不变，数据库维持 schema 4，不新增迁移。详见 [`TAX_COVERAGE_1.4.4.md`](docs/design/TAX_COVERAGE_1.4.4.md) 与 [`UPGRADE_1.4.4.md`](docs/releases/UPGRADE_1.4.4.md)。
- **1.4.3 日历与公式对齐**：按参考图重排公告提醒、月份导航、日期图例、本月概况、更新时间与日历网格；日历/年度视图、账户范围和底部导航保持穗账原样。持仓新增市场值、浮动盈亏与盈亏率、昨收息率、成本息率、同币种占比和首买持股天数。涨红跌绿及 1.4.2 Material 控件继续保留。
- **1.4.1 日历控件与分红视觉精修**：统一月历/年度总览、说明入口与全局账户范围；说明仍可展开，两个视图仍为单选，账户范围仍由总览控制；涨跌增加文字方向并按 A 股习惯涨红跌绿，平盘用中性色；红利指标强化，登记日/除权日/派息日等生命周期色保持原样。
- **账本与成本口径**：多账户、持仓、交易、支出目标、DRIP 参数、统计及完整 Excel `.xlsx` 导出/导入保留；仍能导入旧版 JSON 备份。提供分红摊薄、摊薄成本、加权平均三种前向算法；买入手续费计入成本，卖出手续费计入剩余成本，分红摊薄只扣用户录入的实际净到账。算法切换以当前成本为新起点，不回算切换前的交易/分红。
- **DRIP 复利测算口径**：红利再投资预测按**年度一步**简化计算——每年仅结算一次"期初资产 × 预期年化股息率"，税后按再投资比例回到资产，再叠加当年定投，随后进入下一年。**不是按月或按季复利**，因此结果会低于按月复利的理论值；预期年化股息率、定投金额与综合税率均为手工输入的假设值，不代表实际收益，也不构成投资建议。参数越界（年数 1–30、再投资比例 0–100%、金额与收益率非负）会被拒绝。
- **持仓卡片**：显示市值、浮动盈亏、盈亏率、昨收息率、成本息率、同币种占比与年分红预估。无行情时市值按成本估值，但浮动盈亏不计并明确标注；不同币种不直接相加。
- **证券搜索/自动回填**：主动搜索公开证券名称/代码，结构可识别时回填名称、代码、市场、币种；结果来自可变的东方财富公开搜索接口，应由用户核对。搜索服务失败时可用本地索引或手工输入。可选择基金、ETF 类别；行情服务把基金误分为 A 股时保留用户选的基金/ETF 类别。
- **新增持仓自动预测**：选中或填完 A 股/港股代码后自动读取公开历史，默认按最近 3 个已完成年度的每股分红均值回填；可切换近 1/3/5 年。持有数量变化时即时显示“每份金额 × 数量”的预计年总额。取数失败使用缓存或给出提示，可手工录入；美股明确提示未接入自动分红。历史推算不是正式公告或已到账金额，来源与更新时间单独标明。
- **行情（A 股/港股/基金/ETF）**：用腾讯公开行情字段解析现价和当日涨跌幅，包含 GBK 解码。15 分钟本机缓存，8 秒超时；请求失败时显示旧缓存/未同步。报价用于估值参考，不保证实时。
- **公开分红（日历）**：东方财富公开报表只接入抽样验证过的 A 股和港股。按除权前股权登记日持仓与本地交易流水估登记股数；日历区分登记日、除权除息日、派息日，并标识已公告、历史推算、手工记录和已到账。同一分红方案按证券/报告期/登记日/除息日去重；供应商更正派息日会更新原事件而不是重复新增。
- **日历月历与年度总览**：月历用登记、除息、派息三色圆点标记日期，并配完整文字图例。顶部优先显示未来已公告方案提醒；月度概况区分已到账与待收，实收金额优先采用用户录入的券商到账额；未录实收的旧记录显示原登记额并提示。年度总览按月展示走势与 12 个月明细，按币种分列、不做汇率换算；点选月份可回到月历。
- **年分红口径**：数据同步提供历史行作为手工预测参考，并按所选 1/3/5 年窗口平均；不承诺未来派息。A 股 `PRETAX_BONUS_RMB` 依接口口径按每 10 股金额除以 10；没有派息日期时按除息日 +1 日显示“约”。港股报表请求不传经抽样会导致错误的排序列；报表缺失状态字段时显示未提供/需核对。
- **1.3.6 分红优先总览**：总览先显示年分红估算和按年分红排序的持仓标的，再显示近期预期与日历入口；成本收益率和累计已确认到账放在辅助指标区。新增持仓改为首页右下角加号，菜单提供手动新增和券商截图识别；持仓、日历、账户等其他页面新增入口均使用次级样式。未知金额继续显示为暂无数据，不伪装为零。总览、持仓、日历页不放显眼的税前/税后切换，统计页仍保留原切换。
- **1.3.8 紧凑主 tab**：总览独占品牌/账户选择器；其它 root 页面移除重复大标题与副标题，账户范围以非交互轻提示保留可理解性。日历登记/除息/派息与到账口径改为默认折叠的可访问帮助面板；滚动区适配系统栏、手势及 IME inset，并在 Activity 状态恢复时保留当前 tab 与筛选范围。数据算法、数据源、权限、schema、备份协议和四栏顺序不变。
- **1.3.9 东方财富持仓截图识别**：按详情页与窄列表版式读取 ML Kit 本机 OCR 元素坐标；依标签/列位置识别证券名称、完整代码、持仓数量和成本价，列表基金分区不混入股票，列表未显示代码不猜码。逐字段标识来源和待确认/冲突状态，预填可编辑，首次建仓日期缺失需手动选择；历史交易/股息不导入。图片/OCR 文字不上传穗账服务、不写日志/备份或整页预览；保留 ML Kit 服务使用指标披露。版本验收边界见 [`UPGRADE_1.4.0.md`](docs/releases/UPGRADE_1.4.0.md)。
- **1.3.7 设计系统与界面精修**：以产品专属 [`DESIGN.md`](docs/design/DESIGN.md) 记录可抽取令牌、页面/组件模式、空错载入语义与 Android 无障碍要求；关键颜色、圆角、内距、字号接入 `values` 资源并用回归脚本核对文档。首页年度金额获得更清晰层级，持仓统计重排为两列×两行，涨跌和日历事件色加入可读性检查；不改变算法、数据库、数据源、备份或导航。
- **底部导航与录屏参考**：当前保留“总览、持仓、日历、更多”四栏，标签常显且均匀排布。所录屏中的列表/详情层级、日历/年度总览切换和解释弹窗只作界面参考；本次未重构其它页面，也未把录屏当作 APK 运行验证。
- **日历与操作区 hotfix**：修正全部账户和指定账户日历查询中多拼的右括号；日历日期和空事件结果增加边界守卫。持仓动作收敛为新增/更多两项，较少使用的市场、币种、账户和税务设置折叠在持仓表单内。
- **1.3.2 对话框热修复**：输入提示由 TextInputLayout 单独持有，差异性辅助提示作为 placeholder，避免 EditText 与容器双 hint 重叠；证券/离线检索期间隐藏原持仓表单，并在选择、取消、返回、失败后恢复；表单内容改为受限自适应滚动并请求键盘弹起时窗口重排。未改数据库或财务计算逻辑。
- **截图导入**：系统照片选择器只交付用户所选图像 URI，中文 OCR 用随包的 bundled Google ML Kit 在设备上运行；字段逐项预览/纠错后还需进入持仓表单并确认保存，不会自动写买入/卖出交易。穗账不上传所选图片。按 Google ML Kit 官方条款，SDK 仍可能联系 Google 服务获取维护/兼容性信息及发送使用指标，需一并遵守其条款和适用的隐私披露要求。
- **税率估算**：全局设置由用户明示税务身份及港股/美股渠道，应用不按设备位置推断。A 股自动模式按本机初始日期及买卖交易流水 FIFO 估登记日批次持有期，区间参数 20%/10%/0% 可调整；税差实际扣缴可能在卖出时发生。港股只在用户明确选港股通时使用 20% 简化情景；普通港股账户保持待确认。美股只有用户明确确认协定资格且 W‑8BEN 已被券商接受时，才使用 10% 情景；选择不适用协定时以 30% 情景估算。基金/ETF 税务保持待确认。未知时不自动扣税，显示“税务未确认”，不表示免税。手动模式沿用每项持仓的用户税率。

## 数据源、准确性和隐私

本次对 A 股分红、港股分红、腾讯行情和证券搜索做了小范围 HTTPS 抽样，记录了实际查询 URL、响应状态、字段结构与已知限制，见随源码附带的 [`data-source-validation.md`](docs/compliance/data-source-validation.md)。测试响应成功不能证明服务稳定、准确、得到官方授权或允许商用/长期再分发；发行前应取得相应服务许可。股息事件与行情均可失效，显示来源/更新时间和缓存/估算提示。

美股分红端点在指定一个供应商字段时明确返回“字段不存在”，去掉该字段重试仍读取超时，**因此没有接入美股自动分红**；美股只能手工录入方案、使用用户明示的税务情景。汇率端点虽然抽样返回 USD 汇率 JSON，本轮没有接入汇率换算/跨币种合并，统计仍按币种分别显示。基金/ETF 主数据也没有独立权威来源。

网络请求只发送用户触发的证券搜索词或证券代码；行情/分红同步请求不包含持仓数量、成本、账户、交易流水或截图。图片/OCR 文本通过本地 ML Kit 处理。应用仅新增 INTERNET 权限；图像从 Android 系统照片选择器读取，不申请整库照片权限。ML Kit 官方文档与条款链接及本地/SDK 处理说明见 [`THIRD_PARTY_NOTICES.md`](docs/compliance/THIRD_PARTY_NOTICES.md) 和应用内“统计与设置 → 开源组件与许可”。

**估算结果不是投资建议、实际行情或税务意见。** 股息历史数据、登记日前交易回放与税务批次尤其应对照券商对账单；旧版本持仓的默认首次买入日期只是建账时间，升级后应校正真实日期，缺失/不可靠历史批次不会被称为准确回算。

## 数据库、升级和备份

SQLite schema 从 3 升至 4，持仓新增成本算法字段（默认 `weighted_average`），分红新增可空 `received_amount`。迁移不改写旧持仓成本；旧 JSON 备份依靠数据库默认值导入。恢复仍先校验，再在单一事务中替换，异常回滚。升级前建议从“更多 → 完整备份与恢复”导出 JSON。**不要先卸载旧版或清除数据。**

已报告的一份 JSON 备份导入闪退尚未复现，根因未确认，也未声称已修复；请保留原始备份，详情见 [`KNOWN_ISSUES.md`](docs/compliance/KNOWN_ISSUES.md)。

## 依赖和许可

- HiMiuix 子模块沿用其 LGPL-2.1 源码/许可。
- Material Components Android 1.14.0 使用 Apache-2.0。
- 本地 Material Icons VectorDrawable（含日历、导航、账户和操作图标）使用 Apache-2.0；源码与 APK 均随附完整许可文本。
- Google ML Kit 中文文字识别使用 `com.google.mlkit:text-recognition-chinese:16.0.1` bundled model；请遵守 Google ML Kit 服务条款。该本地模型增加 APK 体积。

来源、版本、完整开源许可证和 ML Kit 官方条款说明见 [`THIRD_PARTY_NOTICES.md`](docs/compliance/THIRD_PARTY_NOTICES.md)。

## 构建与验证

环境：JDK 21、Android SDK Platform 36、Gradle Wrapper 8.13、AGP 8.13.2。设置 Android SDK 路径后执行：

```bash
./gradlew --no-daemon clean assembleDebug lint testDebugUnitTest
./scripts/test-finance.sh
./scripts/test-finance-edge.sh
bash scripts/test-market-data.sh
bash scripts/test-dividend-forecast.sh
python3 scripts/test-dividend-flow.py
python3 scripts/test-dividend-persistence.py
python3 scripts/test-formula-alignment.py
python3 scripts/test-a-share-tax-coverage.py
python3 scripts/test-backup-safety.py
python3 scripts/test-ui-static.py
python3 scripts/test-excel-backup.py
python3 scripts/test-dividend-focus-ui.py
python3 scripts/test-overview-metrics.py
python3 scripts/test-no-plan-nav.py
python3 scripts/test-dialog-regressions.py
python3 scripts/test-design-tokens.py
python3 scripts/test-calendar-polish.py
python3 scripts/test-calendar-redesign.py
python3 scripts/test-compact-tabs-ui.py
python3 scripts/test-security-code-autofill.py
python3 scripts/test-calendar-query.py
```

关于测试的组织方式：项目**不使用 Gradle 的 JUnit 测试源**，`app/src/test` 为空，因此 `testDebugUnitTest` 任务会执行但输出 `NO-SOURCE`。实际断言由`scripts/` 下的两类回归承担——

- **纯计算回归**：`scripts/test-finance.sh`（常规口径 53 项）与 `scripts/test-finance-edge.sh`（边界与异常 120 项）用 `javac` + `java` 直接编译执行 `FinanceMath.java`，**不依赖 Android SDK**，可在任意装有 JDK 21 的环境运行。
- **源码静态与集成回归**：`scripts/test-*.py` 与 `test-market-data.sh`、`test-dividend-forecast.sh` 对源码文本、数据源契约和界面结构做断言。

**Windows 运行提示**：`test-formula-alignment.py`、`test-dividend-persistence.py`、`test-calendar-query.py` 使用 `tempfile.NamedTemporaryFile` 后直接交给 SQLite，在 Windows 上会因文件被独占而报 `unable to open database file`；`test-easternfortune-ocr.py` 依赖 PATH 中的 `javac`。这几项在 Linux/macOS 下正常，**在 Windows 上复现失败不代表代码有问题**，请勿据此判定回归。

1.4.7 回归覆盖 Excel 工作表/字段映射、文件大小限制、拒绝公式、旧 JSON 兼容、全量恢复保护，以及 1.4.6 总览/日历、财务/税务和数据源检查。APK 已用 JDK 21、Android SDK Platform 36 与 Gradle 8.13 构建；当前无连接设备或模拟器，未进行运行时点按和屏幕截图验收。

1.3.5 新增离线年度收息汇总用例：跨年/年末边界、空数据、币种分列、已到账/预计分离和年度显示范围；UI 静态回归验证月/年切换、账户范围、12 个月跳转、文字标记与估算语义。

当前工作区未连接 Android 设备，**没有进行真机/模拟器点击、用户截图渲染复现或原位迁移测试**。表单继续使用限高滚动窗体、wrap-content 输入容器和系统键盘 resize，源码检查了触控目标与动态文字规则，但不能替代小屏/大字体实机验证。安装后若发现布局问题，请提供 Android 版本、系统字体缩放与复现截图；勿为验证而随意更改系统设置。交付 APK 不是 Play 商店 release 签名包。
