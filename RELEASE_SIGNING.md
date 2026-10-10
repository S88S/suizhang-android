# Release 签名配置

release 构建默认产出 `app-release-unsigned.apk`，**未签名，无法安装到真机**。本文档说明如何配置签名。

## 现状

`app/build.gradle` 已接入签名配置，读取仓库根目录的 `keystore.properties`：

```gradle
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
}
```

**没有 `keystore.properties` 时构建照常成功**，只是产出未签名包。这是有意设计：新克隆仓库、或 CI 环境都不需要密钥才能构建。

## 步骤

### 1. 生成密钥库

在仓库根目录执行（`keytool` 随 JDK 一起提供）：

```bash
keytool -genkeypair -v \
  -keystore suizhang-release.jks \
  -alias suizhang \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12
```

交互提示中：

- **store password 与 key password**：请设成不同的密码，均不少于 6 位
- **姓名 / 组织 / 城市等**：可填真实信息或随意填写，**不影响签名有效性**，只是证书里的备注

有效期 10000 天（约 27 年）。

### 2. 创建 keystore.properties

在**仓库根目录**（不是 `app/`）新建 `keystore.properties`：

```properties
storeFile=suizhang-release.jks
storePassword=你的库密码
keyAlias=suizhang
keyPassword=你的密钥密码
```

### 3. 构建

```bash
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

### 4. 验证签名

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

看到 `Verifies` 且列出证书信息即为成功。

## 两个不可逆的坑

**密钥丢失 = 应用永久无法更新。** Android 要求每次更新都用同一个密钥签名。密钥丢了、忘了密码，Google Play 会拒绝更新，已安装的用户也无法升级。

密钥库一旦提交到 git，即使后来删除，历史记录里仍可检出，等于永久泄露。`.gitignore` 已忽略 `*.jks`、`*.keystore`、`keystore.properties`。

**因此建议：**

- 密钥库文件与密码分开保管——文件放本地或云盘，密码放密码管理器
- 至少额外备份一份到另一个位置
- 明确记住有效期，临近过期前更新（更换密钥在应用商店需要单独申请）

## 签名方案说明

实测生成的是 **v2 签名**：

```
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
```

- v1（JAR 签名）：兼容 Android 7.0 以下。本项目 `minSdk 29`（Android 10），**不需要**
- v2：Android 7.0+ 的标准方案，**已启用，日常够用**
- v3：支持密钥轮换，Android 9+ 可用。当前未启用，**若将来需要轮换密钥时再加**

## 说明

- R8 混淆已开启（`minifyEnabled true` + `shrinkResources true`），混淆后的运行时行为**必须在真机冒烟验证**，源码检查无法替代
- 首次安装 debug 版与 release 版签名不同，两者可共存（`applicationIdSuffix '.debug'` 已做区分）