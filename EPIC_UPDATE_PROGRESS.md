# FluentMai Epic Update Progress

更新时间：2026-07-14（Asia/Shanghai）

## 当前阶段

- 阶段一：现状审计与性能基线已完成。
- 任务开始前的工作树与审计记录均已保存为独立 checkpoint。
- P0 版本语义、B15 正确性、曲库缓存保护、隐私边界、谱面性能与页面状态均已实现并通过 JVM、构建与真机覆盖安装验证。
- Android P1 玩家记录、数据驱动牌子进度、社区别名搜索、稳定谱面身份、完整筛选、统一详情页、工具箱、Rating Trend 与可解释推分推荐均已完成并通过 JVM、构建与真机验证；下一步进入横屏、平板与大屏适配。

## 任务约束

- Android 是绝对主交付，iOS MVP 是次级交付。
- 不 push。
- 不卸载设备上的 FluentMai。
- 不清除应用数据、数据库、登录状态或成绩。
- 不主动重写 Android 导入流程。
- 不修改只读参考仓库 `D:\Code\MaiproberPlus`。
- 参考截图只表达需求和信息架构，不复制第三方应用视觉设计。

## Git 基线

- 仓库：`D:\Code\FluentMai-Android`
- 分支：`master`
- upstream：`origin/master`
- starting commit：`da35826f003bb6347350eca07b91fed3cf0e76ed`
- starting commit subject：`docs: refresh bilingual project presentation`
- starting commit time：`2026-07-05T03:10:19+08:00`
- 初始 staged 修改：无
- 初始 tracked 修改：45 个文件
- 初始 untracked 路径：3 个
- `AGENTS.md`：仓库及其上级目录均未发现
- push：未执行

任务开始时的原始 `git status --short --branch`：

```text
## master...origin/master
 M app/build.gradle.kts
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/dev/fluentmai/android/vpn/core/Constant.java
 M app/src/main/java/dev/fluentmai/android/vpn/core/DnsProxy.java
 M app/src/main/java/dev/fluentmai/android/vpn/core/HttpHostHeaderParser.java
 M app/src/main/java/dev/fluentmai/android/vpn/core/LocalVpnService.java
 M app/src/main/java/dev/fluentmai/android/vpn/core/NatSession.java
 M app/src/main/java/dev/fluentmai/android/vpn/core/NatSessionManager.java
 M app/src/main/java/dev/fluentmai/android/vpn/core/ProxyConfig.java
 M app/src/main/java/dev/fluentmai/android/vpn/core/TcpProxyServer.java
 M app/src/main/java/dev/fluentmai/android/vpn/core/TunnelFactory.java
 M app/src/main/java/dev/fluentmai/android/vpn/dns/DnsFlags.java
 M app/src/main/java/dev/fluentmai/android/vpn/dns/DnsHeader.java
 M app/src/main/java/dev/fluentmai/android/vpn/dns/DnsPacket.java
 M app/src/main/java/dev/fluentmai/android/vpn/dns/Question.java
 M app/src/main/java/dev/fluentmai/android/vpn/dns/Resource.java
 M app/src/main/java/dev/fluentmai/android/vpn/dns/ResourcePointer.java
 M app/src/main/java/dev/fluentmai/android/vpn/tcpip/CommonMethods.java
 M app/src/main/java/dev/fluentmai/android/vpn/tcpip/IPHeader.java
 M app/src/main/java/dev/fluentmai/android/vpn/tcpip/TCPHeader.java
 M app/src/main/java/dev/fluentmai/android/vpn/tcpip/UDPHeader.java
 M app/src/main/java/dev/fluentmai/android/vpn/tunnel/Config.java
 M app/src/main/java/dev/fluentmai/android/vpn/tunnel/HttpCapturerTunnel.java
 M app/src/main/java/dev/fluentmai/android/vpn/tunnel/RawTunnel.java
 M app/src/main/java/dev/fluentmai/android/vpn/tunnel/Tunnel.java
 M app/src/main/java/dev/fluentmai/android/vpn/tunnel/httpconnect/HttpConnectConfig.java
 M app/src/main/java/dev/fluentmai/android/vpn/tunnel/httpconnect/HttpConnectTunnel.java
 M app/src/main/kotlin/dev/fluentmai/android/MainActivity.kt
 M app/src/main/kotlin/dev/fluentmai/android/WahlapWechatAuthUrlClient.kt
 M app/src/main/res/values/arrays.xml
 M app/src/main/res/values/strings.xml
 M app/src/test/java/dev/fluentmai/android/vpn/tunnel/HttpCapturerTunnelTest.java
 M core/database/src/main/kotlin/dev/fluentmai/android/core/database/FluentMaiDatabase.kt
 M core/database/src/main/kotlin/dev/fluentmai/android/core/database/FluentMaiRepository.kt
 M core/database/src/main/kotlin/dev/fluentmai/android/core/database/RoomImportPersistence.kt
 M core/database/src/test/kotlin/dev/fluentmai/android/core/database/RoomImportPersistenceTest.kt
 M core/importer/src/main/kotlin/dev/fluentmai/android/core/importer/ImportPersistence.kt
 M core/importer/src/test/kotlin/dev/fluentmai/android/core/importer/RealWahlapImportAdapterTest.kt
 M core/importer/src/test/kotlin/dev/fluentmai/android/core/importer/WahlapFixtureParserTest.kt
 M feature/home/src/main/kotlin/dev/fluentmai/android/feature/home/HomeScreen.kt
 M feature/import/build.gradle.kts
 M feature/import/src/main/kotlin/dev/fluentmai/android/feature/importflow/ImportScreen.kt
 M feature/quarantine/src/main/kotlin/dev/fluentmai/android/feature/quarantine/QuarantineScreen.kt
 M feature/settings/build.gradle.kts
 M feature/settings/src/main/kotlin/dev/fluentmai/android/feature/settings/SettingsScreen.kt
?? app/src/main/kotlin/dev/fluentmai/android/WahlapCookieStore.kt
?? core/importer/src/test/kotlin/dev/fluentmai/android/core/importer/MaimaiSongCatalogTest.kt
?? feature/import/src/test/
```

差异规模：

- 原始 diff：3,433 insertions / 2,648 deletions，45 个 tracked 文件。
- 忽略行尾差异后：1,156 insertions / 371 deletions，25 个 tracked 文件仍有实质改动。
- 大量 VPN Java 文件主要是 CRLF/LF 变化；`core.autocrlf=true`，仓库 `.gitattributes` 对 Java 仅使用 `text=auto`。
- 实质改动覆盖 `MainActivity`、Room、导入、设置、Hook/VPN、测试和资源；这些区域与本次 P0/P1 会直接重叠。

## 工具链基线

- Gradle wrapper：8.9
- Android Gradle Plugin：8.7.3
- Kotlin plugins：2.0.21
- KSP：2.0.21-1.0.27
- Java launcher / daemon：Oracle JDK 22.0.2
- 编译目标：Java 17 / Kotlin JVM target 17
- Compose BOM：2024.10.00
- Room：2.6.1
- Coroutines：1.9.0
- Ktor：3.0.1
- Coil：2.7.0
- compileSdk：34
- targetSdk：34
- minSdk：26
- applicationId / namespace：`dev.fluentmai.android`
- app version：`0.1.0`（versionCode 1）
- Room 数据库名：`fluentmai-phase0.db`
- Room schema version：5

## 当前架构审计

- `app`：单 Activity、底部导航、平台网络、Hook/VPN、曲库缓存和上传编排。
- `core:model`：纯 JVM 模型，可作为后续共享领域层候选。
- `core:importer`：纯 JVM，但依赖 `org.json`、Jsoup 和部分 JVM API；若迁移 KMP 需要逐步替换或包裹。
- `core:database`：Android Room，本次应保留为 Android source of truth，不进行激进替换。
- `feature:scores`：B50、谱面查询、筛选、Rating 和曲绘均集中在一个约 1,300 行文件中。
- `MainActivity.kt`：约 992 行，持有导航、数据库状态、导入、上传和曲库刷新状态，存在 God Activity/状态所有权过重风险。
- 当前没有 `iosApp`、KMP shared module、Compose Multiplatform 模块或 macOS CI。
- 最低风险跨平台方向：先抽取版本归一化、B35/B15、搜索、Rating、牌子和推荐等纯领域逻辑；Room、Android 导入、Activity/Intent/VPN 保持 Android 专属。

## 曲库、B15 与真实根因证据

数据链路：

```text
LXNS song list API
→ SongCatalogStore 文件缓存 / bundled fallback
→ MaimaiSongCatalog 解析 songVersion 与 chartVersion
→ MainActivity 将全部 ChartRecord 保存在内存
→ ScoresScreen.enrichScores 按 title + SD/DX + levelIndex 关联成绩
→ buildBestSet 以 charts.maxOf(chartVersion) 作为 current version
→ 仅 chartVersion 完全等于该最大值的成绩进入 B15
```

已证明的根因：

- 当前代码把 `chartVersion` 的最大精确值当作“中国大陆当前运营大版本”。
- 设备缓存中最大的 `chartVersion` 是 `25501`；它没有版本名称，表现为内容批次/未来批次，而不是版本表中的大版本基准。
- `25501` 有未来谱面，但设备成绩中没有任何一条匹配该精确版本。
- 因而现有算法得到：B35 35 张 / 10435，B15 0 张 / 0，总 Rating 10435；与真机首页一致。
- 版本表中的 `25500` 名称为“舞萌DX 2026”，设备有 27 条匹配成绩；若仅用它做当前候选验证，则 B15 可取 15 张 / 4225，总 Rating 14655。
- 以上 `25500` 结果只作为根因证明和候选验证，不能把“最高有成绩版本”硬编码为最终当前版本定义。最终实现需要显式区分大版本、内容批次、国服运营状态和未来谱面，并补齐回归测试。

2026-07-14 公开来源复核：

- 舞萌DX 官方 Bilibili 账号（账号说明为“舞萌DX街机音游B站官方账号”）在 2026-05-26 发布“舞萌DX 2026版本升级预告”；
- 该官方账号在 2026-06-24 公告 2026-06-29 后机台版本由 `Ver.CN1.55-A` 变更为 `Ver.CN1.56-A`；截至本次检索没有发现更晚的官方机台版本变更公告；
- LXNS 当前 API 文档把舞萌曲目、Trend 和收藏品等接口的默认 `version` 明确列为 `25500`，并把 `Version.version` 定义为“主要版本 ID”；
- 现场请求公开 song list API 得到 20 个主要版本，最大已命名版本为 `25500 / 舞萌DX 2026`；`25501` 不在主要版本表中；
- 同一现场快照中，`25500` 有 26 首曲目、92 张谱面，`25501` 有 11 首曲目、38 张谱面。`locked` 字段按官方文档仅表示“是否需要解锁”，不能用作上线状态。

来源：

- `https://space.bilibili.com/481648327/`
- `https://www.bilibili.com/opus/1206651919178661888`
- `https://www.bilibili.com/opus/1217391302331596800`
- `https://maimai.lxns.net/docs/api/maimai`
- `https://maimai.lxns.net/api/v0/maimai/song/list?notes=true`

结论：截至 2026-07-14，中国大陆当前运营大版本可以有来源地确定为“舞萌DX 2026”，对应 LXNS 主要版本 ID `25500`；`25501` 不能被解释为一个新的运营大版本。它所代表内容的具体上线状态仍不能从当前 API 字段可靠推出，正式实现必须保留 `unknown/upcoming/available` 的显式状态和可更新来源，而不是把 `locked`、最大值或玩家是否有成绩当作上线证据。

另外发现：

- `ChartVersionFilter.Current` 也直接使用 `charts.maxOf(songVersion)`，同样混淆快照/内容批次和运营大版本。
- `SongCatalogStore.refreshFromNetwork()` 在解析后直接覆盖有效缓存；当前解析器对缺少 `songs` 的 JSON 返回空 catalog 而不抛错，存在空/不完整远程数据覆盖有效缓存的风险。

## 谱面性能与页面状态根因

- 曲库在 `MainActivity` 中一次性解析并持有 5,360 个 `ChartRecord`。
- 谱面页每次输入或筛选变化都在 Composable 侧同步遍历、标准化、排序全部谱面，再 `take(500)`。
- 搜索没有 debounce。
- `JacketArt` 在每个卡片实例中各自 `remember` 一个新的 Coil `ImageLoader` 和 OkHttpClient，不能有效共享内存缓存和连接池。
- 每次曲绘成功/失败及 OkHttp BASIC 请求都会写日志。
- 搜索、等级、难度、类别、版本、游玩状态、排序和 LazyGrid 状态只存于局部 `remember`。
- `MainActivity` 的 `when (selectedTab)` 会移除当前页面 composition；返回谱面页后局部状态被重建。
- 没有 Screen ViewModel、`SavedStateHandle`、持久化筛选状态或显式列表状态。

## 测试与构建基线

已执行：

1. `\.\gradlew.bat test --console=plain`
   - exit code：0
2. `\.\gradlew.bat :app:assembleDebug --console=plain`
   - exit code：0
   - 编译提示：`LocalVpnService.java` 使用或覆盖已过时 API。

Gradle XML 汇总：

- test suites：25（Android library 的 debug/release 变体会重复运行同一测试类）
- tests：134
- failures：0
- errors：0
- skipped：0

Debug APK：

- 路径：`D:\Code\FluentMai-Android\app\build\outputs\apk\debug\app-debug.apk`
- 大小：28,437,980 bytes
- SHA-256：`CC70E1F62FB5212E5F90C2E7AFD2666FBD09A08B50BE55A9F36211D26CDE9954`
- 当前构建任务判定产物为 up-to-date。

在凭证/隐私扫描和公开版本复核完成后，于 2026-07-14 再次顺序执行同样的 `test` 与 `:app:assembleDebug` 命令：两者均 `BUILD SUCCESSFUL`；XML 仍为 25 suites / 134 tests / 0 failures / 0 errors / 0 skipped，APK 大小与 SHA-256 未变化。没有执行 `clean`、安装、卸载或清除数据。

尚未执行：

- `connectedDebugAndroidTest`：仓库没有 `androidTest` 源集测试；基线阶段未为此覆盖安装应用。
- iOS Simulator / macOS CI：当前没有 iOS 工程或 workflow。

## ADB 与设备基线

- ADB：1.0.41 / platform-tools 37.0.0
- 设备数：1，已授权
- serial：`2923ae26`
- manufacturer：Xiaomi
- model：23116PN5BC
- Android：16
- physical size：1440×3200
- override size：1080×2400
- physical density：560
- override density：420
- 已安装包：`dev.fluentmai.android`
- 已安装版本：0.1.0 / versionCode 1
- 首次安装：2026-06-29 02:36:58
- 最近更新：2026-07-03 03:52:33
- 包可调试：是，`run-as` 可用
- 本地 Debug APK 与已安装 APK 的 signer SHA-256 均为：`504c3093d1a6d00f1c0eacdbc99b7dc064e95ef72dbc38918261375257917460`
- 签名冲突：无
- 覆盖安装：签名层面可行，但基线阶段未执行安装
- uninstall：未执行
- clear data / clear database：未执行

## 设备数据完整性基线

- SQLite `integrity_check`：`ok`
- `PRAGMA user_version`：5
- 数据库主文件：6,135,808 bytes
- WAL：0 bytes
- score records：1,619
- quarantine records：9,983
- import batches：29
- cached Wahlap score pages：5
- score semantic SHA-256：`e80e3d1f91117af799b4eb63e2b9203336bc9919243bac36aef19e44a0cc1991`
- quarantine semantic SHA-256：`cf8d21c506aabede62b6fed1a10a2e888bdf39dd8acf7de5997b2a23924e0799`
- import batch semantic SHA-256：`b13da25b612c298edc0a6393608890f99ced6e2275d1847c63dbfff1d36c76cc`
- 临时数据库副本已删除；未输出真实曲名、成绩明细、HTML 或认证内容。

## 真机性能基线

冷启动与数据就绪：

- `am start -W` LaunchState：COLD
- Activity TotalTime：566ms
- Activity WaitTime：571ms
- Room 成绩/隔离状态加载：489ms
- 本地曲库：1,302 首 / 5,360 张谱面，455ms
- Rating 数据 ready：1,762ms
- 启动阶段 frames：53
- janky frames：4（7.55%）
- 50/90/95/99 percentile：8 / 200 / 250 / 400ms

谱面页切换：

- frames：52
- janky frames：1（1.92%）
- 50/90/95/99 percentile：7 / 65 / 85 / 1050ms
- 存在一帧约 1,050ms 的长帧。

谱面页连续 10 次滑动：

- frames：514
- janky frames：5（0.97%）
- 50/90/95/99 percentile：8 / 65 / 85 / 150ms
- missed vsync：3
- slow UI thread：3

搜索 `PANDORA`：

- ADB 输入命令：860ms（包含输入注入，不等同于纯查询耗时）
- 结果：5
- frames：58
- janky frames：12（20.69%）
- 50/90/95/99 percentile：31 / 113 / 117 / 150ms
- slow UI thread：6
- slow issue draw commands：7

状态恢复复现：

```text
谱面页搜索 PANDORA（结果 5）
→ 切到首页
→ 返回谱面页
→ 搜索框清空，结果恢复为 500
```

基线截图：

- `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\baseline_home.png`
- `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\baseline_charts.png`

## 只读参考仓库

- `D:\Code\MaiproberPlus`
- 分支：`lab/current-wahlap-sync`
- commit：`bf4a6e179758ffa9cde89ec099ac9afd90dd07fa`
- worktree：干净
- 本阶段未修改该仓库。

## UI/UX 基线决定

- 参考截图只提取能力、信息层级和交互需求。
- 不复制 EasyMai / RankHub 的具体深色紫色视觉、弹窗布局或卡片样式。
- 保留 FluentMai 当前青绿/琥珀色彩语言，并优先满足对比度、44–48dp 触控目标、稳定焦点、响应式布局与统一图标体系。
- 第三方截图中的 Emoji 工具图标不沿用，使用一致的 Material/SVG 图标。

## 凭证与隐私审计

对任务开始前的实质 diff 新增行和 3 个既有 untracked 文件执行了不输出匹配内容的凭证扫描：

- 扫描实质新增行：`1156`；
- 扫描既有 untracked 文件：`3`；
- 私钥、AWS/GitHub/GitLab/Slack/Google key、JWT、带凭证 URL、明显字面量凭证赋值命中：`0`；
- `.env`、keystore、PEM、私钥或 `google-services.json` 等敏感路径命中：`0`；
- 未读取或输出设备中的 Token、Cookie、HTML 内容。

但是，既有未提交实现与仓库隐私契约存在直接冲突：

- `MainActivity.kt` 把水鱼/LXNS Token 写入普通 `SharedPreferences`；
- `MainActivity.kt` 把 Wahlap 调试页和补充页原始 HTML 写入内部文件；
- Room v5 新增 `wahlap_score_pages.html`，repository 与测试明确保存并恢复原始 HTML；
- `README.md`、`README.zh-CN.md`、`docs/PRIVACY_MODEL.md` 和 `docs/IMPORT_PIPELINE.md` 明确规定 Token 与原始 HTML 不得持久化。

设备只读元数据验证进一步确认当前安装版本已经执行过这些路径：

- `shared_prefs/fluentmai_tokens.xml` 存在，大小 `341` bytes；未读取内容；
- 内部文件目录存在 `7` 个 `wahlap-*.html` 文件；只读取文件名与数量；
- Room 中存在 `5` 个 `wahlap_score_pages` 行；此前只统计行数并计算不含内容的语义基线，未输出 HTML。

这些设备数据不会在未获得明确授权时删除或迁移。后续实现需要先停止新增明文持久化并保持导入/上传流程可用；既有私密缓存的清理属于破坏性数据操作，按 Goal 的高风险规则另行确认。

## 已解决阻塞

任务开始前已有的重要未提交代码会与本次工作直接重叠，曾按 Goal 的高风险规则暂停。

- 2026-07-14 用户明确授权方案 1，并要求后续工作无需再次等待确认；
- 45 个 tracked 修改和 3 个 untracked 文件已精确保存为独立 checkpoint；
- `EPIC_UPDATE_PROGRESS.md` 未包含在该 pre-epic commit 中；
- 后续仍遵守不 push、不卸载、不清除应用数据、不破坏成绩与导入流程的原始约束。

既有私密缓存不会被删除；后续 checkpoint 只停止新增 Token/HTML 持久化，并补齐保护性测试、保持导入与上传行为不退化。

## P0：版本语义、B15 与曲库缓存保护

实现：

- 在 `core:model` 新增明确的主要版本模型和解析规则：曲库主要版本表优先，只有带名称的曲目/谱面元数据可作为降级来源，禁止再以原始最大版本号推断当前运营大版本。
- B35/B15 分桶、排序和 DX Rating 计算已迁入纯领域层。低于当前主要版本进入 B35，精确等于当前主要版本进入 B15；高于当前版本、缺失版本或当前版本不可解析的记录均不进入两个榜单。
- 首页与谱面“当前版本”筛选共同使用同一解析结果，未来内容批次不会再把 B15 置空。
- 曲库网络刷新在覆盖有效缓存前校验曲目、谱面、主要版本表完整性，拒绝低于现有规模 80% 的明显截断响应和版本回退，并通过同目录临时文件原子替换缓存。
- 新增版本优先级、命名元数据降级、未来批次隔离、35/15 上限、空/不完整/部分响应保护与版本回退测试。

验证：

- `\.\gradlew.bat test :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`。
- Gradle XML：28 suites / 145 tests / 0 failures / 0 errors / 0 skipped。
- `git diff --check`：通过；仅保留原有 `LocalVpnService` 过时 API 编译提示。
- Debug APK：28,437,980 bytes；SHA-256 `F15709B2D303E7AA49C5C83765F3953CD4A7A81385E712281E585FE3EEFEE197`。
- 对 serial `2923ae26` 执行 `adb install -r` 覆盖安装成功；没有卸载或清除数据，首次安装时间仍为 `2026-06-29 02:36:58`。
- 真机冷启动成功，`TotalTime=864ms`；日志无 fatal exception，Rating ready 为 1,265ms，本地 1,619 条成绩全部匹配。
- 首页 Rating 从基线 `10435` 恢复为 `14655`；B35 为 35 张，当前版本 B15 为 15 张。界面证据：`C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p0_b15_fixed.png`。
- 覆盖安装并启动前后，在强制停止应用后以相同的主键顺序与 JSON-lines 规范化方法计算语义指纹；四组数据数量和 SHA-256 均逐字节一致：
  - `score_records`：1,619 / `22cc15e4fbcb25b12c4c3af7de962795cce41ce6a7f49e8d32c29ad8e484ea21`
  - `quarantine_records`：9,983 / `cf8d21c506aabede62b6fed1a10a2e888bdf39dd8acf7de5997b2a23924e0799`
  - `import_batches`：29 / `b13da25b612c298edc0a6393608890f99ced6e2275d1847c63dbfff1d36c76cc`
  - `wahlap_score_pages`：5 / `4c7a35fe6d9060b61f71d13f45922a657445e1fb78a721b4fa6fa4564ab8919c`
- SQLite `integrity_check=ok`、`user_version=5`；验证未输出曲名、成绩明细、HTML、Token 或 Cookie 内容。

## P0：凭据与原始页面持久化边界

实现：

- 水鱼与 LXNS Token 不再从 `SharedPreferences` 读取或写入，只存在于当前根级 Compose 会话状态；进程/Activity 重建后为空。
- 两个 Wahlap 客户端的 debug/supplemental 原始页面 sink 已移除；认证页、主页、成绩页和补充页只在当前请求、解析与导入调用链的内存中流转。
- `FluentMaiRepository` 与 Room DAO 不再暴露 `wahlap_score_pages` 的读写路径；旧实体和 v5 表仅为数据库 schema 兼容而保留，因此不会破坏已经安装设备的数据库。
- 移除 `fallbackToDestructiveMigration()`，未知 schema 不再以静默清空成绩库作为降级方案。
- 设置页明确说明 Token 与导入页面只在当前会话处理、新原始 HTML 不落盘。
- 新增源码边界回归测试；Room 测试验证普通 repository 操作不会删除旧表中已经存在的行。

验证：

- `\.\gradlew.bat test :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`。
- Gradle XML：30 suites / 149 tests / 0 failures / 0 errors / 0 skipped。
- Debug APK：28,437,980 bytes；SHA-256 `2721EBE7360002E423EA312821A027BF1EECE4ECDF23F3D1C342A58A1AAB5764`。
- 对 serial `2923ae26` 再次执行 `adb install -r` 成功；未卸载或清除数据，首次安装时间仍为 `2026-06-29 02:36:58`。
- 真机冷启动 `TotalTime=714ms`，首页 Rating 仍为 `14655`，1,619 条成绩与 5,360 张谱面全部匹配，日志无 fatal exception。
- 上传区水鱼与 LXNS Token 标签均可见，2 个输入框均为空；设备上旧 `fluentmai_tokens.xml` 仍存在但未被读取。
- 覆盖安装与启动前后，旧 Token 偏好文件和 7 个历史 `wahlap-*.html` 文件的路径、大小、mtime 完全一致；没有清理或重写既有隐私缓存。
- SQLite `integrity_check=ok`、`user_version=5`；成绩 1,619、隔离 9,983、导入批次 29、旧原始页 5，四组规范化语义指纹全部与前一 checkpoint 一致。
- 设置页证据：`C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p0_privacy_boundary.png`。

## P0：谱面查询性能与页面状态

实现：

- 将 5,360 张谱面的标题、艺人、分区、谱师、版本、BPM、ID、难度和类型检索字段一次性规范化并建立内存查询索引；成绩关联也在索引构建阶段完成。
- 全量筛选与确定性排序移到 `Dispatchers.Default`，搜索和等级输入采用 180ms debounce；UI 最多渲染 500 条，但结果计数保留完整匹配数量。
- 新增 Activity 作用域 `ChartQueryViewModel` 与 `SavedStateHandle`，保存搜索、等级、难度、分区、版本、游玩状态、排序以及网格滚动位置；底部 Tab 往返不再重建索引或丢失状态，配置重建后也可恢复。
- 每张卡片不再创建独立 Coil/OkHttp 实例，统一复用应用级图片加载器并请求 320px 目标尺寸；移除每张曲绘成功/失败和 BASIC HTTP 日志。
- 顶层 Tab 使用 `rememberSaveable`；查询状态使用稳定谱面 key，筛选期间显示明确的“筛选中/正在准备谱面结果”状态。
- 新增查询别名、ID、游玩状态、当前版本未来批次隔离、确定性排序，以及 `SavedStateHandle` 筛选/排序/滚动恢复与损坏枚举降级测试。

同设备、同操作口径验证：

- `\.\gradlew.bat test :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`；34 suites / 157 tests / 0 failures / 0 errors / 0 skipped。
- Debug APK：33,747,410 bytes；SHA-256 `FBD0F4513BDE26332163D5403E6741C0ACA31F80628240AD3687D144857145A6`。
- 对 serial `2923ae26` 执行 `adb install -r` 覆盖安装成功；未卸载或清除数据，首次安装时间仍为 `2026-06-29 02:36:58`。
- 冷启动 `TotalTime=697ms`；Rating 仍为 `14655`。
- 首次进入谱面页从基线 102 frames / 4.92% jank / p99 1,050ms，改善到 140 frames / 0.71% jank / p99 109ms；索引后台构建 441ms，初次查询后台计算 215ms。
- 10 次长列表滑动从基线 14.97% jank 改善到首次 11.54%、热缓存反向滑动 9.13%；热缓存相对基线下降约 39%。
- 键盘已就绪时搜索 `PANDORA`：查询后台计算 17ms，5 条结果；帧指标从基线 20.69% jank / p99 150ms 改善到 15.79% / p99 73ms。
- 带搜索状态在“谱面 → 首页 → 谱面”往返后仍为 `PANDORA` 与 5 条结果，索引构建次数保持 1；往返过程 118 frames / 2.54% jank。
- 强制横屏后再恢复原系统旋转设置，搜索与 5 条结果保持；深度滚动后 Tab 往返，前后 17 个可见曲绘语义标识数组逐项一致。
- 日志只包含不带查询内容的索引/结果数量与耗时；未再出现逐曲绘网络日志。
- 覆盖安装后 SQLite `integrity_check=ok`、`user_version=5`；成绩 1,619、隔离 9,983、导入批次 29、旧原始页 5，四组规范化语义指纹全部与基线逐字节一致；临时数据库副本已删除。

## P1：玩家记录与牌子进度

实现：

- 在 `core:model` 建立公开稳定的 `ChartIdentity(songId, songType, difficulty)`；Room 自增主键不参与身份。成绩优先按官方 Song ID、SD/DX 与难度匹配，标题降级只在标题/类型/难度唯一时使用，同名歧义不再猜测或复用一条成绩。
- 建立玩家记录领域目录、成绩等级、FC/FC+/AP/AP+、SYNC/FS/FS+/FSD/FSD+ 规范化、单曲 Rating、统计、组合筛选与确定性排序；5,360 张谱面的映射和过滤均在后台线程执行。
- 玩家记录支持曲名/曲师/谱师/类别/ID 搜索，版本、定数范围、显示等级、难度、类别、SD/DX、成绩等级、FC、FS、已/未游玩、当前/旧版本和牌子阻塞条件组合筛选；支持 Rating、达成率、定数、等级、曲名、版本与歌曲 ID 排序。没有可靠新增时间字段，因此未伪造“最近新增”排序。
- 统计随筛选条件同步更新，覆盖全部成绩等级与数据模型支持的 FC/FS 状态；未映射成绩单独计数。
- 实现将、极、神、舞舞、霸者领域规则与进度：要求谱面总数、完成数、剩余数、百分比、阻塞谱面、当前成绩和差距均由真实曲库/成绩计算。
- 牌子页支持版本、难度、类型、只看未完成/显示全部和按等级分组、曲名、歌曲 ID 排序；版本/曲库缺失时显示“数据不足”，不会宣布完成。
- 筛选与牌子页面状态保存在 `SavedStateHandle`；使用可写入 Bundle 的稳定字符串列表 key，Tab 往返后牌子状态保持。
- 规则与边界记录在 `docs/PLATE_RULES.md`。来源为 SEGA 官方 2020-01-15 牌子公告与官方国际版玩法说明：
  - <https://maimai.sega.jp/news/2020-01-15/>
  - <https://maimai.sega.com/play/howto/>

验证：

- `\.\gradlew.bat test :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`；35 suites / 167 tests / 0 failures / 0 errors / 0 skipped。
- Debug APK：33,747,410 bytes；SHA-256 `2B2E7050FDD2F5E0AF54DBCC1F4E3B057183CF8F42BFD0AB02EB51B223EC0F64`。
- 真机 serial `2923ae26` 使用 `adb install -r` 覆盖安装成功；没有卸载或清除数据，首次安装时间仍为 `2026-06-29 02:36:58`，最终冷启动 `TotalTime=702ms`。
- 真机玩家记录显示 5,360 张谱面、1,618 张唯一映射已游玩、3,742 张未游玩；1 条同名歧义成绩明确保留为未映射，没有被错误复用，数据库中的 1,619 条成绩均未删除或修改。
- “玩家记录 → 牌子进度 → 首页 → 玩家记录”往返后仍停留牌子页；进程存活，清空后的 events 日志 `am_crash` 为 0。
- 真机牌子页按当前有效曲库计算“舞萌 DX 2026 将”为 17 / 92、剩余 75；阻塞谱面展示当前达成率与到 SSS 的差距，未出现虚假完成。
- SQLite `integrity_check=ok`、`user_version=5`；成绩 1,619、隔离 9,983、导入批次 29、旧原始页 5，四组规范化语义指纹全部与基线逐字节一致；临时数据库副本已删除。
- 界面证据：
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p1_player_records.png`
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p1_plate_progress.png`

## P1：社区别名搜索、稳定谱面身份、完整筛选与统一详情

实现：

- 建立双源社区别名管线：运行时独立请求 LXNS `https://maimai.lxns.net/api/v0/maimai/alias/list` 与 Yuzu `https://www.yuzuchan.moe/api/v2/aliases/maimaidx/aliases`，任一来源可用时均可产出合并目录；按规范化别名去重，只有相同官方歌曲 ID 才会合并，不用标题猜测映射。
- 别名缓存包含 schema、来源、抓取时间和确定性 SHA-256 内容版本，更新前校验空数据与 80% 规模回退并原子替换；任一请求、解析、校验或写入失败都保留旧缓存。为平滑继承已验证的 LXNS 单源缓存，继续使用旧私有文件名，但当前 schema 与元数据明确表示双源社区载荷。
- Yuzu 的普通 DX ID `10000..99999` 按上游约定折回基础歌曲 ID；当前源的 56 组折回冲突均为同名歌曲。`>=100000` 的宴会场 ID 保持原值，避免和普通谱面碰撞。来源、许可边界、更新保护与映射规则记录在 `docs/ALIAS_DATA.md`，应用不捆绑或再分发第三方别名库。
- 谱面索引扩展到曲名、别名、歌曲 ID、稳定组合身份、BPM、曲师、谱师、类别、歌曲/谱面版本、难度与 SD/DX；查询使用 NFKC、大小写、常见空白与标点归一化。稳定谱面身份统一为 `songId + SD/DX + difficulty`，Room 自增键和标题均不参与主身份。
- 谱面页新增定数范围、难度、类别、版本、SD/DX、已/未游玩、达成率范围、FC、FS 与排序的组合筛选；手机默认保持紧凑搜索和“更多筛选”，高级条件折叠时显示摘要，可一键重置。筛选、排序、展开状态和滚动位置继续由 `SavedStateHandle`/可保存状态恢复。
- 曲库解析保留上游 `locked` / `disabled` 的可空语义；上线状态明确分为可用、锁定、停用、未来批次与未知。只有显式字段或主要版本证据才下结论，缺失字段显示“未知”，不把最大 ID、无成绩或缺字段伪装成上线状态。
- 新增统一谱面详情页，并从首页 B35/B15/全部成绩、谱面查询、玩家记录与牌子阻塞项复用。详情展示曲绘、稳定身份、同曲难度切换、定数、谱师、音符、歌曲/谱面版本、别名来源、玩家最佳成绩与 FC/FS/DX；没有可靠判定数据的“容错 / 失分”明确显示“数据不足，暂不估算”。
- 删除旧的激进标题归一化成绩索引；成绩关联统一使用精确稳定身份，然后才允许“标题/类型/难度唯一”的保守降级，同名歧义仍保持未映射。

验证：

- `.\gradlew.bat test :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`；36 suites / 179 tests / 0 failures / 0 errors / 0 skipped；`git diff --check` 通过，仅保留既有 `LocalVpnService` 过时 API 编译提示。
- Debug APK：33,747,410 bytes；SHA-256 `F244E8F3B9F3A002D1BCE758F72FC4D318EC4D53688EC63726781A00FD424112`。
- 真机 serial `2923ae26` 使用 `adb install -r` 覆盖安装成功；没有卸载或清除数据，首次安装时间仍为 `2026-06-29 02:36:58`；最终冷启动 `TotalTime=694ms`，Rating 数据在 1,219ms 就绪，无 fatal exception。
- 真机本地缓存和最终在线刷新均得到 1,313 个别名歌曲 ID、10,480 个去重别名；当前 5,360 张谱面的目录中映射 1,241 个歌曲 ID，72 个上游 ID 保留为未映射，没有按标题错误附着。内容版本前缀为 `sha256:463793347cd4`。
- 真机验证社区别名“心跳不止”精确命中歌曲 ID 1512、“哈皮”精确命中歌曲 ID 118；验证纯数字歌曲 ID `834` 得到 9 张谱面并显示 `PANDORA PARADOXXX`。这些别名来自实时合并缓存，不是测试硬编码的产品别名。
- 谱面标题进入详情后使用系统返回键回到 `834` 的查询结果，搜索与列表状态保持；组合身份搜索、双源降级、失败更新保留缓存、Yuzu ID 折回、宴会场 ID 保留、同名歧义与未知上线状态均有 JVM 回归测试。
- 覆盖安装后，旧 Token 偏好元数据仍为 `341|1783304211`，7 个历史 HTML 文件仍存在且没有读取内容；新增的 1 个别名缓存只包含公开社区检索数据。SQLite `integrity_check=ok`、`user_version=5`；成绩 1,619、隔离 9,983、导入批次 29、旧原始页 5，四组规范化语义指纹全部与基线逐字节一致，临时数据库副本已删除。
- 界面证据：
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p2_chart_filters.png`
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p2_chart_detail.png`

## P1：工具箱与 Rating Trend

实现：

- 新增独立 `feature:tools`，将底栏设置入口调整为“工具”；设置页保留在工具箱右上角，并支持界面返回和系统返回回到工具箱。
- 在 `core:model` 集中实现单曲 DX Rating 与谱面失分/达成率/目标容错计算。单曲 Rating 使用既有系数表、100.5% 封顶与向下取整；谱面计算使用 Tap/Touch=1、Hold=2、Slide=3、Break=5 的基础权重和 BREAK 额外 1% 判定段。公式边界、无效输入、BREAK 2550/2500 Perfect 与目标容错均有 JVM 回归测试。
- 工具箱提供 Rating、失分/容错、版本、Kaleid×Scope 与趋势五个区段。版本资料优先使用已校验运行时目录，并在 `core:model` 保留集中维护的离线版本表；同时修复 18500 被错误显示为 MURASAKi 而非 MURASAKi PLUS 的旧映射。
- Kaleid×Scope 建立可替换 model/repository/UI 边界。当前官方页面没有可审计、可稳定更新的结构化门曲与逐门条件，因此明确显示“数据源待接入”，没有硬编码或伪造门曲。
- Room schema 从 5 显式迁移到 6，只新增 `rating_history` 表和时间/来源索引；没有 destructive migration。历史点区分自动导入与手动补录，手动记录支持新增、编辑、确认删除，自动记录不能通过界面编辑或删除。
- 自动历史点只在一次 Wahlap 导入全部成功且真实 B35/B15 已重新计算后写入；启动、升级、失败导入和现有分数不会反推或伪造过去时间点。时间线最后一个 Rating 未变化时跳过重复自动点。
- 趋势图按真实时间比例绘制，支持近 1 月、近 3 月和全部；空历史、来源、日期时间、手动标记与备注均有明确状态。数据来源与公式审计记录在 `docs/TOOLBOX_DATA.md`。

验证：

- `.\gradlew.bat test :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`；39 suites / 191 tests / 0 failures / 0 errors / 0 skipped；`git diff --check` 通过。
- Debug APK：33,747,469 bytes；SHA-256 `B0EF4FE131B1CE9EB77450E4C0ED21BF215320E983A65A0FF4A16AF872A97E59`。
- 首次执行真实 v5→v6 迁移的 `adb install -r` 覆盖安装成功，冷启动 `TotalTime=703ms`；最终 checkpoint APK 再次覆盖安装并冷启动 `TotalTime=661ms`。全程未卸载、未清除数据，首次安装时间仍为 `2026-06-29 02:36:58`，日志没有 fatal exception 或 Room migration error。
- 真机输入定数 `13.5`、达成率 `100.6217%` 得到单曲 Rating `303`、SSS+、系数 `22.4`，并明确显示按 `100.5000%` 封顶；设置页可进入并通过系统返回回到工具箱。
- 真机完成手动趋势记录 `14655` → 编辑为 `14656` → 确认删除的完整闭环，随后空历史状态恢复；新表最终为 0 行，证明冷启动和升级没有生成虚假历史。
- SQLite `integrity_check=ok`、`user_version=6`。既有成绩 1,619、隔离 9,983、导入批次 29、旧原始页 5，四组规范化语义 SHA-256 仍分别为 `22cc15e4fbcb25b12c4c3af7de962795cce41ce6a7f49e8d32c29ad8e484ea21`、`cf8d21c506aabede62b6fed1a10a2e888bdf39dd8acf7de5997b2a23924e0799`、`b13da25b612c298edc0a6393608890f99ced6e2275d1847c63dbfff1d36c76cc`、`4c7a35fe6d9060b61f71d13f45922a657445e1fb78a721b4fa6fa4564ab8919c`，与 v5 基线逐字节一致。
- 旧 Token 文件元数据仍为 `341|1783304211`；7 个历史 HTML 文件的元数据 SHA-256 仍为 `EBB86FFAC0DCB84CF6693C6E8B9A21E9F5C843C4001E4373F208A5C9C3C5E7D4`。没有读取 Token、Cookie 或 HTML 内容，临时数据库副本已删除。
- 界面证据：
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p3_toolbox_rating.png`
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p3_toolbox_rating_calculated.png`
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p3_rating_trend.png`

## P1：可解释推分推荐

实现：

- 在 `core:model` 新增纯领域推荐模型；输入只包含稳定谱面身份、真实本地最佳成绩、谱面定数、当前大版本、B35/B15 集合与用户显式条件。算法不估计玩家技术风格、不伪装成 AI，也不输出无法证明的“最适合你”。
- 无显式目标时使用下一个 `97 / 98 / 99 / 99.5 / 100 / 100.5` 达成率里程碑；输入目标总 Rating 时，按“只提升这一张谱面”计算需要超过当前 Best 尾部的单曲 Rating，并以 `0.0001%` 精度确定能达到该 Rating 的最低达成率。目标达成率与目标总 Rating 同时存在时取更严格目标。
- 每个候选都实际重排受影响的 B35 或 B15，分别展示当前达成率/单曲 Rating、目标达成率/单曲 Rating、单曲理论增量、实际 B50 增量、目标总分、是否进入 Best 集合和封闭枚举的解释原因。
- 结果按实际 B50 增量、单曲增量、达成率差、目标单曲 Rating、定数、曲名与稳定身份确定性排序；相同输入得到相同结果和顺序。
- 推荐只包含有真实成绩和定数、且属于当前 B35/B15 版本范围的谱面。显式锁定或停用谱面仍参与历史 B50 基线以与首页口径一致，但不会作为新的练习建议；未来版本与版本不明谱面安全排除。
- 支持目标总 Rating、目标达成率、定数范围、当前版本/旧版本、排除已鸟加、“不想练”稳定身份集合和只看能实际提升 B50 的筛选。“不想练”集合、筛选条件和推荐页区段使用 `SavedStateHandle` 恢复；可一键恢复排除项与重置条件。
- 推荐卡复用统一谱面详情页；系统返回后仍停留推荐区段。没有可靠拟合难度数据源，因此 UI 明确说明不显示或猜测拟合难度。算法、排序、口径与限制记录在 `docs/RATING_RECOMMENDATIONS.md`。

验证：

- `.\gradlew.bat test :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`；42 suites / 199 tests / 0 failures / 0 errors / 0 skipped；领域测试覆盖 B15 替换、已在 Best 集直接增分、已完成目标、锁定/停用基线口径、未来版本、筛选、确定性与非法范围，ViewModel 测试覆盖区段/筛选/排除恢复。
- Debug APK：33,747,469 bytes；SHA-256 `7AD55980DF7B06A3BC8C18B66C1A0CFEA61FFA36B3AA74CA44BC9D9B0D1210B5`。
- 真机 serial `2923ae26` 使用 `adb install -r` 覆盖安装成功；没有卸载或清除数据，首次安装时间仍为 `2026-06-29 02:36:58`，最终冷启动 `TotalTime=676ms`，日志无 fatal exception。
- 真机 1,618 张稳定映射且可计算的成绩重建出的当前 B50 为 `14655`，与首页完全一致；B35 尾部为 `292`、B15 尾部为 `270`。默认“排除已鸟加 + 只看实际提升”得到 22 条建议，后台计算 267–455ms。
- 真机首条建议为当前 B15 内谱面：`100.0209% / 280 → 100.5000% / 292`，单曲理论与实际 B50 均为 `+12`，目标总分 `14667`；解释明确说明它已在 B15，因此增量直接进入总 Rating。
- 输入目标总 Rating `14660` 后得到 208 条能由单谱面达到目标的候选，计算 80ms；首项离散 Rating 重算后达到 `14666`，没有把不能达到目标的候选混入结果。重置后恢复默认 22 条。
- 真机“不想练”后建议从 22 条变为 21 条，切换“首页 → 记录”后推荐区段与排除项仍在；恢复排除后回到 22 条。推荐卡进入统一谱面详情成功，系统返回仍为推荐区段。
- SQLite `integrity_check=ok`、`user_version=6`；成绩 1,619、隔离 9,983、导入批次 29、旧原始页 5 及四组规范化语义 SHA-256 全部与基线一致，`rating_history` 最终仍为 0 行，临时数据库副本已删除。
- 界面证据：
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p4_rating_recommendations.png`

## P1：Android 平板、横屏与可调整窗口

实现：

- 根导航改为单一响应式 Scaffold：窗口宽度低于 600dp 使用 Bottom Navigation，达到 600dp 后切换 Navigation Rail；Tab 切换、详情返回与设置返回继续复用同一状态和页面，不复制两套业务界面。
- 600dp 断点被提取为纯函数，并锁定 599 / 600 / 840dp 边界测试；根 Scaffold 同时提供 411×914 手机竖屏、914×411 手机横屏和 840×900 展开平板 Compose Preview。
- 成绩、谱面查询、玩家记录、牌子进度、推分建议和谱面详情继续使用 `GridCells.Adaptive(320/340.dp)`；宽屏自动形成多列，窄屏仍保持手机单列。筛选区继续使用可滚动行或全宽输入，不压缩字号。
- 导入、工具箱和设置属于长文本/表单页面，在超宽窗口内居中并限制到 1000dp 阅读宽度；普通手机约束下仍占满可用宽度。

验证：

- `\.\gradlew.bat test :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`；44 suites / 203 tests / 0 failures / 0 errors / 0 skipped；`git diff --check` 通过，仅保留既有 `LocalVpnService` 过时 API 编译提示。
- Debug APK：33,747,469 bytes；SHA-256 `A0A8B7AE9A263D7ED85DC3808739CEE6CFC573CA565DB209C1D9CBD1F6A3A597`。
- 最终 APK 对真机 serial `2923ae26` 执行 `adb install -r` 覆盖安装成功并冷启动 `TotalTime=783ms`；以 1080×2400 / 420dpi 验证 411dp 手机竖屏，以系统 WindowManager 锁定 90° 验证 914dp 手机横屏。横屏侧栏、记录统计、推分筛选、双列建议卡均无重叠或横向溢出。
- 使用可逆 WindowManager 尺寸/密度覆盖验证约 617dp 中等平板和约 864dp 展开平板；展开平板成绩卡自动变为双列。验证结束后逐项恢复为原始 1080×2400 / 420dpi / 锁定竖屏，首次安装时间仍为 `2026-06-29 02:36:58`。
- 本机原先没有 Android Emulator 或 system image；为完成验收，新增独立 `FluentMai_API_34_Tablet` AVD，并在全新空数据的 API 34 Medium Tablet（2560×1600 / 320dpi，逻辑宽度 1280dp）安装同一 APK。冷启动 `TotalTime=4030ms`，本地曲库与谱面结果均为 5,360，谱面卡稳定形成三列，日志无应用 fatal 或 Room 错误；截图后已关闭模拟器，真机仍是唯一在线设备。
- SQLite `integrity_check=ok`、`user_version=6`；成绩 1,619、隔离 9,983、导入批次 29、旧原始页 5 的四组规范化语义 SHA-256 与基线逐字节一致，`rating_history` 仍为 0 行，临时数据库副本已删除。
- 界面证据：
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p5_phone_portrait.png`
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p5_phone_landscape_real.png`
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p5_phone_landscape_final.png`
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p5_landscape_recommendations.png`
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p5_landscape_recommendation_grid.png`
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p5_tablet_expanded.png`
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p5_emulator_tablet.png`
  - `C:\Users\Daozh\.codex\visualizations\2026\07\13\019f5cc4-901b-70b0-803e-290994d3a542\p5_emulator_tablet_charts_ready.png`

## P2：iOS MVP、共享领域层与 macOS Level 1 验证链

实现：

- 将 `core:model` 从 JVM-only 模块迁移为 Kotlin Multiplatform，保留 JVM target 供现有 Android、Room、导入、上传与各 feature 模块消费，并新增 `iosX64`、`iosArm64`、`iosSimulatorArm64` 静态 `FluentMaiShared.framework` target。Android 工程没有为了形式统一而改写；`core:importer` 的 Jsoup/JSON/Wahlap 链路仍是 JVM-only。
- 将 7 组既有领域测试从 JUnit 断言迁移到 `kotlin.test` 公共测试源集；NFKC 规范化改为 `expect/actual`，JVM 继续使用 `java.text.Normalizer`，iOS 使用 Foundation compatibility normalization。公共源码已没有 Java、Android 或第三方运行时依赖。
- 新增面向 Swift 的小型 primitive-only 桥接层：单曲 Rating、Rating 系数、判定损失，以及按当前版本切分 B15/旧版本 B35 的分析器。新测试覆盖 15/35 上限、候补与未来版本隔离、确定性顺序、判定枚举和非法输入；跨平台替换的四位小数格式也由牌子阻塞说明断言锁定。
- 新增 `iosApp/project.yml` 与完整 SwiftUI MVP。iPhone 使用底部 Tab，iPad regular width 使用侧栏；曲库使用自适应多列卡片。功能包括离线公开曲库启动、标题/曲师/编号/本地别名搜索、标准/DX 与分类筛选、谱面详情、用户自定义别名、本地成绩新增/更新、B15/B35、总 Rating、趋势图、单曲 Rating 与判定损失计算器、当前版本设置，以及 iPhone/iPad/旋转布局。
- iOS 只打包现有公开 `fixtures/lxns_song_list_fallback.json`。成绩、趋势与自定义别名以 schema v1 JSON 原子写入 iOS Application Support；不读取 Android 数据库，不接收或持久化 Cookie、Token、auth URL 或网页缓存。iOS MVP 暂不移植 Android 账号导入，以避免在没有真实 Mac/iPhone 验证时扩大隐私和回归风险。
- 新增 `.github/workflows/ios-level1.yml`：在 `macos-15` 上运行公共 Kotlin metadata/JVM 测试，安装 XcodeGen，生成并用 `xcodebuild -list` 检查工程，选择并启动真实可用的 iPhone Simulator，以 unsigned 配置构建、安装和启动 App，等待 5 秒截图，并上传版本信息、共享测试、工程检查、Xcode 构建、启动日志、截图、生成的 pbxproj 与未签名 Simulator App。
- 新增 `IOS_TESTING.md`，为普通 Mac 测试者记录 JDK/Xcode/XcodeGen 准备、工程生成、唯一 Bundle ID、Team、真机 Run、建议验收路径、崩溃日志与截图/录屏反馈方式，并明确禁止分享登录链接、Cookie、Token、网页缓存、Android 数据库或设备备份。

本机验证：

- `:core:model:compileCommonMainKotlinMetadata`、`:core:model:jvmTest` 与 `:app:compileDebugKotlin` 通过，证明公共领域源码、JVM 变体和 Android 消费链兼容。
- 干净执行 `.\gradlew.bat test :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`，45 suites / 206 tests / 0 failures / 0 errors / 0 skipped。
- 公开曲库 JSON、XcodeGen project spec 和 GitHub Actions YAML 解析通过；全部 Swift 源码使用独立 tree-sitter Swift parser 检查，`ERROR` 节点为 0；`git diff --check` 通过。
- 对 32 个变更/新增文本文件执行不输出匹配内容的高置信凭证扫描：私钥、AWS/GitHub/Google key、JWT、Bearer secret、凭证赋值、带凭证 URL 与敏感文件路径命中均为 0。
- Debug APK：19,981,743 bytes；SHA-256 `391C24520E7128044196B3837B81BF0571F38B12E60E5A1922496DAFF3B7026C`。
- 该 APK 对真机 serial `2923ae26` 执行 `adb install -r` 成功；没有卸载或清除数据，首次安装时间仍为 `2026-06-29 02:36:58`。冷启动 `TotalTime=891ms`，日志中 0 个 fatal 标记，验证结束后已 force-stop。
- SQLite `integrity_check=ok`、`user_version=6`。覆盖安装后的关键表行数和按主键排序、UTF-8 compact JSON-lines 规范化 SHA-256 与前一 checkpoint 完全一致：
  - `score_records`：1,619 / `22cc15e4fbcb25b12c4c3af7de962795cce41ce6a7f49e8d32c29ad8e484ea21`
  - `quarantine_records`：9,983 / `cf8d21c506aabede62b6fed1a10a2e888bdf39dd8acf7de5997b2a23924e0799`
  - `import_batches`：29 / `b13da25b612c298edc0a6393608890f99ced6e2275d1847c63dbfff1d36c76cc`
  - `wahlap_score_pages`：5 / `4c7a35fe6d9060b61f71d13f45922a657445e1fb78a721b4fa6fa4564ab8919c`
  - `rating_history`：0 行。
- 当前 Windows 主机没有 Swift/Xcode/macOS，且任务明确禁止 push，因此新增 workflow 尚未实际运行，不能宣称 iOS Simulator build 已通过。此 checkpoint 是“Level 1 候选”：工程定义、共享编译、静态 Swift 检查、macOS CI 和人工测试路径已经就绪；只有在后续由用户将本地 commit 推送后取得真实 macOS workflow 成功日志，才能升级为有证据的 Level 1。

## Checkpoint commits

- `2670892c6ec309d7f628660072bc9dba67980ff8` — `chore: checkpoint pre-epic working tree`
  - 保存任务开始前的 48 个文件；
  - 凭证扫描通过；
  - checkpoint 前 134 项测试通过且 Debug APK 构建成功；
  - 未包含本进度文件。
- `d68dd88ae39068b8031729bff5b44901c4750c02` — `docs: record epic audit baseline`
  - 保存仓库、架构、测试、ADB、真机数据、性能、版本来源、隐私与参考截图审计；
  - 记录方案 1 已获授权并解除任务开始时的高风险阻塞。
- `da31435890c7f9bafa1987a6a832a0e8fdae437b` — `fix: stabilize current-version B15 calculation`
  - 统一主要版本来源、B35/B15 领域计算与谱面当前版本筛选；
  - 加固曲库缓存完整性、规模回退、版本回退和原子替换；
  - checkpoint 当时 145 项测试通过，并以真机覆盖安装确认 Rating `14655`、B15 15 张且用户数据指纹不变。
- `14ad6c20d95602dc49d9329b5eb584947ad89d35` — `fix: enforce transient credential boundary`
  - 停止新增 Token 与原始 HTML 持久化，保留旧 Room v5 schema 与设备既有缓存；
  - 移除 destructive migration 降级并补齐源码边界、旧行保留测试；
  - checkpoint 当时 149 项测试通过，真机覆盖安装后导入数据、旧私密缓存元数据和语义指纹均保持不变。
- `cc9aa8d2728122f3af4c7f71bb7912a6d73f6fc4` — `perf: retain and index chart query state`
  - 谱面查询索引、后台过滤、搜索 debounce 与统一图片加载器完成；
  - 筛选、排序、滚动和 Tab/配置恢复状态迁入 `SavedStateHandle`；
  - checkpoint 当时 157 项测试通过，首次进入、滚动和搜索 jank 均改善且用户数据指纹不变。
- `7a886fde668d5a1b5998882309dc4b8182084701` — `feat: add player records and plate progress`
  - 建立稳定谱面身份、玩家记录领域目录、完整成绩筛选与数据驱动牌子规则；
  - 同名歧义成绩保持未映射，牌子在数据不足时不宣布完成；
  - checkpoint 当时 167 项测试通过，真机 1,619 条成绩与全部导入数据语义指纹保持不变。
- `2b18d6308e66b404cf67b3a26e551429ed94109c` — `feat: expand chart search and chart detail`
  - 接入双源社区别名、稳定身份扩展搜索、组合筛选与统一谱面详情；
  - 上线状态与失分数据不足时明确显示未知，不用标题或最大版本猜测；
  - checkpoint 当时 179 项测试通过，真机社区别名、详情返回与全部既有数据指纹验证通过。
- `e65614b6b672b19a0a2fe7daccd4244392907590` — `feat: add verified toolbox and rating trend`
  - 集中实现 Rating/失分公式、版本资料与 Kaleid×Scope 数据边界；
  - Room v6 新增真实时间轴，自动点只连接完整成功导入，手动点支持新增/编辑/删除；
  - checkpoint 当时 191 项测试通过，真机 v5→v6 迁移和手动 CRUD 后全部既有数据指纹不变。
- `6edc62d778be9bac6bb2eb3f2688daf63db47164` — `feat: add explainable rating recommendations`
  - 以确定性 B35/B15 重算实现可解释推分、目标/范围/版本/不想练筛选和真实 B50 增量；
  - 推荐状态进入 `SavedStateHandle`，卡片复用统一谱面详情且返回后保留推荐区段；
  - checkpoint 当时 199 项测试通过，真机目标总分、排除恢复、详情返回和全部既有数据指纹验证通过。
- `7a94e9cd4f58f988ed08ff37b4ffc1d48ff7e877` — `feat: adapt layouts for Android large screens`
  - 在 600dp 断点切换 Bottom Navigation / Navigation Rail，并为手机横屏、可调窗口与平板复用响应式页面；
  - 实机横竖屏、两组 WindowManager 宽度覆盖与全新 API 34 Medium Tablet AVD 验收通过；
  - checkpoint 当时 203 项测试通过，真机设置恢复、应用数据与历史私密缓存均未改变。
- `79d58ac0033b9322d6555d1a8daa7cd5b94f6022` — `feat: add FluentMai iOS MVP`
  - `core:model` 迁移到 KMP，保留 Android JVM 消费并增加 iOS framework、Swift bridge 与公共测试；
  - 新增 SwiftUI iPhone/iPad MVP、离线本地数据、XcodeGen 工程定义、macOS Simulator CI 与 `IOS_TESTING.md`；
  - checkpoint 当时 206 项测试通过，Android 覆盖安装与数据指纹验证通过；iOS 真实 Xcode/Simulator 结果仍明确等待由用户 push 后的 macOS workflow。

## 最终结果

- Android 主交付已经完成：B15 修复、隐私边界、谱面性能/状态、玩家记录、牌子、社区别名、统一详情、工具箱、Rating 趋势、可解释推分，以及手机横屏/平板/可调窗口均已有实现、自动测试、真机数据保护与对应稳定 checkpoint。
- iOS 次级交付已达到可交给 Mac 测试者和 macOS CI 的 MVP/Level 1 候选：共享领域层、SwiftUI 功能面、响应式 iPhone/iPad 布局、本地持久化、工程生成、unsigned Simulator 构建/启动/截图 workflow 和人工测试说明齐全。受当前 Windows 主机与“不得 push”约束，尚无真实 macOS build 成功日志，因此不虚报为已验证 Level 1。
- 最终干净回归为 45 suites / 206 tests / 0 failures / 0 errors / 0 skipped；公共 KMP metadata、JVM 变体、Android 编译和 Debug APK 均成功。Swift 源码 tree-sitter 语法错误为 0，两份 YAML 与公开曲库 JSON 可解析。
- 最终真机覆盖安装使用 `adb install -r`，没有卸载或清数据；首次安装时间不变，冷启动 `891ms`，无 fatal。SQLite 完整性、schema v6、1,619 条成绩、9,983 条隔离记录、29 个导入批次、5 个历史页面行和四组语义 SHA-256 全部与基线一致。
- 旧 `fluentmai_tokens.xml` 元数据仍为 `341:1783304211`；7 个历史 `wahlap-*.html` 只核对文件名/大小/mtime，其规范化元数据 SHA-256 仍为 `EBB86FFAC0DCB84CF6693C6E8B9A21E9F5C843C4001E4373F208A5C9C3C5E7D4`。没有读取、打印、迁移或删除其内容。
- 最终设备状态：只连接真机 serial `2923ae26`；应用已 force-stop；WindowManager 恢复为 override `1080x2400 / 420dpi`，旋转设置 `accelerometer_rotation=0 / user_rotation=0`；iOS/Android 模拟器均未保持运行。
- 本次 32 个变更/新增文本文件的高置信凭证扫描命中 0。全仓扫描另命中 3 处任务前已有的固定本地代理示例/URL 格式化代码，人工复核为示例占位与动态格式串，不是实际 Token、Cookie、私钥或账号凭证；敏感文件路径命中 0。
- 未执行 push。最终分支保持本地 `master`，所有产品变更按 checkpoint 保存；真实 macOS CI 需要用户之后自行 push 才会触发。
