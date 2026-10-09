# 穗账 Android 1.4.1-debug

## 版本与升级兼容性

- `versionCode`: **15**（从 1.4.0 的 14 递增一次）
- `versionName`: `1.4.1-debug`
- Application ID: `cn.suizhang.ledger.debug`
- 最低系统：Android 10 / API 29；目标 API 34
- 数据库仍为 schema 3：**无表结构、财务算法、公开数据源、网络权限、备份协议或导航变更**。
- 本次构建使用与 1.4.0-debug 相同的调试签名证书。应用身份相同且版本号递增，可按 Android 常规应用更新路径覆盖安装。

安装前仍建议从“更多 → 完整备份与恢复”导出 JSON，并保留原文件。升级不会清除或重写本地账本；未删除、卸载旧版或清除应用数据前，不需做数据库迁移。

## 界面变化

- **日历控件收拢**：月历/年度总览使用同一张卡片内的 Material 单选分段控件；说明按钮和账户范围提示在其下方同组显示，缩小原来松散的控件间距。说明仍可展开/收起；账户范围仍是受全局账户筛选控制的说明文字，不是另一个入口。
- **账户范围完整呈现**：不再以单行/省略号裁切；窄屏时文字可自然换行。48dp 触控目标、已选状态语义和现有状态恢复机制保留。
- **涨跌语义**：行情增加“上涨 / 下跌 / 平盘”文字。A 股配色遵循涨红跌绿，平盘为中性色；交易记录中的“买入 / 卖出”不再使用行情方向色。
- **红利突出**：预计年分红、已到账、YoC、股息率等关键分红数字采用对比度校验过的红色语义角色；深色年度汇总主卡使用独立浅红前景。登记日、除权除息日、派息日及“预计/到账”生命周期图例维持原有专属色和文字。
- **图标与组件**：复用已有 Material Components Android `MaterialButtonToggleGroup`，不新增依赖、不迁移 Compose。信息图标为本地可染色的 VectorDrawable，不加载在线字体或远程图像。上游来源和 Apache-2.0 完整文本随 app 离线保留，见 `app/src/main/assets/licenses/` 与 `design-reference-notes.md`。

## 验证结果

- `./gradlew clean assembleDebug lint testDebugUnitTest --no-daemon --stacktrace`：成功；Java、资源编译、Debug APK、lint 和 Gradle 单元测试任务完成。工程没有 app-level JUnit 测试源，回归断言由下列项目脚本执行。
- lint：**0 errors、13 warnings**。警告涉及目标 API / Gradle 版本提示、未使用的尺寸令牌及动态中文文案 `SetTextI18n`；不阻断构建。
- 通过财务计算、行情、分红预测、分红状态/持久化、备份安全、UI 静态、导航、表单、设计令牌、证券自动填充、OCR、日历控件与配色等既有和新增回归脚本。
- 设计令牌脚本通过：56 个颜色、33 个尺寸令牌核对，22 组前景/背景对比度均达到至少 4.5:1；日期状态色与触控尺寸保护检查通过。
- 对旧 1.4.0 APK 和新 APK 分别运行 `apksigner verify --print-certs`：签名证书 SHA-256 指纹一致。新 APK 元数据确认 package、versionCode、versionName、最低/目标 API。
- **没有 Android 设备、`adb` 或模拟器可用**，因此没有声称完成真机安装、屏幕像素复现、触控流程或字体放大目视验收。源码回归与编译不替代这些实机检查。

## 构建

在配置好 JDK 21、Android SDK Platform 36 的环境中：

```bash
./gradlew --no-daemon clean assembleDebug lint testDebugUnitTest
```

本仓库只包含源码和构建说明；已构建 APK、摘要校验文件与干净源码 ZIP 作为独立交付文件保存。
