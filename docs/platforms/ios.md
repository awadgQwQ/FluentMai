# iOS platform

## Status

iOS is an Experimental Alpha implemented in SwiftUI with selected shared Kotlin Multiplatform domain code from `core/model`.

- Minimum deployment target: iOS 17.0
- Device artifact: unsigned `iphoneos arm64` IPA
- Current preview: `v0.2.0-ios-alpha.1`
- Signing: users must sign the IPA with their own Apple identity before installation

The existing IPA is a real generic-device build, not a Simulator archive. Experimental status means feature coverage and data behavior do not necessarily match Android.

## Installation

Download the unsigned IPA from the [iOS Experimental Preview](https://github.com/Daozhu1007/FluentMai/releases/tag/v0.2.0-ios-alpha.1), then follow [IOS_SIDELOAD_GUIDE.zh-CN.md](../../IOS_SIDELOAD_GUIDE.zh-CN.md). FluentMai does not distribute signing certificates, provisioning profiles, Apple account credentials, or passwords.

## Build and validation

iOS builds require macOS, Xcode, JDK 17, and XcodeGen. The repository maintains two independent validation workflows:

- `iOS Level 1` compiles and tests the shared KMP domain layer, runs `iosSimulatorArm64Test`, builds the SwiftUI app for an iOS Simulator, launches it, and captures evidence.
- `iOS Device unsigned IPA` builds the KMP `iosArm64` framework and an unsigned `iphoneos arm64` app, packages a standard Payload IPA, and statically validates its architecture and contents.

Generated Xcode projects and build products are not committed.

## Shared scope and limitations

Only selected domain behavior in `core/model` is shared with Android. UI, platform integration, persistence, installation, and release cycles remain independent. No Android-to-iOS data migration guarantee is currently documented.
