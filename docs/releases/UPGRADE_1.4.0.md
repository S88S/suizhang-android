# 穗账 1.4.0-debug 开发版本说明

## 版本信息

- 显示版本：`1.4.0-debug`（`versionCode 14`）
- Android 包名：`cn.suizhang.ledger.debug`
- 最低版本：Android 10 / API 29
- SQLite schema：3

本公开仓库提供源码、构建配置、必要的第三方组件源码及许可文件；不分发 APK、用户备份、真实截图或本地调试日志。构建产物应在本机生成，不要把账本数据或私人签名材料加入版本控制。

## 从源码构建

需要 JDK 21、Android SDK Platform 36 和 Gradle Wrapper 8.13。配置好本地 Android SDK 后，在仓库根目录运行：

```bash
./gradlew --no-daemon clean assembleDebug lint testDebugUnitTest
```

Debug APK 输出到 `app/build/outputs/apk/debug/`。仓库不包含原开发环境的 debug 签名密钥，因此新构建的签名可能与既有安装包不同；安装前请先备份账本，不要为覆盖安装而卸载含有本地数据的应用。

## 1.4.0 主要变化

- 输入完整证券代码后复用公开搜索；精确匹配时回填证券名称、市场/类别与币种，并在可用时读取公开行情。
- ETF/基金代码不受默认 A 股类别过滤；搜索失败或离线时仍可手工录入。
- 数量和每份成本继续要求用户手工填写，不由证券代码或行情推算。
- 改进东方财富持仓详情与列表的 OCR 字段分组；所有候选值仍需用户核对，不导入历史交易或到账记录。
- SQLite schema、JSON 备份协议、权限、导航和本地账本结构保持不变。

## 验收边界

仓库中的结构化夹具与源码级回归不等同于 APK 内 ML Kit 真机验收。本版本未进行真机/模拟器安装迁移、触屏布局或屏幕视觉验收。JSON 导入问题的状态见 [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md)。
