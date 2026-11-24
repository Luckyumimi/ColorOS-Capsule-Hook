# ColorOS 流体云 Hook

简体中文 · [English](docs/README.en.md) · [日本語](docs/README.ja.md)

从 [Color lyric](https://github.com/Luckyumimi/Color-lyric) 整理出的 ColorOS 17 流体云 Hook 示例。使用现代 Xposed API 102，演示如何定位媒体胶囊、调整长度、叠加背景色，并在右下角绘制绿色标记。

## 构建与查看效果

需要 JDK 25、Android SDK Platform 37（本地目录可能显示为 `android-37.0`），以及支持 API 102 的 LSPosed 兼容框架。Gradle 使用本仓库的 Wrapper。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

macOS / Linux 使用 `./gradlew :app:testDebugUnitTest :app:assembleDebug`。安装 `app/build/outputs/apk/debug/app-debug.apk`，在框架管理器中启用模块，作用域选「系统界面」(`com.android.systemui`)。重启后播放音乐，查看媒体流体云。已有媒体通知时，重新播放一次来刷新通知。

## Hook 链路

| 步骤 | 示例怎么做 |
| --- | --- |
| 进入 SystemUI | `onModuleLoaded` 检查进程；`onPackageReady` 再检查包名和首次加载。入口、静态作用域及 API 版本在 [`META-INF/xposed`](app/src/main/resources/META-INF/xposed/) 中。 |
| 找到插件 | 在 `CapsulePluginContainerController` 的宿主视图下查找唯一、已附着的 `CapsuleContainerRoot`，用根视图的插件 ClassLoader 查找 `CapsuleView`。 |
| 确认媒体 | 优先观察 `sendLiveAlertData(String)`；入口不可用时退到 `sendDataToPlugin(String, StatusBarNotification)`。只处理成功发布的 SystemUI 通知，核对胶囊卡片、活跃播放器 ID、媒体行和 `MediaController` 包名。没有活跃 ID 时只接受唯一卡片。 |
| 找到胶囊并绘制 | 在当前插件容器内找唯一可见、已附着、`getViewItem()` 中含完全匹配通知键的 `CapsuleView`。`onDraw(Canvas)` 先执行原方法，再核对实际接收者和视图归属，最后叠加背景和标记。 |
| 恢复原样 | 播放器释放或媒体取消时清除绑定、恢复宽度并重绘。宿主分离时移除观察器；插件移除或更换时卸载绘制 Hook。 |

入口与生命周期见 [`CapsuleHookModule.kt`](app/src/main/java/io/github/luckyumimi/capsulehook/CapsuleHookModule.kt)，签名和视图探测见 [`NativeCapsule.kt`](app/src/main/java/io/github/luckyumimi/capsulehook/NativeCapsule.kt)。

## 长度与背景示例

在 [`CapsuleAppearance.kt`](app/src/main/java/io/github/luckyumimi/capsulehook/CapsuleAppearance.kt) 改 `demoWidthDp`（默认 **170 dp**）和 `demoBackgroundArgb`（默认半透明深青色 `0xA3336761`）。任一项设为 `null` 并重新构建，即可单独关闭该项。

- **长度：** 绘制前重新确认媒体归属。`getShowingKeyList()` 确认容器只显示这张媒体卡片，且 `getWidthChanging()` 返回 `false` 时，设置 `LayoutParams.width`。解绑时恢复模块写入的宽度，保留系统后续的改动。
- **背景：** 原生 `onDraw` 返回后、子控件绘制前，叠一层圆角半透明色。布局稳定时使用视图宽度，动画期间用 `getCapsuleDrawableWidth()` 的宽度按原生中心绘制。解绑重绘时移除叠层。

## 在此基础上绘制歌词

1. **定位原生控件。** 用资源名 `left_section`、`right_section`、`img_primary`、`fl_title/title`、`fl_content/content` 查找控件，检查唯一性和父子关系。修改可见性、最小宽度或布局参数时保存原值，停用或解绑时恢复。
2. **处理前摄与转场。** 把显示屏切口换算为胶囊局部坐标，将同一条歌词裁成左右两段。展开和收起时，复用背景示例的动画边界裁切歌词。
3. **同步播放进度。** 切换播放器时重新匹配通知键、会话和播放器包名。监听匹配会话的元数据和播放状态，用播放时钟排程；拖动进度、暂停或切歌后重新读取状态。

这些做法的完整实现见 Color lyric 的 [SystemUI 源码](https://github.com/Luckyumimi/Color-lyric/tree/main/app/src/main/java/shop/terramoor/github/io/systemui)。

## 调试与排查

在 Logcat 中按 `ColorOSCapsuleHook` 查看宿主与插件的 Hook 安装状态。提交问题时附上机型、固件、SystemUI 版本、插件版本和复现步骤。

单测覆盖媒体卡片匹配、原生宽度恢复和背景动画边界。上机检查切换播放器、暂停、胶囊开合、多卡并存和停用恢复。

## 许可证

[Apache-2.0](LICENSE)。编译依赖：[libxposed API](https://github.com/libxposed/api)。
