# ADB Mic Recorder

通过 ADB 命令控制的 Android 录音应用。

## 功能

- 通过 ADB 广播命令控制录音的开始/停止
- 录音保存为 WAV 格式（44.1kHz, 16bit, Mono）
- 前台服务确保后台录音不被杀掉
- 支持 GUI 界面和纯 ADB 命令两种操作方式

## 使用方法

### 安装 APK

1. 从 [GitHub Releases](../../releases) 下载 APK
2. 通过 ADB 安装：
   ```bash
   adb install adb-mic-recorder.apk
   ```

### 授予权限

首次运行需要在手机上授予麦克风权限：
```bash
# 打开应用界面
adb shell am start -n com.adbmic/.MainActivity
```

### ADB 命令控制

```bash
# 开始录音
adb shell am broadcast -a com.adbmic.START

# 停止录音
adb shell am broadcast -a com.adbmic.STOP

# 查看状态和文件列表
adb shell am broadcast -a com.adbmic.STATUS
```

### 拉取录音文件

```bash
# 拉取所有录音到当前目录
adb pull /sdcard/Download/ ./

# 或者只拉取特定文件
adb pull /sdcard/Download/adb_rec_20260608_143000.wav ./
```

### 查看状态文件

```bash
# 读取状态
adb shell cat /sdcard/Download/adb_mic_status.txt
```

## 技术细节

- **格式**: WAV (PCM 16bit)
- **采样率**: 44100 Hz
- **声道**: 单声道 (Mono)
- **文件大小**: 约 8.4 MB/分钟
- **保存位置**: `/sdcard/Download/adb_rec_*.wav`

## 权限说明

| 权限 | 用途 |
|------|------|
| `RECORD_AUDIO` | 麦克风录音 |
| `FOREGROUND_SERVICE` | 后台录音服务 |
| `POST_NOTIFICATIONS` | 录音通知 |

## 自行编译

1. Fork 本仓库
2. Push 代码到 main 分支
3. 在 Actions 页面查看构建进度
4. 从 Artifacts 下载 APK
