# watch_adb_tools

针对 **Noemie OS 2.2.0 / Android 9 / `CD_Watch_A`** 手表（包名 `space.sybb.awatch`）的 ADB 调试工具集：一个手表端 App，用于在定制固件上开启 USB 调试，以及两个在电脑上运行的 ADB 脚本。

## 组成

| 路径 | 作用 |
| --- | --- |
| `app/` | 手表端 App「手表调试」。原生 Java，无第三方功能库。含一键 USB 调试、原厂 USB 开关页、原生开发者选项入口。 |
| `scripts/adb-catch` | 设备出现后第一条命令即 `tcpip 5555`，随后通过 Wi-Fi 建立独立 ADB 连接，避免 USB 被定制系统撤掉后失去调查通道。 |
| `scripts/adb-authorize-app` | 安装后的一次性授权：备份原设置，授予 `WRITE_SECURE_SETTINGS`、开启开发者总开关、在保留已有无障碍服务的前提下启用操作助手。 |

## 快速开始

```bash
./gradlew :app:assembleDebug              # 产物 app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
./scripts/adb-authorize-app 192.168.2.18:5555
```

若尚无可用 ADB 通道，先在一个 tmux 会话中运行 `./scripts/adb-catch [USB序列号] [手表Wi-Fi IP]`。

## 文档

- [`docs/debug-app.md`](docs/debug-app.md)：固件发现、App 功能、构建与授权、能力与限制。
- [`docs/adb-catch.md`](docs/adb-catch.md)：启动窗口抢连并切换 Wi-Fi ADB。
- [`docs/adb-persistence.md`](docs/adb-persistence.md)：ADB 常驻调查记录。

## 状态

已在实机完成编译、安装、授权与页面验证。**USB 热插拔后的恢复效果仍需现场观察**：系统开机代码可能再次写入 HRTEST，因此不能承诺 USB 调试永久保留。
