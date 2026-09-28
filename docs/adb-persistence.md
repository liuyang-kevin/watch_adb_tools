# Noemie OS 2.2.0 手表：ADB 常驻调查

2026-09-28 更新：下面是 9 月 23 日的阶段性记录，不能视为“任意时间插线均可连接”的解决结论。后来 USB 在未拔线的情况下消失，用户要求先在启动窗口立即启用 Wi-Fi ADB，以保持调查通道。新版流程见 [抢连并切换 Wi-Fi ADB](adb-catch.md)，USB 热插拔问题仍待继续定位。

调查日期：2026-09-23。用户提供的系统名为 **Noemie OS 2.2.0**。等待脚本已建立 shell，并完成设备只读采集；没有修改配置、安装或刷机。

## 当前结论

**本机已经存在持久化的 ADB 默认配置，且 Android 完整启动后仍可连接；目前没有依据需要再写配置。** 2026-09-23 13:37:56 CST 取得只读快照，13:39:10 CST 再次确认关键连接状态：

| 项目 | 现场值 |
| --- | --- |
| 型号 / 品牌 / manufacturer | `CD_Watch_A` / `BiDeZhi` / `sprd` |
| Android / 构建 | Android `9`，API `28`，`user`，`ro.debuggable=0` |
| 权限 | UID `2000(shell)`，SELinux `Enforcing` |
| 启动状态 | uptime 从 `65.83` 到 `138.90` 秒，`sys.boot_completed=1` |
| 调试设置 | `adb_enabled=1`，`development_settings_enabled=0` |
| USB 配置 | `persist.sys.usb.config=adb`，`sys.usb.config=adb`，`sys.usb.state=adb` |
| 服务 | `init.svc.adbd=running`，`sys.usb.configfs=1` |

第二次读取时，`adbd` 仍运行，三个 USB 配置仍为 `adb`，`adb_enabled` 仍为 `1`。本机 `/vendor/etc/init/hw/init.uws6152_1h10_go.usb.rc` 可读，确有 ADB 的 ConfigFS 配置规则。证据保存在 `.runtime/adb-current-state.log`、`.runtime/adb-latest-state.log`、`.runtime/adb-shell.log`、`.runtime/adb-vendor-rules.log`。当前结果说明 ADB 并非只能存在于启动瞬间；**最初未枚举的原因，以及拔插或下次重启后能否持续使用，仍未确认。** `development_settings_enabled=0` 与当前 ADB 可用并存，不应仅据此修改开发者设置。

电脑端的等待脚本可以尽早建立 shell；它没有“锁定 ADB”的能力。已有 shell 依赖设备端 `adbd` 和传输链路，无法阻止系统停止服务或撤掉 USB 接口。这一限制可直接从 AOSP 的 USB 初始化规则看出：`sys.usb.config=none` 会关闭 USB 并停止 `adbd`；ConfigFS 路径还会解绑 USB 控制器。[传统 USB 初始化规则](https://android.googlesource.com/platform/system/core/+/refs/tags/android-8.1.0_r1/rootdir/init.usb.rc)、[ConfigFS 初始化规则](https://android.googlesource.com/platform/system/core/+/refs/tags/android-8.1.0_r1/rootdir/init.usb.configfs.rc)。

## 这几个状态分别控制什么

| 状态 | 含义 | 对常驻的影响 |
| --- | --- | --- |
| `settings global adb_enabled` | Android 框架的 USB 调试开关 | 正常路径由框架处理开关、USB 功能与授权；厂商可能限制或重置 |
| `persist.sys.usb.config` | 持久化的默认 USB 功能 | AOSP 在启动时用于设置 USB 配置；包含 `adb` 不保证后续不会被覆盖 |
| `sys.usb.config` | 当前请求的 USB 功能 | 运行期值，直接改它不能据此保证重启后保留 |
| `sys.usb.state` | USB 初始化规则报告的已应用功能 | 与请求值一起读取，用于发现切换未完成；不是独立持久开关 |
| `init.svc.adbd` | `adbd` 服务状态 | `running` 是必要信息，但不单独保证 USB 接口可用或主机已授权 |
| `sys.usb.ffs.ready` | FunctionFS ADB 端点准备状态 | 只对使用此实现的固件有意义，不能当作启用开关 |

这张表解释 AOSP 通用机制，本机已取得的具体值见上文。[AOSP 的 adbd 启动说明](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/dev/how_adbd_starts.md)描述了框架、属性与 `init` 的关系。

Android 8.1 的 `UsbDeviceManager` 仅作为历史参照，**不是本机 Android 9 固件源码**：它会持久化 ADB 开关、在启动时回写 `ADB_ENABLED`，并支持 OEM USB 配置覆盖。这说明属性写入成功不等于后续不会被覆盖；目前没有观察到本机出现这种覆盖。[Android 8.1 UsbDeviceManager 源码](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-8.1.0_r1/services/usb/java/com/android/server/usb/UsbDeviceManager.java)。

## 可行路径与条件

1. **当前保持原配置。** 本机 `adb_enabled=1` 且连接正常，无需重复开启。若以后确认开关被关闭，再考虑设置界面或 `settings put global adb_enabled 1`；早期开机时设置服务可能尚未就绪，厂商也可能限制写入。[Android 官方 ADB 使用说明](https://developer.android.com/tools/adb#Enabling)。
2. **若开关保持开启但 USB 功能丢失，调查 USB 策略。** 读取当前和持久配置，再定位固件中的 `init*.usb.rc`、USB 服务和相关日志。不能直接覆盖为 `mtp,adb`：手表未必提供 MTP，对应功能组合还须有固件规则支持。持久属性名称也不等于永久优先级；AOSP 已专门把它的触发限制在启动阶段，避免与框架的运行期配置竞争。[AOSP 相关修订](https://android.googlesource.com/platform/system/core/+/05e04a134e2a7f854f99186306c8033e02328fc0)。
3. **直接设置属性或启动服务取决于权限。** `setprop`、`ctl.start` 与 `ctl.stop` 受系统属性权限及 SELinux 控制；进入 shell 不代表能执行这些操作，UID 为 0 也不能忽略 SELinux 策略。需要结合 `id`、`getenforce` 与实际固件判断。[AOSP 系统属性访问控制](https://source.android.com/docs/core/architecture/configuration/add-system-properties)。
4. **TCP ADB 尚未确认。** 本机有 `persist.sys.adb.tcp.port=5555`，但 `service.adb.tcp.port` 和 `persist.adb.tcp.port` 均为空；这些是不同属性，不能据此断言端口正在监听。只读查询发现设备没有 `ss` 命令，未继续查询或打开端口。旧版 AOSP 读取的是后两者；`adb tcpip 5555` 会请求切换/重启 `adbd`，也不保证常驻。[ADB TCP 服务实现](https://android.googlesource.com/platform/system/core/+/a1da204980891f9e59a41d7a41d510f3f05d34bf/adb/services.cpp)、[Android 8.1 adbd 启动代码](https://android.googlesource.com/platform/system/core/+/refs/tags/android-8.1.0_r1/adb/daemon/main.cpp)。

## 后续复查使用的只读信息

本轮已取得下列关键状态。后续遇到断线时，可用同样的清单对照；先保存属性与身份，再读取依赖 Android 框架的设置和服务状态，并记录电脑时间与 `/proc/uptime`。

```sh
id
getenforce
cat /proc/uptime
getprop ro.product.manufacturer
getprop ro.product.model
getprop ro.product.device
getprop ro.hardware
getprop ro.build.version.release
getprop ro.build.version.sdk
getprop ro.build.fingerprint
getprop ro.build.type
getprop ro.debuggable
getprop ro.secure
getprop ro.adb.secure
getprop sys.boot_completed
getprop ro.bootmode
getprop init.svc.adbd
getprop persist.sys.usb.config
getprop sys.usb.config
getprop sys.usb.state
getprop sys.usb.configfs
getprop sys.usb.ffs.ready
getprop service.adb.tcp.port
getprop persist.adb.tcp.port
getprop persist.sys.adb.tcp.port
```

框架就绪且连接仍在时，再采集：

```sh
settings get global adb_enabled
settings get global development_settings_enabled
dumpsys usb
```

| 观察结果 | 下一步判断 |
| --- | --- |
| `unauthorized` | 手表需要确认该电脑的 RSA 授权；等待脚本不能代替设备端确认 |
| `device` 后 shell 立即失败 | 可能窗口过短或 `adbd` 尚未稳定；保留主机错误和 USB 事件时间 |
| 开机早期有 `adb`，稍后被移除 | 对照框架设置与日志，定位谁重设 USB 功能 |
| `adb_enabled` 从 `1` 变 `0` | 检查框架初始化、厂商服务或策略重置 |
| `adb_enabled=1`，USB 配置也含 `adb`，仍断线 | 继续区分 `adbd` 退出、USB 驱动/线缆/供电、主机权限等因素 |
| 重启前可用，重启后再次只短暂出现 | 当前会话已解决，持久化与启动覆盖问题尚未解决 |

`device` 状态只表示 ADB 已连接，不表示 Android 已完全启动；RSA 授权也是独立条件。[Android 官方设备状态说明](https://developer.android.com/tools/adb#devicestatus)。

## Noemie 资料的可信范围

官方资料：本次没有找到可核实的 **Noemie OS 2.2.0** 官方 ADB 常驻说明；设备自报型号与 Android 版本已记录在本文开头，固件开发方及厂商定制逻辑仍未确认。本文以现场状态为判断依据，AOSP 资料仅用于解释机制。

第三方资料：[S100 手表的个人使用记录](https://g.weich.ee/post/16.html)将其系统称为“noEmie os / 糯米系统”，记录的是 S100、V35，文章没有 ADB 内容。它只辅助理解命名，不能作为本机调试方法的依据。

后续重点是保留现有可用连接，在用户安排拔插或重启时比较状态，确认最初未枚举的原因与跨启动表现。两次自动化连接尝试失败后，应汇报具体失败阶段并转人工，不无限重复自动化。
