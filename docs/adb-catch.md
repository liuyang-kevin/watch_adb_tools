# 启动窗口抢连并切换 Wi-Fi ADB

2026-09-28 更新：旧脚本只保留 USB shell，无法在定制系统撤掉 USB 后继续调查。现在设备出现后的第一条命令是 `tcpip 5555`，然后通过 Wi-Fi 建立独立的 ADB 连接。

## 执行顺序

1. 原生 `adb -s SERIAL wait-for-usb-device tcpip 5555`，切换前不启动 shell、不读取属性。
2. 接受 `adbd` 重启引起的 USB 断开，不重复执行 `tcpip`。
3. 若指定手表 IP，直接连接网络；否则最多等待 60 秒，等 USB 恢复并取得 `wlan0` 的 IPv4 地址。
4. `adb connect IP:5555` 后，实际执行网络 shell 读取设备序列号。与指定序列号一致才打印 `AWATCH_WIFI_ADB_CONNECTED`，随后保留网络交互 shell。

手表需要连接与电脑互通的 Wi-Fi。当前为 Android 9，使用传统 TCP ADB，无需 `adb pair`。`tcpip` 返回成功本身不等于网络连通；以网络 shell 成功为准。

## 使用

在 `apps/awatch` 目录运行：

```bash
./scripts/adb-catch SERIAL
# 已知手表 Wi-Fi IP 时可省去等待 USB 恢复：
./scripts/adb-catch SERIAL 192.168.1.50
```

不指定序列号时选择唯一 USB ADB 设备。`Ctrl-C` 停止等待；网络连接失败两次后停止自动重试。若已请求 TCP 模式但未取得 IP，可从手表 Wi-Fi 页面读取后直接运行 `adb connect IP:5555`，无需重复切换 TCP 模式。

## 本次后台会话结果

2026-09-28 10:21:01（北京时间）启动，序列号 `45965672830742`。实际结果：10:25:00 `tcpip 5555` 返回成功；10:25:17 取得 `192.168.2.18:5555` 并通过 `adb connect` 建立网络 shell，打印 `AWATCH_WIFI_ADB_CONNECTED`。交互 shell 于 10:40 结束，该 tmux 会话已清理。网络 ADB 此后保持可连，后续操作均走 `adb -s 192.168.2.18:5555`。

## 边界

- USB 授权仍需设备允许；等待命令无法绕过授权。
- 如果 `tcpip` 后 USB 不再出现且没有已知 IP，脚本无法自动获知地址；它会限时退出并说明下一步。
- TCP ADB 能在 USB 通道消失后继续工作，前提是 `adbd` 仍运行、Wi-Fi 可达、授权有效。它不能保证厂商永不关闭整个 `adbd`，也不保证跨重启保留。
- 本次修改进行了 Bash 语法检查；实际状态以日志中的连接结果为准。

## 原生能力依据

- [ADB 命令前缀处理](https://android.googlesource.com/platform/system/core/+/android-9.0.0_r1/adb/commandline.cpp)：支持 `wait-for-*` 后直接执行 `tcpip`。
- [Android 9 TCP 模式服务](https://android.googlesource.com/platform/system/core/+/android-9.0.0_r1/adb/services.cpp)：设置 `service.adb.tcp.port` 并触发 `adbd` 重启。
- [Android 9 adbd 初始化](https://android.googlesource.com/platform/system/core/+/android-9.0.0_r1/adb/daemon/main.cpp)：USB 与 TCP 通道的初始化。
