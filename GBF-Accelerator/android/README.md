# GBF Cache Android

单 App 版 Android 工程，集成 GBF core、CMFA/mihomo core、`VpnService` 与 UI，不依赖 Root、LSPosed 或 LSPatch。浏览器通过 `https` Intent 自动发现，支持多选与逐行打开。

## 目录

- `app/`：Android 应用、VPN 服务、核心管理与 UI。
- `app/libs/`：CMFA AAR 构建输入。
- `app/src/main/jniLibs/arm64-v8a/libgbfcore.so`：预编译 Go core。
- `app/src/main/assets/gbf-cache.yaml`：内置 mihomo 配置。

## 构建

使用仓库根目录已解压的 Gradle 9.7.1：

```powershell
..\..\gradle-9.7.1\bin\gradle.bat :app:assembleDebug -x buildGoCore
```

如果本机已安装 Go，需要同时重建 GBF core，可去掉 `-x buildGoCore`。

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 测试与检查

```powershell
..\..\gradle-9.7.1\bin\gradle.bat :app:testDebugUnitTest -x buildGoCore
..\..\gradle-9.7.1\bin\gradle.bat :app:lintDebug -x buildGoCore
```

## 相关文档

- [构建说明](../../BUILD.md)
- [架构与实现](../../DEVELOPMENT.md)
- [使用说明](../../README.md)
