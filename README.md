# ColorOS 流体云 Hook

简体中文 · [English](docs/README.en.md) · [日本語](docs/README.ja.md)

从 [Color lyric](https://github.com/Luckyumimi/Color-lyric) 整理出的 ColorOS 17 流体云媒体胶囊开发示例。使用现代 Xposed API 102，仅在已确认归属的媒体胶囊右下角画一个绿色小圆点。不替换原生布局，不包含歌词、封面取色或设置界面。

## 构建与查看效果

需要 JDK 25、Android SDK Platform 37（本地目录可能显示为 `android-37.0`），以及支持 API 102 的 LSPosed 兼容框架。Gradle 使用本仓库的 Wrapper。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

macOS / Linux 使用 `./gradlew :app:testDebugUnitTest :app:assembleDebug`。APK 在 `app/build/outputs/apk/debug/app-debug.apk`。安装后在框架管理器中启用模块，作用域选「系统界面」(`com.android.systemui`)，重启系统后播放音乐并让原生媒体流体云出现。如果媒体通知已在模块加载前发布，可重新开始一次播放。本示例没有桌面图标。

## Hook 链路

| 步骤 | 示例怎么做 |
| --- | --- |
| 进入 SystemUI | `onModuleLoaded` 检查进程；`onPackageReady` 再检查包名和首次加载。入口、静态作用域及 API 版本在 [`META-INF/xposed`](app/src/main/resources/META-INF/xposed/) 中。 |
| 找到插件 | 在 `CapsulePluginContainerController` 的宿主视图下查找唯一、已附着的 `CapsuleContainerRoot`。通过这个根视图拿插件 ClassLoader，不能直接用 SystemUI 的 ClassLoader 查 `CapsuleView`。 |
| 确认媒体 | 优先观察 `sendLiveAlertData(String)`；入口不可用时退到 `sendDataToPlugin(String, StatusBarNotification)`。只处理成功发布的 SystemUI 通知，核对胶囊卡片、活跃播放器 ID、媒体行和 `MediaController` 包名。没有活跃 ID 时只接受唯一卡片。 |
| 找到胶囊并绘制 | 在当前插件容器内找唯一可见、已附着、`getViewItem()` 中含完全匹配通知键的 `CapsuleView`。`onDraw(Canvas)` 先执行原方法，再核对实际接收者和视图归属，最后画标记。能检测到原生宽度动画时跳过该帧。 |
| 恢复原样 | 播放器释放或媒体取消时清除绑定并重绘。宿主分离时移除观察器；插件移除或更换时卸载绘制 Hook。契约不匹配时保留原生界面。 |

入口与生命周期见 [`CapsuleHookModule.kt`](app/src/main/java/io/github/luckyumimi/capsulehook/CapsuleHookModule.kt)，签名和视图探测见 [`NativeCapsule.kt`](app/src/main/java/io/github/luckyumimi/capsulehook/NativeCapsule.kt)。

## 在此基础上绘制歌词

1. **定位原生控件。** 在已验证的胶囊内按资源名查 `left_section`、`right_section`、`img_primary`、`fl_title/title`、`fl_content/content`，验证唯一性及父子关系。不要写死编译后的资源 ID。改动可见性、最小宽度或布局参数时保存原值，停用或解绑时恢复。
2. **处理前摄与转场。** 将显示屏切口换算为胶囊局部坐标，把同一条歌词裁成左右两段，避免重复绘制。要让覆盖层跟随 ColorOS 的展开与收起，按 `getWidthChanging()` 和 `getCapsuleDrawableWidth()` 的原生宽度裁切，不把动画中间帧当成稳定布局宽度。示例只展示最小绘制，不接管动画。
3. **核对播放进度。** 媒体切换时重新匹配通知键、会话和播放器，不按可见文字或图标猜测归属。需要歌词时监听已验证会话的元数据和播放状态，使用媒体播放时钟排程；拖动进度、暂停、切歌都要重取状态。

这些做法的完整实现见 Color lyric 的 [SystemUI 源码](https://github.com/Luckyumimi/Color-lyric/tree/main/app/src/main/java/shop/terramoor/github/io/systemui)。

## 适配与排查

类名、方法和视图结构来自 Color lyric 已适配的 ColorOS 17 路径，不是公开 Android 接口，不代表所有固件都兼容。以目标设备上实际的 SystemUI 与插件版本为准。可在 Logcat 中按 `ColorOSCapsuleHook` 查看宿主和插件的安装状态；反馈时注明机型、固件、SystemUI / 插件版本及复现步骤，不提供通知键、会话令牌或系统 APK。

本地单测覆盖媒体卡片唯一性、缺失归属和活跃播放器的精确匹配；不覆盖真机的 OEM Hook。上机时建议同时检查切换播放器、暂停、胶囊收起、非媒体卡片及禁用模块后的恢复。运行时逻辑在本地执行，不请求网络或模型，也不记录歌词或通知内容。

## 许可证

[Apache-2.0](LICENSE)。示例从 [Color lyric](https://github.com/Luckyumimi/Color-lyric) 的实现改写而来，使用 [libxposed API](https://github.com/libxposed/api) 作为仅编译依赖。仓库不分发 ColorOS 系统文件。
