# RGB WS2812 Controller Android

这是一个原生 Android 控制 App，用经典蓝牙 SPP 串口向 FPGA WS2812 彩灯板发送可变长度二进制控制帧。

## 功能

- Kotlin + Jetpack Compose + Material 3 界面。
- 支持静态、流水、呼吸、Disco、渐变、流动渐变 6 种模式。
- 支持 RGB、亮度、呼吸周期、基础流水顺序和高级流水画面编辑。
- 支持已配对设备列表、蓝牙扫描发现、SPP 连接和发送。
- 支持 Hex 预览、复制、手动 Hex 校验发送。
- 支持预设、发送历史、JSON 导入导出。
- 默认手动发送，可开启自动发送。

## 协议

每次发送完整 `11~18` 字节二进制帧，不发送 ASCII Hex：

```text
AA 55 mode R G B brightness period flow_count frame0 ... frameN checksum
```

`checksum` 是从 `mode` 到最后一个 `frame` 逐字节 XOR。`flow_count` 为 `1..8`，每个 `frame` 是 1 字节灯掩码，bit0~bit7 对应 LED0~LED7。基础流水会自动转换成单 bit 画面，高级流水允许一个画面同时点亮多颗灯。

## 构建

当前工程使用：

- Android Gradle Plugin 8.13.0
- Kotlin Gradle Plugin 1.9.20
- compile SDK `android-36.1`
- min SDK 23

在 Android Studio 中打开仓库并同步 Gradle。命令行可使用本机 Gradle 分发：

```powershell
C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat test assembleDebug
```

如果首次同步缺少 Compose 或 AndroidX 依赖，需要允许 Gradle 从 `google()` 和 `mavenCentral()` 下载依赖。

## 使用

1. 先在系统蓝牙中打开蓝牙，并尽量完成 HC-05/HC-06 等经典蓝牙串口模块配对。
2. 打开 App，授予蓝牙扫描和连接权限。
3. 在“蓝牙设备”中刷新已配对设备或扫描发现设备。
4. 连接设备后调整参数，点击“发送当前帧”。
5. 如需连续调试，可打开“自动发送”。
