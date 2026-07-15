# FluentMai Android 产品收束进度

> 本文记录“Android 产品收束与纠偏”长任务的可恢复 checkpoint。
> iOS 已冻结；本轮不开发、不构建、不验证 iOS。

## Checkpoint 0：只读基线与根因审计（2026-07-15）

状态：完成。此 checkpoint 完成前没有修改产品代码，也没有安装、卸载、清除应用数据、删除 Token/HTML、push 或改写 Git 历史。

### Git 与构建基线

- 分支：`master...origin/master`，ahead/behind `0/0`。
- 基准提交：`7f305f2b6d18e5425e2dee140f4611f06c4aed5f`（`docs: finalize epic delivery report`）。
- 基线工作树：clean；`git diff --check` 通过。
- 命令：`.\gradlew.bat test :app:assembleDebug --console=plain`。
- 结果：成功，约 31 秒；45 个测试套件、206 个测试、0 failure、0 error、0 skipped。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，19,981,743 bytes。
- APK SHA-256：`391C24520E7128044196B3837B81BF0571F38B12E60E5A1922496DAFF3B7026C`。

### Android 实机与应用基线

- ADB：`1.0.41` / Platform Tools `37`；唯一授权设备 `2923ae26`。
- 设备：Xiaomi `23116PN5BC`，Android 16 / SDK 36。
- 物理分辨率：`1440x3200 @ 560 dpi`；当前 override：`1080x2400 @ 420 dpi`。
- FluentMai：`dev.fluentmai.android/.MainActivity`，`0.1.0`，target SDK 34，debug build。
- RankHub：`space.fukakai.rank_hub/.MainActivity`，`1.1.0`（version code 2001），target SDK 36。
- 所有临时 `show_touches` 修改均已恢复为 `0`。

### 用户数据保护基线

数据库通过 `run-as cat` 复制数据库、WAL、SHM 到临时目录后只读查询；临时副本已删除并验证无残留。未输出 Token 或 HTML 内容。

- schema version：6；`integrity_check=ok`。
- 成绩：1,628。
- 隔离记录：9,985。
- 导入批次：30。
- Rating 历史：1。
- 保存页面：5。
- 成绩语义 SHA-256：`6ac81227eff6b942272cac93ab257d9aa769804b15f3ee22f791ca9e37653738`。
- 隔离区语义 SHA-256：`d57a628e55e50b12d5f086fa7caef4256e51f94c225067fb21e820c7866fd74f`。
- 导入批次语义 SHA-256：`63086db3ef5ea7e2c7347f74d5e48ee0ff2758be29c13c667969f8c3ac4a52bf`。
- Rating 历史语义 SHA-256：`e3452c5bf8387bf728aac63e4dba784d16d552e2e6ff35ae125d5da0c4d9aecc`。
- 保存页面元数据语义 SHA-256：`a7e782380bdf4125c832e3049bbdd8a48f66189380a37f5af14878a1faf9b2da`。
- Token 与 7 个 HTML 文件仅元数据组合 SHA-256：`2AD0600156CA355953EE93260E10E45BA51A038E9688ED3A2AA56B2C1479BD5A`。

以上数量和指纹是后续每个实机 checkpoint 的不可破坏基线。旧进度中的 1,619 条成绩等数字只代表上一轮结束时状态，不可覆盖本次新基线。

## 同机只读性能对标

对标只使用公开 UI、ADB 启动计时、触点录屏和 `gfxinfo`；没有访问 RankHub 私有目录、数据库或反编译产物。

### RankHub

- 冷启动 `am start -W`：954 ms。
- 首页进入资料库：触点与首个页面变化同帧，约 283 ms 后视觉稳定。
- 打开筛选面板：触点同帧出现动画，约 233 ms 后稳定。
- 曲库卡片进入明细：触点后的下一录屏帧已经出现完整明细首屏，延迟不超过约一个 35 ms 帧周期。
- 搜索会随输入增量更新，不先阻塞整个页面。

### FluentMai 基线

- 冷启动 `am start -W`：452 ms。
- 首页进入谱面：约 100 ms 后只出现空脚手架；结果经历 `0 → 筛选中/正在准备 → 5360`，触点后约 2.117 秒才稳定。
- 当前内联大筛选器展开虽立即改变画面，但一次布局尾帧约 350 ms，且没有渐进过渡。
- 搜索录屏：82 frames、16 janky frames（19.51%），p50 26 ms、p90 57 ms、p95 73 ms、p99 93 ms。

结论：冷启动不是主要矛盾。主要问题是首次进入谱面时 ViewModel/索引冷建、catalog/alias 多阶段串行刷新和整页等待；筛选器则有同步大布局。目标是“触点立即反馈、内容增量就绪”，不是用假的延时动画掩盖工作。

录屏与截图证据保存在：
`C:\Users\Daozh\.codex\visualizations\2026\07\14\019f62d9-6346-7910-b49a-41ee6d4abd26\android-convergence`。

## 重复实现审计

### 导航与页面重复

- `AppTab` 目前有：首页、记录、导入、谱面、工具五项；目标是首页、导入、谱面、工具四项。
- 首页 `ScoresScreen` 私有维护 `B50 / 全部成绩` 两套列表模式和搜索。
- `PlayerRecordsScreen` 又维护成绩统计、牌子、推荐及自己的筛选/卡片。
- `ChartQueryScreen` 再维护一套曲库搜索、筛选、版本排序和谱面卡片。
- 条件式 tab composition 会销毁/重建页面；谱面 ViewModel 首次点入才创建，没有预热。

收束原则：

1. 唯一全量浏览入口是“谱面”。
2. 首页保留 B50 摘要、最近导入、牌子摘要和快捷入口，不再承载第二套全量成绩浏览器。
3. 删除独立“记录”底栏；记录分析能力并入首页摘要、谱面筛选或工具中的明确任务。
4. 成绩卡片和谱面卡片现有核心信息层级保留，外围控制和视觉语义统一。

### 筛选重复

- 谱面查询使用 `ChartVersionFilter` 的硬编码版本区间。
- 记录页使用另一套 `majorVersions`、搜索归一化、排序和筛选状态。
- “上线新到旧”实际按内部版本 ID 排序，名称和语义不一致。
- 曲师别名存在于局部硬编码，搜索归一化也分散在多个页面/引擎。

目标：一个可复用 `ChartBrowser` 状态模型和一个筛选系统。首屏只展示万能搜索、精确等级/定数输入和“更多筛选”；定数范围、高级条件进入可动画展开的 sheet/section。13、13+、13.3 和范围滑块能力全部保留。

## 牌子与版本根因

### 已确认的代码根因

- 当前版本牌直接使用 `record.chart.chartVersion == versionId`。
- 标题直接拼接 `${versionName}${kind}`，所以会产生错误的 `FiNALE将`，而不是玩家语义 `輝将`。
- 当前 `majorVersions`、`ChartVersionFilter`、版本对照表是三套不同语义。
- 版本对照表优先展示内部 ID，且说明文字写着“别名省略”，与产品目标相反。

### 官方规则与数据核对

- SEGA 官方说明：版本牌的極/将/神/舞舞检查对应版本的 BASIC～MASTER；覇者检查 Standard 全曲 BASIC～Re:MASTER。
- LXNS 公共 API 的 `plate/list?required=true` 会返回每个收藏品的结构化要求集。
- `輝将`（ID 6145）描述为 `maimai FiNALE 全曲/BASIC～MASTER/RANK SSS`，要求 45 首 Standard 曲、180 张谱面，并明确包含 `PANDORA PARADOXXX`（song ID 834）。
- 本地 fallback 中 PANDORA 的 Standard BASIC～Re:MASTER `chart.version` 均为 19998；因此 `==19900` 必然漏掉它。
- 仅改成 `19900 <= version < 20000` 会得到 46 首/184 张，仍多出 `ヒバナ`（song ID 792）。所以“改区间”仍不完整。

将官方要求按 `(songId, chartType, difficulty)` 与本地 fallback 逐项比较后，当前数据呈现稳定模型：官方集合等于版本区间集合减去少量官方排除曲，没有发现官方要求曲落在区间之外。例如：

| 牌子 | 区间谱面 | 官方谱面 | 需要排除的 song ID |
| --- | ---: | ---: | --- |
| 雪将 | 168 | 164 | 731 |
| 輝将 | 184 | 180 | 792 |
| 熊将 / 華将 | 352 | 348 | 146 |
| 爽将 / 煌将 | 380 | 376 | 1213 |
| 星将 / 宙将 | 500 | 492 | 1253, 1267 |
| 祭将 / 祝将 | 588 | 588 | 无 |
| 双将 / 宴将 | 528 | 528 | 无 |

因此实现采用统一 `VersionCatalog`（内部边界、官方名称、玩家牌子名、国服合并语义）和版本级官方排除集；不在业务代码中加入 PANDORA 或某首曲目的单独特判。官方要求集优先，版本区间是可解释的离线基础。

参考：

- SEGA：<https://maimai.sega.jp/news/2020-01-15/>
- LXNS API 文档：<https://maimai.lxns.net/docs/api/maimai>
- maimai.py 牌子模块：<https://maimai.turou.fun/modules/plates.html>
- EasyMai：<https://github.com/Lista233/EasyMai>

EasyMai 与 maimai.py 只用于交叉验证：EasyMai 的手工映射存在重复 key，maimai.py 的纯区间策略也不能覆盖 FiNALE 的官方排除曲，因此不直接复制其规则。

## 其他已确认问题

- 简繁搜索：当前没有统一字形归一化，所以“华火职人”不能命中“華火職人”。需要离线、确定性、覆盖通用字符的简繁映射，而不是为截图词写特判。
- 失分计算器：当前只有手动 Note 数；默认模式应先选谱面并自动读取 `notes.total`，同时保留显式手动模式。
- 难度颜色：MASTER 与 Re:MASTER 的色值在至少三处重复定义；两者目前都偏高饱和紫，缺少可辨识但同族的语义。
- catalog 刷新：本地曲库、本地别名、网络曲库、网络别名串行改变 identity，会重复建立索引。

## 后续稳定 checkpoints

1. **导航与唯一谱面浏览器**：四底栏；首页移除全量重复；统一浏览状态/筛选；渐进式高级筛选；保留全部精确输入能力。
2. **版本、牌子、搜索和颜色语义**：统一 `VersionCatalog`；官方牌子要求；版本名称表；简繁归一化；统一难度 tokens。
3. **交互性能与失分计算器**：谱面预热/索引单次构建；所有点击即时可见反馈；谱面自动 Note 模式；同机复测。
4. **回归与交付**：206+ 单测、APK、实机四底栏/导入/成绩保护、数据库语义指纹、截图和性能复测；更新本文和上一轮进度文档。

每个 checkpoint 都必须保持：不 push、不卸载、不清数据、不删除旧 Token/HTML、不破坏成绩与导入流程。

## Checkpoint 1：四底栏、唯一谱面浏览器与渐进筛选（完成）

- 底栏已收束为：首页、导入、谱面、工具；独立“记录”入口及其重复全量列表、筛选模型和卡片实现已删除。
- 首页只保留 B50 主结构、B35/B15 快速跳转和“已游玩谱面 / 牌子进度 / 推分建议”低层级入口，不再维护第二套全量成绩搜索。
- “谱面”成为唯一全量浏览器；首页“已游玩谱面”只向同一个 ViewModel 注入临时 preset，离开谱面后恢复用户原筛选。
- 万能搜索、13 / 13+ / 13.3、精确定数、定数范围、成绩范围、Rank、FC、FS、类型、难度、版本、分区、游玩状态和排序均保留在一套筛选状态中。
- 首屏固定展示万能搜索、精确等级/定数和高频下拉条件；范围与完整高级条件进入可滚动 `ModalBottomSheet`，筛选 chip 使用换行布局，不再横向裁切。
- 增加当前结果的 total / played / unplayed / SSS+ 摘要及可展开 Rank / FC / FS 计数。
- 谱面索引在应用根层预热，不再等用户首次点击底栏才创建。
- 实机确认首页、谱面首屏和高级筛选面板布局正常；覆盖安装后本地成绩仍为 1,628。

Checkpoint 1 证据：`checkpoint1_home.png`、`checkpoint1_charts_retry.png`、`checkpoint1_more_filters_retry.png`。

## Checkpoint 2：版本、牌子、简繁搜索与难度颜色（完成）

### 统一版本与牌子目录

- 新增唯一 `MaimaiVersionReference / MaimaiPlateVersion` 领域表，同时描述曲库正式名称、相关街机版本名、内部边界、玩家牌子简称、支持的牌子类型及官方排除曲。
- 曲库版本筛选改为使用该表解析小版本归属；补全经典世代和全部 DX 年份，不再只硬编码 2023～2025 与 FiNALE。
- 牌子计算从 `chartVersion == versionId` 改为“版本范围 + 官方排除曲 + BASIC～MASTER”；Re:MASTER 仍按官方版本牌规则排除。
- `MiLK PLUS → 雪`、`FiNALE → 輝` 已写入领域表；实机显示 `輝将 180`，且阻塞列表包含 PANDORA PARADOXXX。
- 回退曲库测试锁定结构化要求数：雪 164、輝 180、熊/華 348、爽/煌 376、星/宙 492、祭/祝 588、双/宴 528、鏡 616。
- 解析核验发现 song ID 1422 的正式曲名是全角空格，旧 `isNotBlank()` 会丢掉其四张谱面；改为只拒绝真正空字符串后，曲库由 5,360 变为 5,364，祭/祝要求恢复为官方 588。
- 版本页重做为“版本名称与牌子对照”：同时展示国服曲库名、相关版本名、牌子简称、内部范围及低层级 ID，不再让内部 ID 成为主要信息。

### 搜索与视觉语义

- 万能搜索使用本地 `opencc4j 1.14.0` 字典，在 NFKC、大小写和标点归一化后统一转简体建立索引；单测覆盖“华火职人”与“華火職人”双向命中。
- 为避免通用转换流水线逐字段启动的性能成本，索引改为读取同一份 OpenCC 字符/短语字典，以最长短语 trie + code-point map 一次转换整份曲库语料；查询词保留进程级缓存，不依赖在线服务，也没有针对截图词写特判。
- MASTER / Re:MASTER 难度色改为单一共享 token（同属紫色家族、深浅可区分），B50、谱面卡片、牌子阻塞行和详情页不再各自维护不同色值。

### Checkpoint 2 验证

- `gradlew test :app:assembleDebug`：成功；45 个测试套件、221 个测试、0 failure / error / skipped。
- APK：22,618,293 bytes；SHA-256 `D56EFCA1F4B168614C09E2D161B859EC1A3D2048E36A3C6F8EC30559B40718F2`。
- 仅执行 `adb install -r` 覆盖安装；未卸载、未清数据。冷启动 746 ms；首页仍为 1,628 条成绩，曲库为纠正后的 5,364 张谱面。
- 实机默认显示 `鏡将 616`；切换 FiNALE 后显示 `輝将 180` 并包含 PANDORA PARADOXXX；版本对照页布局与滚动正常。

Checkpoint 2 证据：`checkpoint2_plate_default.png`、`checkpoint2_finale_plate.png`、`checkpoint2_versions.png`。

## Checkpoint 3：交互性能与自动 Note 失分计算器（完成）

### 谱面索引与点击反馈

- 根层预热继续复用唯一 `ChartQueryViewModel`；目录、成绩和别名从对象身份比较改为结构内容比较，相同网络刷新不再重复建索引。
- 启动阶段的空目录/成绩/别名输入使用 80ms 合并窗口，取消过期 generation；没有数据时直接发布空结果，不做无意义的 `0/0` 索引。
- 简繁搜索语料从每张谱面的十余个字段逐项转换，改为用不可命中的控制字符保留字段边界后整库一次转换。字段范围仍包括曲名、曲师、分区、谱师、版本名、BPM、歌曲 ID、组合身份、难度、类型和社区别名。
- Checkpoint 2 版本曾实测同一目录索引 `3196ms` 后又重复 `2104ms`；最终版只构建一次，冷进程实测 `876–921ms`。最终一次分段为成绩安全匹配 `321ms`、完整简繁语料 `425ms`、索引条目 `145ms`，查询 `99ms`。
- “更多筛选”点击先提交 chip 选中状态，下一帧才挂载完整 ModalBottomSheet，避免较重的弹层首合成吞掉点击反馈；完整高级筛选和范围滑块没有删减。
- 同机预热后“首页 → 谱面”录屏在扣除 ADB shell/tap 调度开销后约 `236–270ms` 出现并稳定为完整结果，RankHub 对照为约 `283ms`。该段 `gfxinfo` 为 74 帧、现代口径 1 个 janky frame（1.35%）、p50 `10ms`、p90 `14ms`、p95 `57ms`、p99 `250ms`；旧基线需要约 `2.117s` 才稳定。
- 最终冷启动 `TotalTime=680ms`；首页在索引后台完成前已可用，谱面入口在预热完成后直接显示 5,364 张结果。所有临时 `show_touches` 设置均恢复为 `0`。

### 失分计算器

- 默认模式改为“选择谱面”：弹层可按曲名/曲师/ID/等级查找，只展示具备完整 Tap/Hold/Slide/Touch/Break 物量的谱面；选中后直接向既有领域公式提供 Note 数。
- 明确保留“手动输入”模式，五类 Note 字段与既有判定、出现次数、目标达成率逻辑不变。
- 实机选择 `PANDORA PARADOXXX · MASTER 14+ · STANDARD · ID 834` 后自动读出总 Note `1309`（Tap 1017 / Hold 98 / Slide 117 / Touch 0 / Break 77）。
- 工具页区段和两组判定 chip 改为自适应换行；在 1080×2400 / 420dpi 手机上 `Break / Good / Miss` 不再被横向裁掉。
- 新增纯映射测试，锁定完整物量可转换、缺失任一类别时拒绝自动模式。

Checkpoint 3 证据：`checkpoint3_nav_sync.mp4`、`checkpoint3_nav_contact_sheet.png`、`checkpoint3_filter_sync.mp4`、`checkpoint3_calculator.png`、`checkpoint3_calculator_final.png`。

## Checkpoint 4：最终回归与数据保护（完成）

- `.\gradlew.bat testDebugUnitTest jvmTest :app:assembleDebug --no-daemon`：成功；46 个测试套件、223 个测试、0 failure / error / skipped。
- `git diff --check`：通过。Debug APK 为 `22,932,701` bytes，SHA-256 `58DF8B62C18692E7CE55BB8F05BBA60BD7D3FD2A4E2F14384B9F9696D2886AD3`。
- 最终 APK 只使用 `adb install -r` 覆盖安装；没有卸载、没有清数据。首次安装时间仍为 `2026-06-29 02:36:58`，最终应用已 force-stop。
- 只读复制数据库、WAL、SHM 后验证 `integrity_check=ok`、schema version `6`；临时副本随后安全删除并确认无残留。
- 最终表数量与语义指纹逐字节命中 Checkpoint 0：
  - `score_records`：1,628 / `6ac81227eff6b942272cac93ab257d9aa769804b15f3ee22f791ca9e37653738`
  - `quarantine_records`：9,985 / `d57a628e55e50b12d5f086fa7caef4256e51f94c225067fb21e820c7866fd74f`
  - `import_batches`：30 / `63086db3ef5ea7e2c7347f74d5e48ee0ff2758be29c13c667969f8c3ac4a52bf`
  - `rating_history`：1 / `e3452c5bf8387bf728aac63e4dba784d16d552e2e6ff35ae125d5da0c4d9aecc`
  - `wahlap_score_pages` 元数据：5 / `a7e782380bdf4125c832e3049bbdd8a48f66189380a37f5af14878a1faf9b2da`
- 旧 Token 与 7 个 HTML 仅核对路径、大小和 mtime，组合 SHA-256 仍为 `2AD0600156CA355953EE93260E10E45BA51A038E9688ED3A2AA56B2C1479BD5A`；没有读取、输出、迁移或删除内容。
- 最终设备仍只有真机 `2923ae26`；WindowManager 为 override `1080x2400 / 420dpi`，旋转为 `accelerometer_rotation=0 / user_rotation=0`。
- RankHub 全程只读公开 UI 对标；没有访问其私有数据。iOS 按任务要求冻结，没有修改、构建或验证。
- 未 push、未提交、未改写 Git 历史。
