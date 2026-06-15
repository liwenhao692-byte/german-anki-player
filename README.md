# 德语 Anki 播放器 APK

这是一个原生 Android 播放器，用来解决网页端息屏后停止朗读的问题。

核心实现：

- Android 前台媒体服务
- 通知栏播放控制
- MediaSession 支持上一张 / 下一张 / 暂停 / 继续
- 息屏后按系统媒体播放处理
- 德语和中文通过在线 TTS URL 播放，所以播放时需要联网

## APK 下载

打开仓库的 **Actions** 页面，点最新一次 `Build Debug APK`，在页面底部下载 artifact：

`german-anki-player-debug-apk`

里面会有：

`app-debug.apk`

手机下载安装即可。首次安装需要允许“安装未知来源应用”。

## 使用

打开 App 后设置：

- 德语读几遍
- 中文读几遍
- 播放速度
- 卡片间隔
- 从第几张开始

点“开始息屏播放”。

然后可以息屏。通知栏里可以暂停、继续、上一张、下一张、停止。

## 重要说明

当前仓库已经放入原生播放器核心和测试卡片数据。后续可以把完整 3000 张卡片替换到：

`app/src/main/assets/cards.tsv`

格式是：

```text
序号<TAB>德语<TAB>中文<TAB>章节
```

每行一张卡。

## 安卓设置

为了息屏后继续播放，建议：

1. 允许通知权限。
2. 在系统设置里把本 App 的电池优化设为“不限制”。
3. 播放时保持联网。

Build trigger: 2026-06-15-system-tts-screen-off-interval-fix
