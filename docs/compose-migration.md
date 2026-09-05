# Compose 迁移记录

日期：2026-09-05。当前 XML / Kotlin View UI 已完整替换为 Jetpack Compose，覆盖主壳、资料库、搜索、收藏、播放列表、设置及播放页。迁移以原有已校准实现为视觉与交互基线，保留 Smartisan 资源和本地播放器功能；代码完成与设备验收分别记录。

## 基线与边界

- 用户确认迁移前的实现已完成视觉还原。本次依据当时的代码、资源和既有截图实现，不重新查阅原 APK 或 reverse 材料。
- 保持单 `:app` 模块、namespace、applicationId、Media3 服务、资料库、Room、DataStore 及会话恢复协议。页面消费原有状态、上抛原有事件，不创建第二套播放器或数据源。
- 依赖和 SDK 保持现有工程版本；本次工作范围是 UI 迁移、组件整理、命名和不可达代码/资源清理。
- 保留已有版权和资源来源说明。Compose 页面继续使用自定义 Smartisan 视觉；原版位图、selector 和 9-patch 不因布局技术变化而重新绘制或重新授权。

## 已完成的实现

| 区域 | 当前实现 | 保留的主要行为 |
| --- | --- | --- |
| 主壳与导航 | [ui/shell](../app/src/main/java/com/smartisan/music/ui/shell/) | Tab 切换、底部播放条、导航长按编辑、标题栈、横向子页、纵向覆盖层、预测性返回 |
| 歌曲、收藏与播放列表 | [ui/songs](../app/src/main/java/com/smartisan/music/ui/songs/)、[ui/loved](../app/src/main/java/com/smartisan/music/ui/loved/)、[ui/playlist](../app/src/main/java/com/smartisan/music/ui/playlist/) | 排序分组、多选滑选、边缘自动滚动、滑动删除、播放列表管理与歌曲操作 |
| 专辑与艺术家 | [ui/album](../app/src/main/java/com/smartisan/music/ui/album/)、[ui/artist](../app/src/main/java/com/smartisan/music/ui/artist/)、[ui/library](../app/src/main/java/com/smartisan/music/ui/library/) | 列表/网格、封面切换轨迹、歌曲详情、艺术家子页与播放顺序 |
| 文件夹与流派 | [ui/folder](../app/src/main/java/com/smartisan/music/ui/folder/)、[ui/genre](../app/src/main/java/com/smartisan/music/ui/genre/) | 文件夹隐藏/显示、刷新、系统授权删除、详情播放与空态 |
| 搜索、更多与设置 | [ui/search](../app/src/main/java/com/smartisan/music/ui/search/)、[ui/more](../app/src/main/java/com/smartisan/music/ui/more/)、[ui/settings](../app/src/main/java/com/smartisan/music/ui/settings/) | 搜索历史和分组结果、动态入口、设置页栈、主题/图标选择及现有设置存储 |
| 播放页 | [ui/playback](../app/src/main/java/com/smartisan/music/ui/playback/) | 黑胶/歌词舞台、唱针、搓碟、爆豆音、进度/音量、队列重排、评分、音效和睡眠滚轮 |
| 封面与公共组件 | [ui/artwork](../app/src/main/java/com/smartisan/music/ui/artwork/)、[ui/components](../app/src/main/java/com/smartisan/music/ui/components/) | 共享封面缓存、标题栏、系统点击音、弹层、开关、滚动条、资源状态和字体指标 |

字母快捷栏保留竖向 A–Z/# 定位和可横向拉出的字母网格，沿用原拖动判定、释放方向与动画。短屏压缩只改变显示字母，触摸仍能定位全部分组；修复了原实现极短高度下步长溢出的问题。

资源 Painter 在配置变化时重新解析资源，维护 Drawable 状态和回调，保留 ColorStateList、9-patch 拉伸区及分组阴影。标题绘制使用公开 `TextPaint` / 文字布局 API 维持原伪粗体和字体基线；睡眠滚轮使用公开 `Scroller` 计算运动。这些对象在 Compose 的绘制和手势流程中工作，不创建 View 页面。

封面加载器保留原受限 LRU 和解析优先级，增加 Compose 使用的缓存读取与 suspend 入口。同一加载器内相同请求合并，已有缓存先显示，解析在 IO 线程运行，所属页面离开后取消任务。

## 包整理与清理

41 个文件从主壳目录归入对应功能包，`MusicAppShell` 保留跨页面编排；页面、类型和函数移除 `Legacy` / `LegacyPort` 前缀。当前职责与规则见 [UI 架构](ui-architecture.md)。

旧的 40 个布局 XML、生产 UI View 控件、adapter 和 framework shim 已删除，`res/anim` 中退休的 View 动画也已清理。保留的 XML 用于图形、主题、尺寸、颜色和字符串，不再承担页面布局。滚动条的 `declare-styleable` 只读取 framework 主题属性，由 AAPT 生成属性索引。UI 测试中的原生标题栏和 Drawable View 只作为参考对照，不进入生产代码。

资源清理以 main/test/androidTest 的 `R` 引用、Manifest、全部配置下的 XML 传递依赖及生成脚本为根。两轮共删除 256 个不可达资源名，对应 165 个独立文件和 179 条 values 配置声明；夜间生成脚本同步删除 21 条退休规则，保留剩余 153 组映射、6 个深色开关生成源及全部音频素材。清理覆盖同名配置变体，未以 Lint 旧报告作为唯一依据。

动态弹簧库只由标题栏参考测试直接使用，已移入 `androidTestImplementation`；应用对 Compose 间接引入的版本保留约束，确保测试与宿主解析一致。依赖版本未升级。采用 [Gradle 官方依赖约束](https://docs.gradle.org/current/userguide/dependency_constraints.html)（2026-09-05 查阅），约束本身不额外引入模块。

## 验证与待验收

最终自动检查命令：

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug assembleRelease assembleDebugAndroidTest
git diff --check
```

`assembleRelease` 包含代码与资源收缩检查。`assembleDebugAndroidTest` 只构建设备测试 APK，不执行 instrumentation。首次交付只完成构建验证；用户报告真机布局回归并授权设备操作后，补充了设备测试，见下方交付记录。未实际运行 Android Studio Preview。

测试位于 [本地单元测试](../app/src/test/java/com/smartisan/music/) 和 [UI instrumentation](../app/src/androidTest/java/com/smartisan/music/ui/)。迁移相关覆盖包括快捷栏几何、滑选区间恢复、滑删方向、滚动条与拖拽计算、页面事件/外部状态更新、隐藏页面、标题栏，以及 selector / ColorStateList / 9-patch 对照。像素测试使用原生 Drawable 或专用参考标题实现；小容差仅用于栅格化与取整差异，不能代替设备视觉验收。

`connectedDebugAndroidTest` 仅用于允许清空的测试设备或模拟器：本项目当前 Gradle 设备测试流程会在结束时卸载目标应用，不能直接用于保有用户数据的日用设备。

已备份的开发设备可使用保留安装与直接 instrumentation 命令，避免 Gradle 的卸载清理。测试会接管应用前台并中断其当前运行；用例使用合成状态，不修改媒体库、收藏或播放列表。

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w app.smartisanmusic.revived.test/androidx.test.runner.AndroidJUnitRunner
```

## 真机布局回归修复

2026-09-05，真机出现播放条与导航栏堆叠在屏幕顶部、页面空白的回归。界面树确认列表实际存在，但底栏背景覆盖了内容。根因是 `Modifier.paint(sizeToIntrinsics = false)` 仍参与测量：在宽高均有上界的父约束中，它会将最小尺寸扩到最大尺寸，导致底部自适应高度的容器占满屏幕。

生产背景统一改为 `smartisanPainterBackground`，通过 `drawBehind` 在测量完成后绘制 Drawable，保持 selector、9-patch 和配置变化行为。纯背景不再决定布局尺寸；没有播放条时的顶部阴影显式使用资源固有高度，避免 1×1 资源被 `FillWidth` 放大成正方形。未以固定底栏高度掩盖父子约束问题。

新增 3 项设备回归测试，覆盖 220dp / 460dp 的有界父容器、自适应背景高度，以及有/无播放条时的真实底部组件尺寸、位置与未被覆盖的页面像素。修复版在同一真机上确认标题栏、列表、播放条和导航栏恢复正确位置。全量像素与交互验收仍按下表逐项进行。

设备执行还纠正了两类参考测试问题：原生 `View` 的点击回调通过消息队列执行，需要等待空闲后比较；标题按钮现在验证四边内外共 9 个触点。九宫格的像素参考改为同设备上独立原生 View 的硬件绘制，避免软件 Bitmap Canvas 在非整数密度下的边缘栅格差异；参考 View 用明确裁剪与矩形白底限制绘制范围，防止 `drawColor` 干扰相邻 Compose 样本。逐像素容差保持 3/255，未修改生产标题触摸范围或 Painter 绘制算法。另增加真实播放条测试，覆盖封面/文本打开事件、重组后的最新回调、播放按钮事件隔离和阴影区域不透传。

关键设备回归：

| 范围 | 待验收内容 |
| --- | --- |
| 导航与页面栈 | 快速切 Tab、长按导航排序/固定、更多入口更新、子页进退、返回手势取消/完成、播放页展开/收起与返回后的滚动位置 |
| 歌曲与快捷栏 | 排序/分组切换、竖向字母定位、横向拉出和收起网格、短屏字母压缩、多选范围回退、跨标题/页脚自动滚动、滑删与垂直滚动仲裁 |
| 专辑与艺术家 | 列表/网格反复切换、半行滚动下封面飞行轨迹、封面放大/收起、缺失或失效封面、艺术家全部歌曲/专辑页栈和播放顺序 |
| 文件夹与流派 | 首次加载、空态、隐藏/显示眼睛动画、编辑态进退、排除项持久化、刷新、系统删除确认/取消及权限撤销 |
| 收藏、播放列表与搜索 | 收藏更新、列表创建/重命名/删除、添加/移除歌曲、多选和排序、搜索输入/清除/历史、结果详情及 IME 避让 |
| 播放与队列 | 播放/暂停/切歌、随机/循环、seek、队列展开及跨屏拖拽、当前项标记、队列更新后索引和持久化一致性 |
| 唱盘与弹层 | 唱针落点、拖动/取消、搓碟方向和触觉、爆豆音、歌词/控制区切换、音量滑块、评分、音效曲线、睡眠滚轮惯性/吸附及快速关闭 |
| 设置与恢复 | 图标切换成功/失败、重复点击、主题切换/系统日夜变化、应用重建后已有收藏/播放列表/设置/队列和进度仍可读 |
| 视觉与系统边界 | 中英文及 RTL、长标题和省略、字体缩放、日夜按压/焦点/禁用状态、阴影、滚动裁剪、系统动画关闭、手势/三键导航、刘海、横竖向或多窗口尺寸变化 |
| 后台与外部入口 | 锁屏/通知、耳机/蓝牙、前后台切换、进程恢复、外部音频 URI 授权、失效文件及音频分享 |

现有 README 截图属于迁移前经过校准的版本。迁移代码完成不代表已获得 Compose 版本的 1:1 真机验收；听感、触觉和设备图像仍需实际反馈。

## 交付记录

2026-09-05 完成主机自动检查，并在用户授权的 Android 16 真机上使用直接 instrumentation 执行完整设备测试，结果如下：

| 检查 | 结果 |
| --- | --- |
| `testDebugUnitTest` | 171 项通过，0 失败、0 错误、0 跳过 |
| `assembleDebug` | 通过 |
| `assembleRelease` | 通过，包括 R8、Compose 映射及资源收缩 |
| `lintDebug` | 0 错误，62 条警告、2 条提示 |
| `assembleDebugAndroidTest` | 通过；APK 包含 37 项 instrumentation 用例 |
| 真机 `am instrument -w` | 37 项全部通过，耗时 41.333 秒；包括背景约束、实际播放条点击、标题/Drawable 像素、拖拽、开关、设置状态及内存收藏仓库测试 |
| `git diff --check` | 通过 |
| 静态引用检查 | 生产页面无 `AndroidView`、`LayoutInflater`、`R.layout` 或旧 View 控件；40 个页面布局 XML 全部移除 |

剩余 Lint 项涉及版本/target 更新建议、已有 Manifest 声明、像素与图片配置、KTX 建议及两个原有状态装箱提示。6 个未直接引用的开关位图仍是夜间资源生成脚本的必要输入。没有通过禁用检查掩盖 Compose 问题；原 `AppCompatCustomView` 全局豁免已移除。

本轮真机界面检查确认歌曲页和艺术家页列表、更多与设置入口恢复布局；设置输入弹窗可避开键盘。截图与界面树仅留作本机临时诊断，不将设备上的实际媒体信息写入仓库。37 项设备测试全部执行通过，参考测试使用合成数据；这不等同于上方全部页面、听感、触觉和设备组合已完成 1:1 验收。

## 官方资料

以下资料均于 **2026-09-05** 查阅；采用当前依赖支持的 API，现有 Smartisan 样式和行为由当前项目基线决定。

- [迁移策略](https://developer.android.com/develop/ui/compose/migrate/strategy)：建立共用组件并替换页面。
- [自定义设计系统](https://developer.android.com/develop/ui/compose/designsystems/custom)：Compose 中实现自有视觉语言。
- [Compose 资源](https://developer.android.com/develop/ui/compose/resources)：核对 Drawable 支持范围与 ColorStateList 处理。
- [Insets](https://developer.android.com/develop/ui/compose/system/insets-ui)：保持窗口和系统栏所有权，避免重复避让。
- [文字段落样式](https://developer.android.com/develop/ui/compose/text/style-paragraph)：font padding、单行和省略。
- [交互状态](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions)：使用 InteractionSource 表达按压和焦点。
- [状态对象生命周期](https://developer.android.com/develop/ui/compose/state-callbacks)：资源 owner 与 Composition 生命周期。
- [Compose 测试](https://developer.android.com/develop/ui/compose/testing) 与 [v2 测试迁移](https://developer.android.com/develop/ui/compose/testing/migrate-v2)：使用现有依赖中的调度和同步接口。
- [Lazy lists and grids](https://developer.android.com/develop/ui/compose/lists)：稳定项键与列表/网格状态。
- [Gesture input and consumption](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures)：手势所有权和事件消费。
- [Graphics in Compose](https://developer.android.com/develop/ui/compose/graphics/draw/overview)：绘制阶段与公开 Canvas 接口。
- [Graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers) 与 [Constraints and modifier order](https://developer.android.com/develop/ui/compose/layouts/constraints-modifiers)：背景绘制和布局测量分离，核对当前 Compose 源码中的 `PainterModifier.modifyConstraints`。
- [Test from the command line](https://developer.android.com/studio/test/command-line)：直接使用 `am instrument` 运行设备测试及读取测试结果。
