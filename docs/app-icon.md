# 应用图标切换与 Adaptive Icon 约定

## 设备侧证据

查阅日期：2026-07-19。

- 测试设备为 realme RMX5200，用户界面显示 realme UI `7.0` / Android 16，软件版本为 `RMX5200_16.0.8.301(CN01)`。设备属性 `ro.build.version.oplusrom=V16.1.0` 是底层 OPlus ROM 标识，不是 realme UI 的产品版本。
- 设备内 `com.heytap.music` 版本为 `40.11.16.22.2`（versionCode `191116222`）。其 APK launcher 入口仍声明红色音符 PNG，并没有提供截图中的黄色黑胶 adaptive icon。
- `cmd overlay lookup` 也将 `com.heytap.music:mipmap/ic_launcher` 解析到该 APK 的 `mipmap-xxxhdpi/ic_launcher.png`，说明黄色黑胶不是 Android RRO 对该资源的替换。
- realme UI 7.0 在 `/my_product/media/theme/uxicons/hdpi/com.heytap.music/` 为音乐应用提供默认 UXIcon 图层。截图中的黄色黑胶由 `recbg.png` 和 `recfg.png` 组合，再由 Launcher 应用当前图标蒙版。
- 工程保留这两个 240 × 240 PNG，并使用同目录附带的 `monochrome.png`。来源版本、设备端路径和 SHA-256 记录在 `THIRD_PARTY_NOTICES.md`。
- 用户提供的 2026-07-16 公告称 realme 将按下中国市场的暂停键，现有机型的后续销售与售后由 OPPO 官方团队接手，并将在下一代 ColorOS 发布后转入 ColorOS 更新。设置页以“致敬真我”概括保留这枚图标的缘由。

## 官方规范

2026-07-19 查阅以下 Android 官方资料：

- [Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)：彩色图标使用独立前景和背景，用户主题图标使用 monochrome 层；每层为 `108 × 108dp`，关键标志保持在中央 `66 × 66dp` 安全区内，四边各 `18dp` 留给系统蒙版和动效。
- [`<activity-alias>`](https://developer.android.com/guide/topics/manifest/activity-alias-element)：alias 可拥有独立 launcher intent filter 和图标，且必须声明在目标 Activity 之后。
- [`PackageManager`](https://developer.android.com/reference/android/content/pm/PackageManager#setComponentEnabledSettings(java.util.List%3Candroid.content.pm.PackageManager.ComponentEnabledSetting%3E))：API 33 起可批量、原子地切换多个组件；旧版本使用单组件 API。

黄色背景保持全出血，不预先烘焙圆角、圆形或外框。Realme UXIcon 的唱片前景原始不透明边界约为 `206 × 216px`；在 `108dp` adaptive foreground 中增加 `18dp` 内缩后，唱片约为 `61.8 × 64.8dp`，完整落入中央 `66 × 66dp` 安全区。附带的 monochrome 图层有效边界为 `120 × 120px`，映射后为 `54 × 54dp`，也位于安全区内。因此圆形、圆角矩形、squircle 或其他 OEM 蒙版均由 launcher 正确裁切，而不会出现双重圆角。

资源保留在 `mipmap-anydpi-v26`。虽然工程的 minSdk 已是 27，当前 AAPT2 仍要求 `<adaptive-icon>` 根元素位于 API 26 以上限定目录；移动到无版本限定的 `mipmap-anydpi` 会导致 Manifest 引用在资源链接阶段无法解析。Lint 对该目录的 `ObsoleteSdkInt` 提示因此不适用于这个资源。

## 运行时切换

- `MainActivity` 继续持有 `APP_MUSIC` 和外部音频 `VIEW` intent filter，不会因桌面图标切换而被禁用。
- `OriginalIconAlias` 默认启用并使用原版图标；`ModernIconAlias` 默认停用并使用 realme UI 7.0 黄色黑胶 adaptive icon。为兼容已经选择现代图标的安装，alias 类名保持不变。
- `PackageManager` 的组件状态是唯一持久化事实，不另存 DataStore 值，避免恢复、升级或 launcher 修复后两份状态不一致。
- API 33 及以上原子切换两个 alias；API 27–32 先启用目标 alias，再停用另一个 alias，避免短暂出现没有 launcher 入口的状态。
- 使用 `DONT_KILL_APP`，切换图标不应中断正在播放的 Media3 会话。不同 launcher 的图标缓存刷新时间不一致，设置页会提示可能需要等待片刻。

## 真机回归

- 在原版与真我黑胶图标之间各切换一次，确认桌面始终只有一个入口且都能打开同一播放状态。
- 分别检查圆形、圆角矩形、squircle 等桌面图标形状，确认黄色背景铺满、唱片完整且没有双重蒙版。
- Android 13 及以上启用 themed icons，确认 monochrome 唱片可被系统正确着色。
- 切换时保持音乐播放，确认进程和后台播放未被中断。
- 冷启动、升级安装和 launcher 重启后，确认当前选择仍与桌面图标一致。
