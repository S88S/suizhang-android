# 第三方组件与许可声明

穗账 Android 应用使用以下两个第三方 UI 组件项目。两者均为应用的直接依赖；本文件及完整许可文本也随源码包一并分发。

## HiMiuix

- 项目：HiMiuix — 仿 Miuix 的 Android XML 组件库。
- 上游：https://github.com/HChenX/HiMiuix
- 锁定源码：上游提交 `77fe220d8108109bca764e6dee0e71f0e9685b9d`。
- 集成：Gradle 子模块 `:HiMiuix`；穗账实际使用 `MiuixListView` 作为市场、账户、币种、持仓、交易方向与周期等选择组件。
- 许可：GNU Lesser General Public License, version 2.1（LGPL-2.1）。完整许可文本位于 [`third_party/HiMiuix/LICENSE`](third_party/HiMiuix/LICENSE)，APK 内位于 `assets/licenses/HiMiuix-LICENSE.txt`。
- 对应源代码：完整 HiMiuix 上游源代码随本项目保存在 [`third_party/HiMiuix/`](third_party/HiMiuix/)。也可从上游仓库按上述提交获取。保留上游版权与许可声明；本次集成没有改写 HiMiuix 库源文件。

## Material Components for Android

- 项目：Material Components for Android（Material Design 原生 View 组件）。
- Maven 坐标：`com.google.android.material:material:1.14.0`。
- 上游源码/tag：https://github.com/material-components/material-components-android/tree/1.14.0
- 集成：穗账使用 Material3 DayNight 主题、`MaterialCardView`、`MaterialButton`、`TextInputLayout`、`MaterialDatePicker`、`MaterialAlertDialogBuilder` 与 `BottomNavigationView`。
- 许可：Apache License 2.0。完整许可文本位于 [`third_party/licenses/MaterialComponentsAndroid-LICENSE.txt`](third_party/licenses/MaterialComponentsAndroid-LICENSE.txt)，APK 内位于 `assets/licenses/MaterialComponentsAndroid-LICENSE.txt`。
- 对应源代码：可从上述官方 GitHub 1.14.0 tag 获取；应用通过 Google Maven 依赖构建，不把 Material Components 项目源代码复制进本仓库。

## Google ML Kit 中文文本识别

- Maven 坐标：`com.google.mlkit:text-recognition-chinese:16.0.1`（bundled model；随 APK 打包）。
- 官方 Android 文档：https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- 官方 ML Kit 条款与隐私：https://developers.google.com/ml-kit/terms
- 该 SDK 按 Google ML Kit 服务条款提供，不在本文件中错误地归类为 Apache/MIT 开源组件。Google 文档说明图像/文本推理在设备上完成，ML Kit 不把输入图像和输出文本发送到 Google 服务器；SDK 仍可能联系 Google 服务获取维护/兼容性信息，并发送使用与性能指标。用户选择的券商截图/识别文字由穗账在设备本地读取，不上传到穗账服务器。
- 本次选 bundled model：模型随 APK 提供，以便不依赖首次运行时下载；因此安装包明显变大。用户需要在遵守 Google ML Kit 条款和所在地隐私披露要求的前提下使用。

## 分发说明

源码包中同时包含本应用源码、Gradle 构建配置、HiMiuix 对应库源码和许可文件；APK 的 `assets/licenses/` 中包含两项开源 UI 依赖的简要声明及完整许可文本。ML Kit 属于 Google SDK，应同时遵守其官方服务条款。第三方项目的版权、商标与其他上游声明仍归各自权利人所有。
