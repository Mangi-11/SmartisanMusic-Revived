<p align="center">
  <img src="app/src/main/res/mipmap-xxhdpi/ic_launcher.png" width="112" alt="锤子音乐图标" />
</p>

<h1 align="center">锤子音乐复刻 (Smartisan Music Revived)</h1>

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin" alt="Kotlin 2.4.0" /></a>
  <a href="https://developer.android.com/build"><img src="https://img.shields.io/badge/AGP-9.2.1-3DDC84?logo=android" alt="AGP 9.2.1" /></a>
  <a href="https://developer.android.com/about/versions/oreo/android-8.1"><img src="https://img.shields.io/badge/minSdk-27-3DDC84?logo=android" alt="minSdk 27" /></a>
  <a href="https://developer.android.com/media/media3"><img src="https://img.shields.io/badge/Media3-1.10.1-4285F4?logo=android" alt="Media3 1.10.1" /></a>
</p>

<p align="center">
  中文 · <a href="README_EN.md">English</a>
</p>

锤子科技从来不只把自己看作一家做视觉的公司。好看的界面只是起点，产品最终仍要解决真实问题；设计、功能和交互应当保持一致，让第一次使用的人不费力，也让长期使用的人偶尔发现藏在细节里的惊喜。Smartisan 这个名字所代表的“智能时代的工匠”，不是为产品贴上一层漂亮的皮肤，而是认真对待每一次触摸、反馈与停顿。

锤子音乐是这种理念很完整的一次表达。黑胶唱盘、唱针、搓碟和爆豆音让数字音乐重新变成可以触碰的东西，歌曲、专辑和资料库又始终保持清楚、克制。它有拟物的趣味，却不该为了表演牺牲播放和管理；真正值得保留的，正是质感、秩序与实用性之间的平衡。

Smartisan OS 已经退出历史舞台，本项目因此以锤子音乐 8.1.0 为视觉与交互基准，使用现代 Android 技术栈重新实现这款本地音乐播放器。界面尽可能保留原版 XML、Drawable、NinePatch、Selector、动画节奏与控件层级，媒体扫描、后台播放、队列、收藏、播放列表和数据持久化则全部基于公开 Android API 重写。应用只读取和播放设备上的音频文件，不内置内容曲库、账号体系或媒体分发服务。

## 相较原版的改进

- **现代本地播放架构**：使用 Media3 `MediaLibraryService`、ExoPlayer 与 MediaSession 重建播放链路，支持后台与锁屏播放、媒体通知、耳机和蓝牙控制，以及进程重启后的队列与进度恢复。
- **重新实现本地资料库**：通过 MediaStore 扫描歌曲，并按歌曲、专辑、艺术家、流派和文件夹浏览；支持资料库排除、重新扫描、排序筛选和字母快捷栏。
- **重建收藏与播放列表**：保留原版已有的收藏和自建播放列表，并使用 Room 重新实现持久化与播放统计；队列、当前歌曲及播放位置也会保存和恢复。
- **扩展播放页能力**：在原版唱盘与控制区基础上加入音频文件内嵌歌词、睡眠定时和可拖拽播放队列。
- **新增个性化设置**：加入艺术家分隔符、可排序及固定的底部导航、可切换应用图标和简易音效；除原版图标外，还保留了 realme UI 7.0 音乐的黄色黑胶图标，并按 Android adaptive icon 规范接入彩色与单色图层。音效提供几种预设及自定义均衡曲线。
- **打磨唱盘交互**：保留唱针拖拽、唱片转动、搓碟、爆豆音和播放状态联动，并针对现代触摸事件、生命周期与帧时钟重新实现。
- **增强资料库操作**：支持多选、滑动操作、加入播放列表、设置铃声和按系统版本授权的 MediaStore 媒体删除；也可以从文件管理器或其他应用直接打开音频。
- **适配 Android 8.1 及以上版本**：为新旧存储模型、系统栏、手势导航、刘海、WindowInsets 和预测性返回分别提供兼容路径，同时保持原版页面比例与视觉语言。
- **现代数据架构**：使用 Room、DataStore、Coroutines 和 StateFlow 管理资料库、收藏、播放列表、设置及播放状态，不依赖 Smartisan OS 私有服务或系统签名能力。
- **清理历史包袱**：业务源码全部使用 Kotlin，只保留当前实现需要的资源和公开 API，不携带原版后台服务、旧数据库或旧设置迁移代码。

## 当前功能

- 本地音乐授权、扫描、重新索引及资料库目录排除
- 歌曲、专辑、艺术家、流派和文件夹浏览
- 排序筛选、字母快捷栏、多选与滑动操作
- 收藏歌曲、自建播放列表和播放统计
- 后台播放、媒体通知、耳机与蓝牙媒体控制
- 顺序、随机、单曲循环和列表循环播放
- 播放队列展开、拖拽排序，以及队列和进度恢复
- 黑胶唱盘、唱针拖拽、搓碟与爆豆音
- 音频文件内嵌的静态、逐行及逐字歌词
- 原声、低音、清澈、人声、摇滚与自定义音效
- 睡眠定时、系统音量控制和设置手机铃声
- 外部音频打开和 MediaStore 媒体删除
- 自定义艺术家分隔符、底部导航顺序与固定项，以及应用图标

## 本地媒体与权限

应用的最终 Manifest 不包含 `INTERNET` 权限，运行时不依赖网络，也不会上传歌曲、封面、歌词或资料库信息。

- Android 13 及以上使用 `READ_MEDIA_AUDIO` 读取设备音频；Android 8.1 至 Android 12 使用受版本限制的 `READ_EXTERNAL_STORAGE`。
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 仅用于用户播放音乐时维持后台播放和媒体通知。
- `MODIFY_AUDIO_SETTINGS` 用于播放音效与系统音乐音量控制，`VIBRATE` 用于交互触觉反馈。
- `WRITE_SETTINGS` 只在用户主动选择“设置铃声”时使用；应用会先跳转系统授权页面，未获得用户明确授权时不会修改系统设置。
- 删除歌曲时，Android 11 及以上使用系统批量确认，Android 10 使用单文件授权；Android 8.1 和 Android 9 仅在用户确认删除后申请 `WRITE_EXTERNAL_STORAGE`，再通过 MediaStore 执行删除。
- 应用不申请定位、相机、麦克风、通讯录、短信、悬浮窗或无障碍权限。

## 真机截图

<p align="center">
  <img src="docs/images/screenshot-playback.jpg" width="200" alt="锤子音乐播放页面" />
  <img src="docs/images/screenshot-lyrics.jpg" width="200" alt="锤子音乐歌词页面" />
  <img src="docs/images/screenshot-albums.jpg" width="200" alt="锤子音乐专辑页面" />
</p>

截图中展示的专辑封面、艺人信息和音乐内容版权归原权利人所有，仅用于展示应用界面效果。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 构建 | Android Gradle Plugin `9.2.1`、Gradle `9.4.1`、JDK 21（Java 11 字节码） |
| 语言 | Kotlin `2.4.0` |
| UI | XML Layout、Android View、自定义 View、Jetpack Compose |
| 播放 | Media3 `1.10.1`、ExoPlayer、MediaLibraryService、MediaSession |
| 状态 | Lifecycle、StateFlow、Coroutines |
| 存储 | Room `2.8.4`、DataStore `1.2.1`、MediaStore |
| SDK | `minSdk 27` / `targetSdk 36` / `compileSdk 37` |

## 构建

准备 JDK 21 和 Android SDK，然后执行：

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/`。

如需验证经过代码与资源压缩的发布构建，执行：

```bash
./gradlew assembleRelease
```

Release APK 位于 `app/build/outputs/apk/release/SmartisanMusic-Revived-0.1.0.apk`。

## 致谢

感谢 [People-11](https://github.com/People-11/) 的 [SmartisanOS_APP_Port](https://github.com/People-11/SmartisanOS_APP_Port/) 移植工作。本项目使用该项目提供的 `Music_8.1.0.apk` 进行逆向分析，用于确认原版资源、页面层级、视觉细节、动画时序与交互行为。

People-11 的工作让原版应用能够在非 Smartisan 设备上继续运行；本项目则重新实现媒体扫描、播放服务、资料库、队列和数据存储，在保留原版设计语言的同时使用现代公开 Android API 承担系统能力。

## 免责声明

本项目与字节跳动、锤子科技、realme、OPPO 及相关产品的任何权利方无关，仅为个人兴趣驱动的非官方复刻。

- Smartisan OS、相关商标、视觉设计及原版素材的知识产权归原权利人所有。
- 可选的黄色黑胶图标取自 realme UI 7.0 默认 UXIcon 资源，仅作视觉保存与致敬；相关图形、商标及素材权利归原权利人所有，不随本项目许可证重新授权。来源与文件摘要见 `THIRD_PARTY_NOTICES.md`。
- 本项目不提供音乐内容；用户应确保设备中音频文件的来源与使用方式符合所在地法律及权利人要求。
