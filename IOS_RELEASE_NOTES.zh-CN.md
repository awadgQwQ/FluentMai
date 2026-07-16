# FluentMai iOS Experimental Preview 0.2.0-alpha.1

这是 FluentMai 第一份面向真实 iPhone 自签测试的实验性 iOS 包。

## 安装前必读

- 下载文件是 **unsigned IPA**，需要测试者用自己的 Apple Account 自行签名；不能直接点击安装。
- 当前没有 App Store 或 TestFlight 分发。
- 请按 [`IOS_SIDELOAD_GUIDE.zh-CN.md`](https://github.com/Daozhu1007/FluentMai/releases/download/v0.2.0-ios-alpha.1/IOS_SIDELOAD_GUIDE.zh-CN.md) 操作，不要使用来源不明的在线签名网站。
- 本包已通过 macOS GitHub Actions 的 `iphoneos`、`iosArm64`、generic iOS device Release 构建和静态验证。
- `realDeviceInstallVerified` 仍为 `false`：发布前没有把静态验证虚报成真实 iPhone 安装成功。
- 卸载可能删除 iOS 本地成绩、别名和趋势数据。

## 当前功能范围

- 随 App 离线打包的公开曲库；
- 按曲名、曲师、曲目编号和自定义本地别名搜索；
- 曲风筛选与标准/DX 谱面切换；
- 谱面定数、谱师、物量等详情；
- 手工录入并仅在本机保存成绩；
- B35/B15 与总 Rating；
- 本地 Rating Trend；
- 单曲 Rating 和判定损失计算工具。

## 当前缺失或未验证

- 没有证明 Android 数据可迁移到 iOS；
- 没有移植或承诺 Android 的完整舞萌成绩导入流程；
- 当前不接收 Wahlap Cookie、Token 或原始网页；
- 没有云同步，也没有承诺可用的数据导出/恢复流程；
- iOS 功能范围少于 Android；
- 尚未完成真实 iPhone 安装与运行验证。

## 构建与安全说明

- 配置：Release
- SDK：iphoneos（准确版本见 `ios-device-build-manifest.json`）
- Kotlin/Native target：iosArm64
- 架构：arm64；不包含 x86_64
- KMP：`FluentMaiShared.framework` 以静态方式链接进主二进制，并验证链接命令与运行时符号
- 签名：unsigned；未打包证书、Provisioning Profile、私钥或 Apple Account 信息
- `SHA256SUMS.txt` 和 manifest 均来自最终成功的 device IPA workflow 产物

现有 Android `v0.2.0-beta` release、APK、版本号和产品行为不受本次 iOS 实验发布影响。
