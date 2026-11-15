# GBF Cache（单 App 版）

GBF Cache 是一个 Android 端的《Granblue Fantasy》静态资源缓存加速器。它把 **VPN 分流、mihomo 规则引擎、GBF 缓存核心和 UI** 集成在同一个 APK 中，不需要安装 FlClash，也不需要 Root 或 LSPosed。

> 当前仓库处于开发阶段。构建方式见 [BUILD.md](BUILD.md)，架构与实现细节见 [DEVELOPMENT.md](DEVELOPMENT.md)。

> **SkyLeap 不支持**：当前单 App 版不支持 `com.dena.skyleap`。SkyLeap 不信任用户安装的 CA，单 App 无法强制其信任；TLS 握手失败会导致白屏。请使用实测信任用户 CA 的浏览器，或让 SkyLeap 直连（不加速）。原因见下文「SkyLeap 不支持」。

## 目标

- 只接管选中的浏览器流量，其他 App 直连。
- 自动发现可处理 `https` 的已安装浏览器，首次运行默认全选，可在 App 内多选。
- GBF 静态素材走本地缓存核心；动态 API 不缓存。
- 一个 APK、一个 VPN 开关、内置规则，不需要手动导入配置。
- 不使用上游代理；非 GBF 流量不经过任何远程服务器。

## 工作原理

```text
选中的浏览器
      │
      ▼
Android VpnService / TUN
      │
      ▼
mihomo（CMFA core，进程内）
      ├─ GBF 域名 ──► 127.0.0.1:8124 ──► GBF core（MITM + 缓存）
      └─ 其他域名 ──► DIRECT
```

App 自身和 GBF core 子进程不加入 VPN allowlist，因此它们的回源流量会绕过 TUN，避免形成回环。

## 功能

- 系统级 VPN 分流，覆盖实测信任用户 CA 的 WebView 浏览器。
- 只代理已勾选浏览器；App 自身和其他 App 直连。
- 内置 GBF 域名规则，无需导入 FlClash 配置。
- 静态素材磁盘缓存、缓存命中统计、缓存清理。
- 可选 RAM 缓存和预取（移动端默认关闭）。
- QUIC/UDP 443 拦截，强制回退到 TCP，保证缓存生效。
- 本地 CA 证书安装入口。
- 系统快捷设置磁贴，一键启停代理核心。
- Clash Meta 风格深色卡片 UI：首页核心卡片、运行指标、浏览器范围、缓存管理，底部提供日志、证书、设置、帮助、关于入口。

## 环境要求

- Android 8.0（API 26）及以上。
- arm64-v8a 设备。
- 首次开启 VPN 时系统会要求授权。
- 浏览器需要信任本地 CA 证书，才能正常访问 HTTPS GBF 资源。
- SkyLeap（`com.dena.skyleap`）不支持，原因见下文「SkyLeap 不支持」。

## 安装与使用

1. 安装 `GBF-Cache-1.0.0.apk`（arm64-v8a）。
2. 打开 App，进入“浏览器代理范围”勾选目标浏览器（不要勾选 SkyLeap）。
3. 进入“证书”页安装 CA 证书，按系统提示把证书安装为用户 CA。
4. 返回首页，点击“代理核心”卡片，在系统弹窗中授权 VPN。
5. 等待核心卡片显示运行中；点开“运行指标与缓存监控”可查看 PID、缓存命中、请求与 H2 复用。
6. 首页“静态缓存管理与维护”可查看缓存统计、清理或瘦身，并可切换 RAM 缓存与后台预加载。
7. 底部可进入“日志”“设置”“帮助”“关于”；可将“GBF Cache”添加到系统快捷设置，用于一键启停。首次使用前仍需在 App 内完成 VPN 授权和浏览器选择。

## CA 证书

- GBF core 首次启动后会生成 `files/certs/ca.crt`。
- App 内“证书”页提供“安装 CA 证书”按钮，会调用系统 `KeyChain` 安装流程。
- 如果系统不支持直接安装，可导出到 Download 目录后手动导入：
  `设置 → 安全 → 加密与凭据 → 安装证书 → CA 证书`。
- 证书 issuer 为 `GBF Local Accelerator`。
- **浏览器是否信任用户 CA 取决于其 WebView/网络栈实现，需要实测。** 如果出现 SSL 证书错误，请先确认 CA 已安装并启用；单 App 无法强制浏览器信任用户 CA。

## 浏览器范围

App 会在“浏览器代理范围”页自动发现可处理 `https` 链接的已安装浏览器，并以复选框列表展示：

- 首次运行默认勾选全部已发现浏览器；SkyLeap 即使被默认勾选，也应手动取消。
- 可同时勾选多个浏览器；勾选结果会保存。支持全选、清空选择与刷新列表。
- SkyLeap（`com.dena.skyleap`）即使未被系统查询返回，也会作为显式候选列出，但当前不支持，不应勾选；原因见下节。
- 未勾选的 App 不经过 VPN，保持直连。
- App 自身和 GBF core 永远不会加入 allowlist。
- VPN 运行期间修改勾选，需要重启 VPN 后生效。

## SkyLeap 不支持

当前单 App 版不支持 SkyLeap（`com.dena.skyleap`）。

原因：

- SkyLeap 的 WebView/网络栈不信任用户安装的 CA；单 App 无法向其他应用注入信任配置。
- GBF 域名被分流到 GBF core 后，core 使用本地 CA 终止 TLS；SkyLeap 拒绝该 CA，握手失败，表现为白屏或无提示。
- 让 SkyLeap 信任该 CA 需要修改 SkyLeap（LSPatch/重打包）或把 CA 安装为系统 CA（Root）。这两种方式不在单 App 支持范围内，并可能带来客户端完整性、更新和账号风险。

可行替代：

- 使用 Chrome、Via 或其他实测信任用户 CA 的浏览器。
- 如需继续使用官方 SkyLeap，请将其从 VPN allowlist 中移除，让它直连；此时没有缓存加速。

> 上游 SkyLeap 修补/LSPosed 说明属于上游或历史方案，不适用于本单 App 版。

## 缓存说明

- 静态素材：图片、音频、视频、JS、CSS 等可缓存。
- 动态 API：`game.granbluefantasy.jp` 等动态接口不缓存。
- 缓存目录：`files/cache/gbf/https`。
- 可在首页“静态缓存管理与维护”页查看缓存大小、清理缓存、执行缓存瘦身。
- RAM 缓存和预取默认关闭，可在缓存页切换，避免移动端内存和网络抖动。

## 验证缓存是否生效

1. 打开 GBF，进入游戏或切换页面。
2. 回到 App 查看“磁盘命中”是否增长。
3. 查看日志中是否出现 GBF core 的缓存命中记录。
4. 断网后重新进入已缓存页面，静态素材应仍可加载（动态 API 仍需要网络）。

## 故障排查

### VPN 无法开启

- 确认至少勾选了一个已安装浏览器。
- 确认系统 VPN 授权已允许。
- 检查是否有其他 VPN/代理 App 正在运行。
- 查看 App 日志中的 `VpnService` 错误。

### 浏览器无法访问 GBF / SSL 错误

- 确认 CA 已安装为用户 CA 并在系统设置中启用。
- 确认系统时间正确。
- 重新安装 CA 后重启浏览器。
- 如果浏览器是 SkyLeap：当前不支持，见上文「SkyLeap 不支持」；不要通过修改 SkyLeap 或 Root 来规避，相关方式不在支持范围内。

### 缓存命中不增长

- 确认目标浏览器已安装并已勾选。
- 确认 VPN 状态为 RUNNING，代理徽章显示 VPN ACTIVE。
- 确认 GBF core 正在监听 `127.0.0.1:8124`。
- 检查 QUIC 是否被正确拦截。
- 查看日志中是否有 `disk_hits` 增长。

### 开启 VPN 后网络异常

- 关闭 VPN，确认网络恢复。
- 检查是否误把 App 自身加入 allowlist。
- 检查 GBF core 是否崩溃；VPN 应自动停止或提示错误。
- 确认只代理已勾选浏览器。

### 浏览器证书错误

这是已知风险。单 App 方案无法强制 WebView 信任用户 CA。可尝试：

- 在 App 内勾选其他信任用户 CA 的浏览器。
- 在系统设置中确认 CA 已安装并启用。
- 如果仍失败，需要浏览器补丁或 Xposed 方案；这些方案不在单 App 支持范围内。

## 安全与隐私

- 所有缓存和代理都在本机运行。
- 不使用远程上游代理。
- 非 GBF 流量直连。
- VPN 只接管选中的浏览器。
- 本地 CA 仅用于 GBF 域名 MITM；请勿把 CA 私钥分享给他人。
- 不建议在不可信设备上安装本地 CA。

## 卸载

1. 关闭 VPN。
2. 停止 Go Core。
3. 卸载 App。
4. 如需彻底清理，删除系统用户 CA 中的 `GBF Local Accelerator` 证书。

## 许可证

本项目包含或链接以下组件：

- GBF-Accelerator：MIT
- mihomo：MIT
- ClashMetaForAndroid core：GPL-3.0

由于集成了 CMFA core，最终 APK 按 GPL-3.0 分发。源码和构建脚本见本仓库；分发二进制时需要同时提供对应源码。详见 [NOTICE](NOTICE)。

## 相关文档

- [BUILD.md](BUILD.md)：构建环境、CMFA AAR、APK 构建与验证。
- [DEVELOPMENT.md](DEVELOPMENT.md)：架构、数据流、关键实现和测试计划。
- [gbf-cache.yaml](gbf-cache.yaml)：内置 mihomo 配置。
