# 开发文档

本文记录单 App 版 GBF Cache 的架构、关键实现决策、代码结构和测试计划。

## 1. 目标与约束

### 目标

- 一个 APK 同时提供 VPN 分流、mihomo 规则引擎、GBF 缓存核心和 UI。
- 只接管选中的浏览器流量，其他 App 直连。
- 不使用远程上游代理。
- 自动发现可处理 `https` 的已安装浏览器；首次运行默认全选，支持多选。
- 不需要 Root、LSPosed 或 FlClash。

### 约束

- Android 8.0+，arm64-v8a。
- CMFA core 的 JNI 包名固定为 `com.github.kr328.clash.core.bridge`，不能改。
- CMFA core 为 GPL-3.0。
- 浏览器是否信任用户 CA 由其 WebView/网络栈决定，单 App 不能强制。
- SkyLeap（`com.dena.skyleap`）不在支持范围：其 WebView/网络栈不信任用户 CA，单 App 无法注入信任配置；修改 SkyLeap 或 Root/系统 CA 不属于本方案。
- 单 App 与 GBF core 子进程同 UID，必须通过 per-app allowlist 排除自身，避免回环。

## 2. 总体架构

```text
GBF Cache App（单进程）
├─ MainActivity
│  ├─ 原有状态/缓存/指标/日志 UI
│  └─ VPN 状态、CA 安装/导出、浏览器多选与打开
├─ VpnController
│  ├─ 暴露 STOPPED/STARTING/RUNNING/ERROR
│  └─ 通知 UI 更新
├─ CoreService : VpnService
│  ├─ CoreManager.startCore()
│  ├─ Clash.reset() / Clash.load(profileDir)
│  ├─ VpnService.Builder + establish()
│  └─ Clash.startTun(fd, ...)
├─ CMFA core（libclash.so + libbridge.so）
│  └─ mihomo：DNS、规则、TUN、DIRECT
└─ CoreManager
   └─ 子进程：libgbfcore.so
      ├─ HTTP proxy 127.0.0.1:8124
      └─ control 127.0.0.1:8125
```

## 3. 数据流

### 3.1 浏览器请求

```text
浏览器
  │ TCP/UDP
  ▼
TUN（只捕获 allowlist 中的浏览器）
  │
  ▼
mihomo
  ├─ DNS 解析 / fake-ip
  ├─ 规则匹配
  ├─ GBF 域名
  │   ▼
  │ HTTP 出站 127.0.0.1:8124
  │   ▼
  │ GBF core
  │   ├─ 缓存命中 → 本地返回
  │   └─ 未命中 → 回源 GBF CDN → 写入缓存
  └─ 其他域名
      ▼
    DIRECT
```

### 3.2 自身与 GBF core

```text
App 自身 / GBF core 子进程
  │ 同 UID，且不在 VPN allowlist
  ▼
绕过 TUN
  ├─ App → 127.0.0.1:8125 查询状态
  └─ GBF core → GBF CDN 回源
```

### 3.3 mihomo 到 GBF core

mihomo 的 `gbf-core` 出站连接到 `127.0.0.1:8124`。该 socket 通过 `VpnService.protect(fd)` 保护，避免被 TUN 再次捕获。

## 4. 关键决策

### 4.1 复用 CMFA core，而不是 FlClash

选择 CMFA：

- Kotlin/JNI API 直接适配 Android VpnService。
- `Clash.startTun(fd, stack, gateway, portal, dns, markSocket, querySocketUid)` 正是单 App 需要的接口。
- 原生库可从 release APK 提取，不需要 Go/NDK。
- FlClash 是 Flutter 工程，集成成本更高。

### 4.2 自己写 VpnService，不复用 CMFA `service` 模块

CMFA 的 `TunService` 会：

```kotlin
(store.accessControlPackages + packageName).forEach {
    runCatching { addAllowedApplication(it) }
}
```

它会把自身包名加入 allowlist，这在单 App 方案中会导致：

- App 自身流量进入 TUN。
- GBF core 子进程（同 UID）流量进入 TUN。
- mihomo 回源到 `127.0.0.1:8124` 可能形成回环。

因此只复用 `core` 和 `common`，由 `CoreService` 自己实现：

- allowlist 只包含选中的浏览器。
- 绝不加入 `com.sagisawa.gbfaccelerator`。
- 通过 `protect(fd)` 保护 mihomo 出站。

### 4.3 Clash Meta 风格分页面 UI

采用 AppCompat + XML 的深色卡片布局，不引入 Compose/Material 依赖。首页保留三张主卡片：

1. 代理核心卡片：点击启停，运行中时在下方展开“运行指标与缓存监控”。
2. 浏览器代理范围卡片：显示已选/总数，点击进入独立页面。
3. 静态缓存管理与维护卡片：显示文件数与占用，点击进入独立页面。

底部提供日志、证书、设置、帮助、关于入口。浏览器、缓存操作与偏好、CA、日志、权限设置均拆分为独立 Activity；除 `MainActivity` 与 `ProxyTileService` 外，新页面全部 `exported=false`。

`CoreService` 现在同时承担 Go Core 子进程管理和 VpnService TUN 生命周期。

### 4.4 只代理选中的浏览器

`VpnService.Builder.addAllowedApplication(pkg)` 只添加当前勾选的浏览器。这样：

- 未勾选的 App 不受影响。
- App 自身和 GBF core 自动绕过。
- 避免全局 VPN 带来的兼容性和回环问题。

`BrowserCatalog` 通过 `Intent.ACTION_VIEW` + `https` + `CATEGORY_BROWSABLE` 查询浏览器，并在 `BrowserScopeActivity` 中以复选框列表展示。支持全选、清空选择、刷新列表与逐行打开。首次运行默认全选，选择结果保存在 `AppPreferences`。VPN 运行期间修改选择会显示重启提示，需要重启 VPN 后生效。SkyLeap 会被显式列为候选，但当前不支持；首次默认全选时需手动取消。

### 4.5 预编译原生库

CMFA release APK 已包含 arm64 的 `libclash.so` 和 `libbridge.so`。直接提取可以避免：

- 安装 Go。
- 安装 Android NDK。
- 配置 CMake。
- 处理 CMFA 的 `golang-android` 插件。

代价是需要校验 16KB 页对齐；不合格时再从源码重建。

### 4.6 快捷设置磁贴

`ProxyTileService` 继承 `TileService`，提供系统快捷设置中的一键启停入口：

- 运行中或启动中点击磁贴，调用 `CoreService.stop`。
- 已停止或崩溃时点击磁贴，先检查已安装/已选浏览器与 `VpnService.prepare`。
- VPN 未授权或没有已选浏览器时，显示提示并打开 `MainActivity`，由主界面完成授权或选择。
- 磁贴状态来自 `CoreManager.currentState` 与 `VpnController.state`；`RUNNING` 且 VPN `RUNNING` 时显示为激活。
- `onStartListening` 注册状态监听，`onStopListening` 移除监听。
- Manifest 使用 `android.permission.BIND_QUICK_SETTINGS_TILE` 限制绑定方，并只暴露 `android.service.quicksettings.action.QS_TILE`。

## 5. 代码结构

```text
android/app/src/main/java/com/sagisawa/gbfaccelerator/
├─ GbfApplication.kt              # Global.init(this)
├─ core/
│  ├─ CoreManager.kt              # 核心启停、指标与日志缓冲区
│  ├─ CoreService.kt              # VpnService + Go Core 前台服务
│  ├─ VpnController.kt            # VPN 状态
│  ├─ CacheStats.kt               # 缓存目录、文件数与占用统计
│  └─ CoreControlReceiver.kt
├─ cert/
│  └─ CaCertManager.kt            # CA 检测/安装/导出
├─ ui/
│  ├─ MainActivity.kt             # 首页三卡片、核心启停、指标与摘要
│  ├─ BasePageActivity.kt         # 子页面统一 Toolbar/返回
│  ├─ BrowserScopeActivity.kt     # 浏览器代理范围
│  ├─ CacheManagementActivity.kt  # 缓存统计、清理、瘦身与偏好
│  ├─ CertificateActivity.kt      # CA 安装/导出
│  ├─ LogsActivity.kt             # 日志清空/复制/导出
│  ├─ SettingsActivity.kt         # 通知/电池/VPN/应用设置
│  ├─ HelpActivity.kt             # 帮助
│  ├─ AboutActivity.kt            # 关于与版本号
│  └─ ProxyTileService.kt         # 快捷设置磁贴
└─ patch/                         # 浏览器发现/启动
```

### 5.1 `GbfApplication`

```kotlin
class GbfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Global.init(this)
    }
}
```

`Global` 来自 CMFA `common` AAR。必须在任何 `Clash` 调用前执行，因为 `Bridge` 的静态初始化会读取 `Global.application`。

### 5.2 `CoreService : VpnService`

职责：

- 前台服务与通知。
- 启动/停止 GBF core。
- 加载 mihomo 配置。
- 创建 TUN。
- 调用 `Clash.startTun`。
- 处理 `onRevoke` / `onDestroy`。

启动顺序：

```text
startForeground()
  → CoreManager.startCore()
  → wait 8124/8125
  → Clash.reset()
  → Clash.clearOverride(Session)
  → Clash.load(profileDir).await()（15s 超时）
  → Builder.establish()
  → Clash.startTun()
```

停止顺序：

```text
Clash.stopTun()
  → Clash.reset()
  → CoreManager.stopCore()
  → stopSelf()
```

TUN 参数：

```kotlin
addAddress("172.19.0.1", 30)
addRoute("0.0.0.0", 0)
addDnsServer("172.19.0.2")
setMtu(9000)
setBlocking(false)
setSession("GBF Cache")
```

mihomo 参数：

```kotlin
Clash.startTun(
    fd = fd,
    stack = "mixed",
    gateway = "172.19.0.1/30",
    portal = "172.19.0.2",
    dns = "172.19.0.2",
    markSocket = ::protect,
    querySocketUid = ::queryUid,
)
```

`queryUid`：

```kotlin
if (Build.VERSION.SDK_INT < 29) return -1
return connectivity.getConnectionOwnerUid(protocol, source, target)
```

API < 29 时，mihomo native 会回退到 procfs 查询。

### 5.3 `VpnController`

职责：

- 暴露 VPN 状态给 UI。
- 提供 `STOPPED`、`STARTING`、`RUNNING`、`ERROR` 四种状态。
- 保存最近一次错误信息。

状态：

```kotlin
enum class State { STOPPED, STARTING, RUNNING, ERROR }
```

### 5.4 `MainActivity`

首页只负责核心状态、VPN 状态、运行指标与页面导航：

- 代理核心卡片按状态显示“点此启动/停止/重试”，启动前检查至少勾选一个已安装浏览器，再调用 `VpnService.prepare()`，最后启动 `CoreService`。
- VPN 运行状态直接显示在核心卡片副标题与说明中；`RUNNING` 时展开指标卡片。
- 浏览器与缓存卡片分别显示摘要并跳转独立页面。
- 底部按钮跳转日志、证书、设置、帮助、关于。
- 通知权限与电池优化提示保留在 `onCreate`。
- VPN 授权仍使用 `ActivityResultLauncher`。

浏览器查询：

```kotlin
Intent(Intent.ACTION_VIEW, "https://".toUri()).apply {
    addCategory(Intent.CATEGORY_BROWSABLE)
}
pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
```

Manifest 需要 `<queries>` 声明 `VIEW` + `BROWSABLE` + `https`，否则 Android 11+ 可能查不到浏览器。

### 5.5 配置加载

`gbf-cache.yaml` 放在 `app/src/main/assets/`。每次启动 TUN 时复制到：

```text
filesDir/mihomo/config.yaml
```

然后：

```kotlin
Clash.load(File(filesDir, "mihomo")).await()
```

`Clash.load` 接收 profile 目录，内部会读取该目录下的 `config.yaml`；不能直接传 YAML 文件路径。

### 5.6 CA 证书

`CaCertManager` 读取 GBF core 在 `filesDir/certs/ca.crt` 生成的根证书，并提供：

- `getCertInfo(context)`：判断证书是否存在、主题、系统信任状态。
- `createInstallIntent(context)`：打开系统证书安装界面。
- `exportCaCertToDownloads(context)`：导出到 Download 目录。

`CertificateActivity` 在 `onResume()` 刷新状态；`BrowserScopeActivity` 仅提供 CA 提示入口，具体安装与导出集中在证书页。

## 6. 配置说明

核心配置见 [gbf-cache.yaml](gbf-cache.yaml)。

关键点：

- `dns.enhanced-mode: fake-ip`：让 mihomo 按域名分流。
- 不配置 `dns.fallback`：避免 mihomo 默认启用 GeoIP fallback filter 并下载 `geoip.metadb`；当前规则不需要 GeoIP 数据库。
- `proxies.gbf-core.type: http`：GBF core 是 HTTP 代理。
- `server: 127.0.0.1`、`port: 8124`：本机 core。
- `AND,((NETWORK,UDP),(DST-PORT,443)),REJECT`：拦截 QUIC，强制 TCP。
- 私有 IP `DIRECT`：避免本地网络进入 core。
- GBF 域名走 `GBF` 组。
- `MATCH,DIRECT`：其他流量直连。

GBF 域名：

```text
*.granbluefantasy.jp
granbluefantasy.jp
*granbluefantasy.akamaized.net
*granbluefantasy-steam.akamaized.net
*gbf.akamaized.net
```

动态 API `game.granbluefantasy.jp` 会进入 GBF core，但 core 内部不缓存动态 API。

## 7. 防回环设计

### 7.1 主方案

- `addAllowedApplication` 只列浏览器。
- 不列 App 自身。
- GBF core 子进程与 App 同 UID，因此绕过 VPN。
- mihomo 到 `127.0.0.1:8124` 的 socket 通过 `protect` 保护。

### 7.2 备用

如果没有选中任何浏览器，不允许开启 VPN，而不是退回全局模式。这样可以避免用户误操作导致所有流量进入 TUN。

### 7.3 失败处理

如果发现回环：

1. 检查 allowlist 是否意外包含自身包名。
2. 检查 `protect` 是否传入 `Clash.startTun`。
3. 检查 GBF core 是否以子进程方式运行（同 UID）。
4. 最后手段：把 GBF core 合并进 mihomo Go 运行时，由 `protect` 保护回源 socket。

## 8. 构建与测试

### 8.1 CMFA AAR

```powershell
cd cmfa-aar
..\gradle-8.10.2\bin\gradle.bat :core:assembleRelease :common:assembleRelease
```

产物：

```text
cmfa-aar/core/build/outputs/aar/core-release.aar
cmfa-aar/common/build/outputs/aar/common-release.aar
```

复制到 `GBF-Accelerator/android/app/libs/`。

### 8.2 APK

```powershell
cd GBF-Accelerator/android
..\..\gradle-9.7.1\bin\gradle.bat :app:assembleDebug -x buildGoCore
```

### 8.3 单元测试

```powershell
..\..\gradle-9.7.1\bin\gradle.bat :app:testDebugUnitTest -x buildGoCore
```

### 8.4 校验

```powershell
$bt = "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0"
& "$bt\zipalign.exe" -c -P 16 -v 4 app\build\outputs\apk\debug\app-debug.apk
& "$bt\aapt2.exe" dump badging app\build\outputs\apk\debug\app-debug.apk
```

## 9. 测试计划

### 9.1 启动与权限

1. 首次启动，`VpnService.prepare()` 弹出授权。
2. 授权后状态从 `STARTING` 变为 `RUNNING`。
3. 通知显示“VPN 已连接”。
4. 关闭 VPN 后状态回到 `STOPPED`，Go Core 停止。

### 9.2 分流

1. 安装至少两个浏览器，勾选其中一个，确认 allowlist 只包含该包名。
2. 勾选两个浏览器，确认两者流量都进入 VPN，未勾选 App 不受影响。
3. App 自身控制面 `127.0.0.1:8125` 可访问。
4. GBF core 回源不经过 TUN。

### 9.3 缓存

1. 首次打开 GBF，素材从 CDN 下载并写入磁盘。
2. 第二次打开，静态素材命中本地缓存。
3. 动态 API 不写入缓存。
4. 清空缓存后重新下载。

### 9.4 CA

1. 启动核心后 `filesDir/certs/ca.crt` 存在。
2. “安装 CA 证书”打开系统安装界面。
3. “导出 CA 证书”在 Download 目录生成文件。
4. 目标浏览器信任该 CA 后，HTTPS 素材可正常 MITM 缓存。

### 9.5 异常

1. 核心启动失败时 UI 显示 `ERROR` 和错误信息。
2. VPN 被系统或其他 App 撤销时回到 `STOPPED`。
3. 重复点击启动/停止不会产生多个服务实例。

## 10. 已知风险

- 未在 arm64 真机验证；当前环境只有 x86_64 模拟器。
- 各浏览器 WebView 是否信任用户 CA 未逐一验证；不信任时 HTTPS 缓存会失败。
- `CoreService` 停止与 `Clash.load()` 的并发时序需要真机压力测试。
- `common` AAR 会注入 `com.sagisawa.gbfaccelerator.permission.RECEIVE_BROADCASTS`，当前无害。
- 浏览器发现依赖系统 `https` Intent 解析；未声明标准浏览 Intent 的 App 可能不会出现在列表中。

## 11. SkyLeap 不支持

当前单 App 版不支持 SkyLeap（`com.dena.skyleap`）。

原因：

- SkyLeap 的 WebView/网络栈不信任用户安装的 CA；其信任锚由 SkyLeap 自身决定，GBF Cache 无法强制。
- `gbf.game.mbga.jp` 等 GBF 域名被分流到 GBF core 后，core 使用本地 CA 终止 TLS；SkyLeap 拒绝该 CA，握手失败，表现为白屏或无提示。
- 单 App 不包含 LSPatch/Xposed，也没有 Root 系统 CA 安装能力，因此不能为 SkyLeap 增加信任锚。
- 修改 SkyLeap 或使用 Root/系统 CA 不属于当前支持范围，并涉及客户端完整性、更新和账号风险。

若只需 SkyLeap 可用，请将其从 VPN allowlist 移除，让它直连；此时没有缓存加速。替代方案是使用实测信任用户 CA 的浏览器（如 Chrome、Via）。

## 12. gbf-acc-patcher（PC 补丁工具）

`GBF-Accelerator/tools/gbf-acc-patcher` 是上游 PC 端辅助工具，用于把 `SkyLeapModule` 嵌入用户自行提供的官方 SkyLeap 安装包。工具不内置或下载官方 APK，实际重打包由预置的 LSPatch Portable 完成。当前单 App 版不依赖该工具。

### 12.1 环境要求

- Java 21+。
- `GBF-Accelerator/tools/gbf-acc-patcher/build/lspatch/lspatch.jar`。
- 模块 APK，默认使用 `GBF-Accelerator/android/app/build/outputs/apk/debug/app-debug.apk`。
- 可通过 `--java`、`--lspatch`、`--module` 覆盖默认路径。

### 12.2 构建与测试

在 `GBF-Accelerator/tools/gbf-acc-patcher` 下执行：

```powershell
go test -v ./patcher
go build -o gbf-acc-patcher.exe .
```

### 12.3 使用

```text
Usage: gbf-acc-patcher.exe [options] <input.apk | input.apks | directory>

Options:
  -o, --output string   输出目录 (默认: "./output")
  --java string         指定 Java 21+ 路径
  --lspatch string      指定 lspatch.jar 路径
  --module string       指定 SkyLeapModule APK 路径
  -v, --verbose         启用详细日志输出
  --keep-temp           保留临时工作目录 (用于调试排查)
```

常见输入：

```powershell
# Split APK Bundle (.apks / .xapk)
.\gbf-acc-patcher.exe skyleap.apks -o ./output/

# 已解压的 Split APK 文件目录
.\gbf-acc-patcher.exe ./skyleap_splits/ -o ./output/

# 单个 APK
.\gbf-acc-patcher.exe skyleap.apk -o ./output/
```

工具只读取输入文件，解包与重打包在独立临时目录中完成；失败时会清理临时目录。第三方组件与许可证说明见 `GBF-Accelerator/tools/gbf-acc-patcher/THIRD_PARTY_LICENSES.md`。
