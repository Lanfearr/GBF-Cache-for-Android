# 构建文档

本文说明如何从源码构建单 App 版 GBF Cache。最终产物是一个包含 GBF core、CMFA/mihomo core、VpnService 和 UI 的 arm64 APK。

## 1. 构建环境

已验证环境：

| 组件 | 版本 / 路径 |
|---|---|
| 操作系统 | Windows 10/11 |
| JDK | 21（Temurin） |
| Android SDK | 例如 `%LOCALAPPDATA%\Android\Sdk` |
| Android 平台 | `platforms;android-35` |
| Build Tools | `build-tools;35.0.0`（36.0.0 也可） |
| Gradle | 8.10.2（cmfa-aar）/ 9.7.1（App），已解压到仓库根目录 |
| Git | 2.49+ |
| Go | 仅从源码重建 CMFA 原生库时需要 |

检查 SDK：

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
Test-Path "$sdk\platforms\android-35\android.jar"
Test-Path "$sdk\build-tools\35.0.0"
```

如果缺少 API 35，可在 Android Studio 的 SDK Manager 安装 **Android 15.0 (API 35)**。本项目不需要 `cmdline-tools`。

`local.properties` 示例：

```properties
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

`local.properties` 只描述本机 SDK 路径，已被 `.gitignore` 忽略，请勿提交。

## 2. 目录结构

```text
GBFCache/
├─ GBF-Accelerator/              # 上游 Android 工程
│  └─ android/
│     ├─ app/
│     │  ├─ libs/                # 放置 CMFA AAR
│     │  ├─ src/main/assets/     # gbf-cache.yaml
│     │  └─ src/main/java/...    # GbfApplication / core / UI
│     ├─ settings.gradle.kts
│     └─ gradlew.bat
├─ cmfa-aar/                     # CMFA core AAR 构建工程
│  ├─ core/
│  ├─ common/
│  ├─ hideapi/
│  ├─ settings.gradle.kts
│  └─ gradlew.bat
├─ gradle-8.10.2/                # 已解压的 Gradle（cmfa-aar）
├─ gradle-9.7.1/                 # 已解压的 Gradle（App）
├─ gbf-cache.yaml
├─ README.md
├─ DEVELOPMENT.md
└─ NOTICE
```

## 3. 获取源码

```powershell
git clone --depth 1 https://github.com/Sagisawa/GBF-Accelerator.git GBF-Accelerator
```

如果目录已存在但 clone 不完整：

```powershell
Remove-Item -Recurse -Force GBF-Accelerator
git clone --depth 1 https://github.com/Sagisawa/GBF-Accelerator.git GBF-Accelerator
```

## 4. 准备 CMFA 原生库

使用 CMFA release APK 中预编译的 arm64 原生库，避免安装 Go/NDK。

仓库不保留该 release APK；仅在需要重新提取原生库时按下方地址下载，提取完成后可删除。

版本固定为：

```text
ClashMetaForAndroid v2.11.37
cmfa-2.11.37-meta-arm64-v8a-release.apk
```

下载地址：

```text
https://github.com/MetaCubeX/ClashMetaForAndroid/releases/download/v2.11.37/cmfa-2.11.37-meta-arm64-v8a-release.apk
```

提取：

```powershell
$apk = "cmfa-2.11.37-meta-arm64-v8a-release.apk"
$out = "cmfa-aar\core\src\main\jniLibs\arm64-v8a"
New-Item -ItemType Directory -Force -Path $out | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $apk))
try {
    foreach ($name in @("lib/arm64-v8a/libclash.so", "lib/arm64-v8a/libbridge.so")) {
        $entry = $zip.GetEntry($name)
        if (-not $entry) { throw "Missing $name" }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $out ([IO.Path]::GetFileName($name))), $true)
    }
} finally {
    $zip.Dispose()
}
```

校验：

```powershell
Get-ChildItem "cmfa-aar\core\src\main\jniLibs\arm64-v8a"
```

应至少包含：

```text
libclash.so
libbridge.so
```

### 16KB 页对齐校验

Android 15 的 16KB page size 设备要求 ELF `PT_LOAD` 的 `p_align` 为 `0x4000` 或更大。可用 Android NDK 的 `llvm-readelf` 或 PowerShell 解析 ELF：

```powershell
# 如果有 NDK
& "$env:ANDROID_NDK_HOME\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe" -l "cmfa-aar\core\src\main\jniLibs\arm64-v8a\libclash.so"
```

如果 `libclash.so` / `libbridge.so` 不满足 16KB 对齐，需要从 CMFA 源码重建：

- NDK：`29.0.14206865`（与 CMFA 一致）
- Go：按 `core/src/main/golang/go.mod` 要求
- 构建 CMFA `:core` 的 native 部分后替换 `.so`

## 5. 构建 CMFA AAR

如果 wrapper 下载超时，直接使用仓库根目录已解压的 `gradle-8.10.2` / `gradle-9.7.1`。

`cmfa-aar` 是一个独立的最小 Gradle 工程：

- Gradle 8.10.2
- AGP 8.8.0
- Kotlin 2.1.0
- `compileSdk = 35`
- `minSdk = 26`

`core/build.gradle.kts` 不包含 `golang-android` 和 CMake，只消费 `src/main/jniLibs` 下的预编译库。

构建：

```powershell
cd cmfa-aar
..\gradle-8.10.2\bin\gradle.bat :common:assembleRelease :core:assembleRelease
```

产物：

```text
cmfa-aar/core/build/outputs/aar/core-release.aar
cmfa-aar/common/build/outputs/aar/common-release.aar
```

复制到 App：

```powershell
New-Item -ItemType Directory -Force -Path "..\GBF-Accelerator\android\app\libs" | Out-Null
Copy-Item "core\build\outputs\aar\core-release.aar" "..\GBF-Accelerator\android\app\libs\cmfa-core-release.aar" -Force
Copy-Item "common\build\outputs\aar\common-release.aar" "..\GBF-Accelerator\android\app\libs\cmfa-common-release.aar" -Force
```

## 6. 集成到 App

`android/app/build.gradle.kts` 增加：

```kotlin
implementation(files("libs/cmfa-core-release.aar"))
implementation(files("libs/cmfa-common-release.aar"))
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.activity:activity-ktx:1.9.3")
implementation("androidx.annotation:annotation:1.8.0")
```

保留：

```kotlin
packaging {
    jniLibs {
        useLegacyPackaging = true
    }
}
```

`useLegacyPackaging = true` 是 GBF core 以子进程方式执行 `nativeLibraryDir/libgbfcore.so` 的前提。

`GbfApplication` 必须在任何 `Clash` 调用前初始化 CMFA 的 `Global`：

```kotlin
class GbfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Global.init(this)
    }
}
```

Manifest：

```xml
<application android:name=".GbfApplication" ...>
```

## 7. 构建 APK

如果仓库已包含预编译 `libgbfcore.so`，跳过 Go 构建：

```powershell
cd GBF-Accelerator\android
..\..\gradle-9.7.1\bin\gradle.bat :app:assembleDebug -x buildGoCore
```

如果需要重新构建 GBF core：

```powershell
# 需要 Go
cd GBF-Accelerator\android
..\..\gradle-9.7.1\bin\gradle.bat :app:assembleDebug
```

产物：

```text
GBF-Accelerator/android/app/build/outputs/apk/debug/app-debug.apk
```

建议重命名：

```powershell
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "..\..\dist\GBF-Cache-1.0.0.apk" -Force
```

## 8. 校验 APK

列出原生库：

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = "app\build\outputs\apk\debug\app-debug.apk"
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $apk))
try {
    $zip.Entries | Where-Object { $_.FullName -like "lib/arm64-v8a/*" } |
        Select-Object FullName, Length | Format-Table -AutoSize
} finally {
    $zip.Dispose()
}
```

必须包含：

```text
lib/arm64-v8a/libgbfcore.so
lib/arm64-v8a/libclash.so
lib/arm64-v8a/libbridge.so
```

检查包信息：

```powershell
& "$sdk\build-tools\35.0.0\aapt2.exe" dump badging "app\build\outputs\apk\debug\app-debug.apk" |
    Select-String "package:|sdkVersion|targetSdkVersion|native-code"
```

检查 16KB 对齐：

```powershell
& "$sdk\build-tools\35.0.0\zipalign.exe" -c -P 16 -v 4 "app\build\outputs\apk\debug\app-debug.apk"
```

`zipalign -P 16` 检查 APK 内 `.so` 的 16KB 页对齐；ELF 内部 `p_align` 仍需单独检查。

## 9. 安装与调试

```powershell
& "$sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
& "$sdk\platform-tools\adb.exe" shell am start -n com.sagisawa.gbfaccelerator/.ui.MainActivity
```

查看日志：

```powershell
& "$sdk\platform-tools\adb.exe" logcat -s GBF-ACC-Manager GBF-ACC-Service GBF-ACC-Vpn AndroidRuntime
```

检查端口：

```powershell
& "$sdk\platform-tools\adb.exe" shell "cat /proc/net/tcp | grep -i 1FBC"
```

`8124` 十六进制为 `1FBC`，`8125` 为 `1FBD`。

## 10. 常见构建错误

### `android-35` 缺失

安装 Android 15.0 (API 35)。API 37 不能替代 `compileSdk = 35`。

### `libgbfcore.so` 未打包

确认：

- `android/app/src/main/jniLibs/arm64-v8a/libgbfcore.so` 存在。
- `useLegacyPackaging = true`。
- 构建时使用了 `-x :app:buildGoCore` 或本机已安装 Go。

### CMFA AAR 编译失败

- 确认 `compileSdk = 35`。
- 确认 Kotlin 2.1.0 与 serialization 插件版本一致。
- 确认 `common` 依赖 `:hideapi` 为 `compileOnly`。
- 确认 `core` 不再引用 `golang-android` / CMake。

### `System.loadLibrary("bridge")` 失败

- 确认 APK 同时包含 `libbridge.so` 和 `libclash.so`。
- 确认 ABI 为 `arm64-v8a`。
- 确认 `libbridge.so` 的 `DT_NEEDED` 能找到 `libclash.so`。

### VPN 开启后回环

- 确认 allowlist 不包含 `com.sagisawa.gbfaccelerator`。
- 确认 GBF core 子进程与 App 同 UID，因此会绕过 VPN。
- 确认 `markSocket = VpnService::protect` 已传入 `Clash.startTun`。

## 11. 许可证

CMFA core 为 GPL-3.0；mihomo 为 MIT；GBF-Accelerator 为 MIT。最终 APK 按 GPL-3.0 分发，详见 [NOTICE](NOTICE)。
