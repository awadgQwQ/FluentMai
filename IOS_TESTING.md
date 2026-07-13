# FluentMai iOS MVP 测试说明

这份说明面向拿到一台普通 Mac 和一部 iPhone 的测试者。当前 iOS 是 MVP：公开曲库、筛选/搜索/本地别名、详情、本地成绩、B35/B15、Rating 趋势和计算器可用；Android 的账号导入、Room 数据库和后台任务没有移植，也不会被 iOS 读取。

## 1. 准备环境

1. 安装能够构建 iOS 17 App 的正式版 Xcode，并至少启动一次以完成组件安装。
2. 安装 JDK 17。可在终端运行 `java -version` 确认。
3. 安装 XcodeGen：`brew install xcodegen`。
4. 克隆仓库后进入根目录，运行 `chmod +x gradlew`。

不需要 Apple 发布证书、描述文件、FluentMai Token、Cookie 或登录链接。

## 2. 生成并打开工程

在仓库根目录运行：

```bash
xcodegen generate --spec iosApp/project.yml --project iosApp
open iosApp/FluentMaiIOS.xcodeproj
```

Xcode 首次构建时会通过 Gradle 生成 `FluentMaiShared.framework`。公开曲库来自仓库中的 `fixtures/lxns_song_list_fallback.json`，不需要网络请求。

## 3. 配置自己的 iPhone

1. 在 Xcode 左侧选择蓝色的 `FluentMaiIOS` 工程，再选择同名 Target。
2. 打开 `Signing & Capabilities`。
3. 保持 `Automatically manage signing` 开启，Team 选择自己的 Apple ID 团队。
4. 把 Bundle Identifier 从 `dev.fluentmai.ios` 改成一个只属于你的值，例如 `com.yourname.fluentmai`。
5. 用数据线连接 iPhone，按 Xcode 提示完成信任和开发者模式设置。
6. 在顶部设备列表选择这部 iPhone，点击 Run（三角形）。

仅测试模拟器时，可以直接选择任意可用的 iPhone 或 iPad Simulator 后 Run，不需要配置 Team。

## 4. 建议测试路径

1. 冷启动，确认曲库显示曲目数量且可离线浏览。
2. 搜索标题、曲师、曲目编号；切换标准/DX 和分类筛选。
3. 打开曲目详情，添加一个本地别名，再回到曲库用别名搜索。
4. 为当前版本和旧版本谱面各录入若干成绩，检查 B15/B35 分桶、总 Rating 和趋势图。
5. 修改同一谱面成绩，确认只更新这一条且重启 App 后仍存在。
6. 使用单曲 Rating 与判定损失计算器，旋转 iPhone；在 iPad 上检查侧栏和多列布局。

## 5. 反馈材料

请记录：设备型号、iOS 版本、Xcode 版本、复现步骤、预期结果和实际结果。界面问题请附截图或录屏；崩溃可在 Xcode 的 `Window > Devices and Simulators > Open Console` 或运行控制台中复制相关日志。

不要发送登录链接、Cookie、Token、网页缓存、完整设备备份或 Android 数据库。CI 失败时，可下载 `fluentmai-ios-level1-*` artifact，其中包含共享测试日志、Xcode 构建日志、模拟器启动日志、截图和未签名 Simulator App；未签名产物不能直接安装到真机。
