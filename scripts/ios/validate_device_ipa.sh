#!/bin/bash

set -euo pipefail

IPA_PATH="${1:?usage: validate_device_ipa.sh IPA_PATH EVIDENCE_DIR KMP_FRAMEWORK_BINARY XCODEBUILD_LOG}"
EVIDENCE_DIR="${2:?usage: validate_device_ipa.sh IPA_PATH EVIDENCE_DIR KMP_FRAMEWORK_BINARY XCODEBUILD_LOG}"
KMP_FRAMEWORK_BINARY="${3:?usage: validate_device_ipa.sh IPA_PATH EVIDENCE_DIR KMP_FRAMEWORK_BINARY XCODEBUILD_LOG}"
XCODEBUILD_LOG="${4:?usage: validate_device_ipa.sh IPA_PATH EVIDENCE_DIR KMP_FRAMEWORK_BINARY XCODEBUILD_LOG}"

mkdir -p "$EVIDENCE_DIR"
IPA_PATH="$(cd "$(dirname "$IPA_PATH")" && pwd)/$(basename "$IPA_PATH")"
EVIDENCE_DIR="$(cd "$EVIDENCE_DIR" && pwd)"
EXTRACT_DIR="$EVIDENCE_DIR/extracted-ipa"
rm -rf "$EXTRACT_DIR"
mkdir -p "$EXTRACT_DIR"

test -f "$IPA_PATH"
test -s "$IPA_PATH"
unzip -t "$IPA_PATH" | tee "$EVIDENCE_DIR/unzip-test.txt"
zipinfo -1 "$IPA_PATH" | tee "$EVIDENCE_DIR/ipa-contents.txt"
unzip -q "$IPA_PATH" -d "$EXTRACT_DIR"

TOP_LEVEL_COUNT="$(find "$EXTRACT_DIR" -mindepth 1 -maxdepth 1 | wc -l | tr -d ' ')"
test "$TOP_LEVEL_COUNT" = "1"
test -d "$EXTRACT_DIR/Payload"

APP_COUNT="$(find "$EXTRACT_DIR/Payload" -mindepth 1 -maxdepth 1 -type d -name '*.app' | wc -l | tr -d ' ')"
test "$APP_COUNT" = "1"
APP_PATH="$EXTRACT_DIR/Payload/FluentMai.app"
test -d "$APP_PATH"

if grep -Eiq '(^|/)(DerivedData|\.gradle|gradle-cache|test-results?|reports?|src|source)(/|$)|\.(apk|p12|pfx|mobileprovision|cer|pem|key|sqlite|sqlite3|db|html?)$|(^|/)(cookies?|tokens?)(\.|/|$)' "$EVIDENCE_DIR/ipa-contents.txt"; then
  echo "Forbidden build, source, credential, database, Android, test, or HTML content found in IPA" >&2
  exit 1
fi

PLIST="$APP_PATH/Info.plist"
test -f "$PLIST"
plutil -lint "$PLIST" | tee "$EVIDENCE_DIR/plist-lint.txt"

BUNDLE_ID="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$PLIST")"
EXECUTABLE_NAME="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$PLIST")"
APP_VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$PLIST")"
BUILD_VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$PLIST")"
MINIMUM_OS="$(/usr/libexec/PlistBuddy -c 'Print :MinimumOSVersion' "$PLIST")"
SUPPORTED_PLATFORMS="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleSupportedPlatforms' "$PLIST")"
PLATFORM_NAME="$(/usr/libexec/PlistBuddy -c 'Print :DTPlatformName' "$PLIST")"
SDK_NAME="$(/usr/libexec/PlistBuddy -c 'Print :DTSDKName' "$PLIST")"

test -n "$BUNDLE_ID"
test "$EXECUTABLE_NAME" = "FluentMai"
test -n "$APP_VERSION"
test -n "$BUILD_VERSION"
test -n "$MINIMUM_OS"
echo "$SUPPORTED_PLATFORMS" | grep -Fq "iPhoneOS"
if echo "$SUPPORTED_PLATFORMS" | grep -Fq "iPhoneSimulator"; then
  echo "Simulator platform found in Info.plist" >&2
  exit 1
fi
test "$PLATFORM_NAME" = "iphoneos"
case "$SDK_NAME" in
  iphoneos*) ;;
  *) echo "Unexpected SDK in Info.plist: $SDK_NAME" >&2; exit 1 ;;
esac

MAIN_EXECUTABLE="$APP_PATH/$EXECUTABLE_NAME"
test -f "$MAIN_EXECUTABLE"
test -x "$MAIN_EXECUTABLE"
file "$MAIN_EXECUTABLE" | tee "$EVIDENCE_DIR/main-file.txt"
lipo -info "$MAIN_EXECUTABLE" | tee "$EVIDENCE_DIR/main-lipo.txt"
MAIN_ARCHS="$(lipo -archs "$MAIN_EXECUTABLE")"
echo " $MAIN_ARCHS " | grep -Fq " arm64 "
if echo " $MAIN_ARCHS " | grep -Fq " x86_64 "; then
  echo "x86_64 found in main executable" >&2
  exit 1
fi
xcrun vtool -show-build "$MAIN_EXECUTABLE" | tee "$EVIDENCE_DIR/main-vtool.txt"
grep -Eq 'platform[[:space:]]+IOS([[:space:]]|$)' "$EVIDENCE_DIR/main-vtool.txt"
if grep -Fq "IOSSIMULATOR" "$EVIDENCE_DIR/main-vtool.txt"; then
  echo "Simulator Mach-O platform found in main executable" >&2
  exit 1
fi
otool -L "$MAIN_EXECUTABLE" | tee "$EVIDENCE_DIR/main-otool.txt"
if grep -Eiq 'iPhoneSimulator|iphonesimulator|XCTest' "$EVIDENCE_DIR/main-otool.txt"; then
  echo "Simulator-only load command found in main executable" >&2
  exit 1
fi

test -f "$APP_PATH/lxns_song_list_fallback.json"
test -s "$APP_PATH/lxns_song_list_fallback.json"

test -f "$KMP_FRAMEWORK_BINARY"
file "$KMP_FRAMEWORK_BINARY" | tee "$EVIDENCE_DIR/kmp-framework-file.txt"
lipo -info "$KMP_FRAMEWORK_BINARY" | tee "$EVIDENCE_DIR/kmp-framework-lipo.txt"
KMP_ARCHS="$(lipo -archs "$KMP_FRAMEWORK_BINARY")"
echo " $KMP_ARCHS " | grep -Fq " arm64 "
if echo " $KMP_ARCHS " | grep -Fq " x86_64 "; then
  echo "x86_64 found in Kotlin/Native framework" >&2
  exit 1
fi
grep -Fq -- "-framework FluentMaiShared" "$XCODEBUILD_LOG"
strings "$MAIN_EXECUTABLE" > "$EVIDENCE_DIR/main-strings.tmp"
grep -Fq "IosDomainBridge" "$EVIDENCE_DIR/main-strings.tmp"
rm "$EVIDENCE_DIR/main-strings.tmp"

: > "$EVIDENCE_DIR/embedded-frameworks.txt"
if test -d "$APP_PATH/Frameworks"; then
  find "$APP_PATH/Frameworks" -mindepth 1 -maxdepth 1 -type d -name '*.framework' -print | sort | while IFS= read -r FRAMEWORK; do
    FRAMEWORK_PLIST="$FRAMEWORK/Info.plist"
    test -f "$FRAMEWORK_PLIST"
    FRAMEWORK_EXECUTABLE_NAME="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$FRAMEWORK_PLIST")"
    FRAMEWORK_EXECUTABLE="$FRAMEWORK/$FRAMEWORK_EXECUTABLE_NAME"
    test -f "$FRAMEWORK_EXECUTABLE"
    FRAMEWORK_ARCHS="$(lipo -archs "$FRAMEWORK_EXECUTABLE")"
    echo " $FRAMEWORK_ARCHS " | grep -Fq " arm64 "
    if echo " $FRAMEWORK_ARCHS " | grep -Fq " x86_64 "; then
      echo "x86_64 found in embedded framework $FRAMEWORK" >&2
      exit 1
    fi
    xcrun vtool -show-build "$FRAMEWORK_EXECUTABLE" | grep -Eq 'platform[[:space:]]+IOS([[:space:]]|$)'
    if otool -L "$FRAMEWORK_EXECUTABLE" | grep -Eiq 'iPhoneSimulator|iphonesimulator|XCTest'; then
      echo "Simulator-only load command found in embedded framework $FRAMEWORK" >&2
      exit 1
    fi
    printf '%s: %s\n' "$(basename "$FRAMEWORK")" "$(lipo -info "$FRAMEWORK_EXECUTABLE")" >> "$EVIDENCE_DIR/embedded-frameworks.txt"
  done
fi

if find "$APP_PATH" \( -type d -name '_CodeSignature' -o -type f -name 'CodeResources' -o -type f -name 'embedded.mobileprovision' \) -print | grep -q .; then
  echo "Signing credentials or signature resources found in unsigned app" >&2
  exit 1
fi

set +e
codesign -dvv "$APP_PATH" > "$EVIDENCE_DIR/codesign-display.txt" 2>&1
CODESIGN_DISPLAY_STATUS=$?
codesign --verify --deep --strict "$APP_PATH" > "$EVIDENCE_DIR/codesign-verify.txt" 2>&1
CODESIGN_VERIFY_STATUS=$?
set -e
if test "$CODESIGN_VERIFY_STATUS" -eq 0; then
  echo "App unexpectedly has a valid code signature" >&2
  exit 1
fi

cat > "$EVIDENCE_DIR/validation-summary.txt" <<EOF
validation=passed
bundleIdentifier=$BUNDLE_ID
cfBundleExecutable=$EXECUTABLE_NAME
cfBundleShortVersionString=$APP_VERSION
cfBundleVersion=$BUILD_VERSION
minimumOSVersion=$MINIMUM_OS
supportedPlatforms=iPhoneOS
dtPlatformName=$PLATFORM_NAME
dtSdkName=$SDK_NAME
mainArchitectures=$MAIN_ARCHS
kotlinNativeTarget=iosArm64
kmpFrameworkArchitectures=$KMP_ARCHS
kmpLinkage=static-linked-into-main-executable
simulatorArtifact=false
codesignDisplayExitCode=$CODESIGN_DISPLAY_STATUS
codesignVerifyExitCode=$CODESIGN_VERIFY_STATUS
signingState=unsigned
realDeviceInstallVerified=false
EOF

cat "$EVIDENCE_DIR/validation-summary.txt"
rm -rf "$EXTRACT_DIR"
