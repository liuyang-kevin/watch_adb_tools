# 手表调试 App

2026-09-28，适配本机 Noemie OS 2.2.0 / Android 9 / `CD_Watch_A`。包名 `space.sybb.awatch`。原生 Java Activity，未引入第三方功能库；复用固件已有页面与 Android 无障碍 API。

## 实际发现

- 原生 `Settings$DevelopmentSettingsDashboardActivity` 和完整 `DevelopmentSettingsDashboardFragment` 均存在，但 Activity 在固件 Manifest 中为 `enabled=false`。此前启动到的是 `DevelopmentSettingsDisabledActivity`，不是原生开发者界面被删除。
- 本次实测中，shell 执行 `pm enable` 启用这个 Activity 返回 255，已移除此授权步骤。正确入口由原生设置自身启用：写入 `development_settings_enabled=1` 后，进入原生设置页面，`SettingsActivity.onResume → updateTilesList → doUpdateTilesList` 会让设置进程更新开发者 Activity 的启用状态。设备实际的 `APPLICATION_DEVELOPMENT_SETTINGS` 解析结果已变为 `Settings$DevelopmentSettingsDashboardActivity`。
- `com.android.settings.HrTestSwitchActivity` 为导出 Activity，无调用权限限制。页面关闭开关时写入 `/sys/class/bd1860/device/hrtest=0`，成功后写 `persist.sys.dw.hrtest.cmd=false`。
- 厂商 `DwDevelopmentSettingsActivity` 的 ADB 开关与该节点有关联；当前固件 `ro.dw.debug.hrtest=true`，但 `persist.sys.dw.hrtest.cmd` 原值为空。
- `SystemUI.HrTestBootReceiver` 在开机广播后延迟 5 秒写入 `hrtest=1`。这解释了为什么需要调查独立于 `adb_enabled` 的开关；尚未经过手动改变该开关后的 USB 热插拔观察，不能宣布根因已最终验证。
- ADB shell 读取该节点已被 SELinux 拒绝。普通 App 不能直接写它；因此通过厂商自己的系统页面操作，不绕过 SELinux。

证据保存在本地 `.runtime/firmware/`、`.runtime/oem-analysis/`、`.runtime/hrtest-state.log` 和 `.runtime/settings-manifest.xml`，不将固件 APK 或设备日志提交到 Git。

## App 功能

1. **开启 USB 调试**：仅在用户点击后，进入上述厂商页面。操作助手最多在 8 秒内点击一次指定开关，并确认页面读数为 `Current: 0`，之后写入 `Settings.Global.ADB_ENABLED=1`。开关已关闭时不重复点击；失败则报告具体阶段，不显示虚假成功。
2. **打开原厂 USB 开关**：直接进入页面，供手动操作，不触发助手。关闭 HRTEST，页面应显示 `Current: 0`。
3. **原生开发者选项**：进入实际的 Android 开发者 Activity，需要先完成一次性启用。
4. **原生系统设置**：直接进入 `com.android.settings.Settings`，避开 `com.dw.setting` 的裁切界面。
5. 显示系统报告的 ADB 开关、USB 枚举状态和 WLAN 地址；可复制网络 ADB 连接命令。复制命令不会开启网络 ADB。

助手只接收 `com.android.settings` 的窗口事件，只查找两个已核实的 HRTEST 控件 ID。没有用户发起的待处理操作时不读取页面。返回 App 后取消未完成操作。

## 构建与首次授权

```bash
./gradlew :app:assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。

用户已明确授权“实测：安装、授权并打开 App”。APK 已安装，一次性授权已执行。需要重新授权时使用：

```bash
./scripts/adb-authorize-app 192.168.2.18:5555
```

脚本先备份相关设置、App 权限和系统设置包状态到 `.runtime/authorization-时间.json`，然后：

- 授予本 App `WRITE_SECURE_SETTINGS`。
- 写入开发者总开关 `development_settings_enabled=1`。
- 在保留已有无障碍服务列表的前提下加入 `space.sybb.awatch/.UsbModeService`，启用操作助手。

脚本不安装 APK、不重启设备、不改 `service.adb.tcp.port`，也不禁用厂商的开机接收器。已经运行中的 Wi-Fi ADB 调查通道无需切换。

若想手动授权，可执行 `adb shell pm grant space.sybb.awatch android.permission.WRITE_SECURE_SETTINGS` 后，在无障碍设置中启用“手表调试操作助手”，并设置 `adb shell settings put global development_settings_enabled 1`。之后先打开 App 中的“原生系统设置”，返回再打开“原生开发者选项”，由系统设置自身刷新入口；无需 `pm enable`。

本机授权前的原始设置备份为 `.runtime/authorization-20260928-153508-174190.json`；修正授权流程后的第二份备份为 `.runtime/authorization-20260928-153636-552128.json`。已有两项厂商无障碍服务保留，新增本 App 的助手。

原生入口的静态证据保存在 `.runtime/native-fallback/`：`DevelopmentSettingsEnabler.smali` 要求开发者总开关开启、当前用户为管理员或演示用户，且没有 `no_debugging_features` 限制；`SettingsActivity.smali` 还排除 Monkey 模式；`SettingsDrawerActivity.smali` 使用设置进程自身权限调用 `setComponentEnabledSetting`。直接给根 `Settings` 传 `:settings:show_fragment` 不可代替此流程：根入口强制显示 `DashboardSummary`；`SubSettings` 又没有导出给第三方 App。开发者 Fragment 虽列在 `SettingsGateway.ENTRY_FRAGMENTS` 白名单中，也仍须通过可用的正式入口启动。

## 能力与限制

当前已完成编译、安装和授权，并经实机验证：

- 操作助手“手表调试操作助手”已出现在系统无障碍服务列表中（`dumpsys accessibility` 记录见 `.runtime/app-accessibility-state.log`）。
- App 内“原生开发者选项”已实际打开固件的开发者页面（截图 `.runtime/native-developer-open.png`），证明一次性启用流程有效。
- “开启 USB 调试”的完整链路（HRTEST 关闭 → 读回 `Current: 0` → 写入 `ADB_ENABLED=1`）依赖物理开关状态，尚待用户现场拔插验证；系统开机代码可能再次写入 HRTEST，所以不能仅凭该属性持久化就承诺永久保留 USB。

无 root 的 Android 9 普通 App 没有从关闭状态直接开启 TCP ADB 的标准权限；`WRITE_SECURE_SETTINGS` 也不提供系统属性写权限。因此本版优先实现 USB 的原厂控制链路，未加入假的“一键 Wi-Fi ADB”按钮。已有的网络 ADB 由 `scripts/adb-catch` 在启动窗口开启。

已比较系统设置 API、厂商导出页面与现成 Shizuku/ADB 客户端方案：Shizuku 在 Android 9 无 root 重启后仍需外部启动，ADB 客户端库需要服务已监听，都不能代替此处的硬件开关。
