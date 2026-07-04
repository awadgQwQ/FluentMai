# FluentMai

[English](README.md) | [简体中文](README.zh-CN.md)

FluentMai 是一个 Android 辅助应用，用于导入、管理、浏览并同步 maimai DX 成绩数据。

FluentMai 目前是公开 Beta 预览版。它是独立的非官方项目，上游页面、数据来源或第三方 API 的行为都可能发生变化。

- [下载 Beta](https://github.com/Daozhu1007/FluentMai-Android/releases)
- [隐私模型](docs/PRIVACY_MODEL.md)
- [产品范围](docs/PRODUCT_SCOPE.md)

## Overview

FluentMai 面向希望在自己设备上管理 maimai DX 成绩的玩家。它提供本地优先的导入、校验、保存和浏览流程，并允许用户按需把本地成绩同步到受支持的社区成绩服务。

这个项目的重点不是把所有功能一次做满，而是让 Wahlap 导入、数据校验、本地 Room 存储、成绩浏览和上传链路都尽量清晰、可测试、可追踪。当前产品方向是 local-first：导入后的成绩以本地数据库为准，外部同步始终是用户主动触发的可选操作。

## Key Features

- 通过本地 Hook 流程导入 Wahlap auth 数据。
- 支持手动 Cookie / Reqable 导入作为 fallback。
- 使用 Room 在本地保存成绩。
- 支持包含 SD/DX 的谱面身份识别和去重。
- 计算 Rating 和 B50。
- 浏览成绩与谱面，支持搜索和筛选。
- 上传到 Diving Fish 和 LXNS。
- Diving Fish rebuild 使用明确确认保护。
- 导入校验、异常隔离和隐私信息脱敏。

## Download & Installation

FluentMai 当前目标平台为 Android 14，最低支持 Android 8.0（`minSdk 26`）。

1. 打开 [GitHub Releases](https://github.com/Daozhu1007/FluentMai-Android/releases)。
2. 下载最新 Beta APK。
3. 在受支持的 Android 设备上安装。
4. Android 可能会要求允许浏览器或文件管理器安装 APK。

## Quick Start

1. 打开 FluentMai。
2. 通过 Hook 或手动 fallback 导入 Wahlap 数据。
3. 检查导入结果，包括被拒绝或进入 quarantine 的记录。
4. 浏览 Rating、B50、成绩和谱面。
5. 按需将本地成绩同步到 Diving Fish 或 LXNS。

## Engineering Highlights

- 模块化 Android/Kotlin 架构。
- 以 Room 为核心的本地优先持久化。
- 包含 SD/DX 的确定性谱面身份识别。
- 导入校验和 quarantine 流程。
- Rating/B50 回归验证。
- 普通上传与 destructive rebuild 隔离。
- 面向 Beta 产物的 exact-HEAD 可复现构建验证。

## Project Structure

- `app` - Android 入口、导航、平台集成和网络传输。
- `core/model` - 成绩、谱面、导入和 quarantine 的共享模型。
- `core/importer` - Wahlap 解析、曲库处理、校验和导入流程。
- `core/database` - Room schema、DAO 和 repository 持久化。
- `core/privacy` - 日志和界面诊断文本的脱敏工具。
- `core/exporter` - 面向上传格式的成绩 payload 导出。
- `core/upload` - Diving Fish 和 LXNS 上传、校验与 rebuild 逻辑。
- `feature/home` - 首页概览界面。
- `feature/import` - 导入流程界面。
- `feature/scores` - Rating、B50、成绩列表和谱面浏览界面。
- `feature/settings` - 上传和设置界面。
- `feature/quarantine` - Quarantine 记录查看界面。

## Build from Source

需要准备：

- Android SDK。
- 与 Android Gradle Plugin 8.7.3 兼容的 JDK 17。
- 包含 `sdk.dir` 的 `local.properties`，或 `ANDROID_HOME` / `ANDROID_SDK_ROOT`。

Windows:

```powershell
.\gradlew.bat test assembleDebug
```

Unix/macOS:

```sh
./gradlew test assembleDebug
```

项目使用 Android Gradle Plugin 8.7.3、Kotlin 2.0.21、Jetpack Compose、Room、Kotlin coroutines 和 Ktor client 相关依赖。

## Privacy & Security

当前规则见 [隐私模型](docs/PRIVACY_MODEL.md)。

本地 Room 数据库是导入成绩数据的事实来源。Secrets 和原始 auth 信息不应提交到仓库；应用设计上不会把 Wahlap auth URL 或上传 token 写入 Room，而是保留在当前 UI/app 状态中。日志和界面诊断文本会对凭据字段、认证 URL、原始 HTML、输入值以及类似 token 的响应文本进行脱敏。

仓库内置 fixtures 使用合成或公开测试数据。同步到 Diving Fish 或 LXNS 是可选操作。

## Beta Limitations

- FluentMai 仍是 Beta 软件，可能存在问题。
- 上游页面或 API 变化可能导致导入或同步流程失效。
- 执行 destructive Diving Fish rebuild 前，用户应自行核对重要成绩数据。
- Diving Fish rebuild 需要明确确认。

## Disclaimer

FluentMai 是独立的非官方项目。

它不隶属于 SEGA、Wahlap、Diving Fish 或 LXNS，也未获得这些组织的认可或背书。
