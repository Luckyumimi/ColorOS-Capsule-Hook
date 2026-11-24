# ColorOS Capsule Hook

[简体中文](../README.md) · English · [日本語](README.ja.md)

A ColorOS 17 capsule hook example adapted from [Color lyric](https://github.com/Luckyumimi/Color-lyric). It uses the modern Xposed API 102 to locate the media capsule, set its width, overlay a background tint, and draw a green marker in the lower-right corner.

## Build and try it

Use JDK 25, Android SDK Platform 37 (the directory may be named `android-37.0`), and an LSPosed-compatible framework with API 102 support. Use the included Gradle Wrapper:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

On Windows, run `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug` in PowerShell. Install `app/build/outputs/apk/debug/app-debug.apk`, enable the module for System UI (`com.android.systemui`), and reboot. Play music to see the capsule. If a media notification is already present, restart playback to refresh it.

## How the hook works

| Step | What the example does |
| --- | --- |
| Enter System UI | Check the process in `onModuleLoaded`, then check the package and first-package flag in `onPackageReady`. [`META-INF/xposed`](../app/src/main/resources/META-INF/xposed/) declares the entry point, static scope, and API version. |
| Resolve the plugin | Find one attached `CapsuleContainerRoot` under the `CapsulePluginContainerController` host view. Use the root view's plugin class loader to resolve `CapsuleView`. |
| Verify ownership | Observe `sendLiveAlertData(String)`, or fall back to `sendDataToPlugin(String, StatusBarNotification)` when the first entry is unavailable. Process successful System UI media notifications only. Match the capsule card, active player ID, media row, and `MediaController` package. If the active ID is missing, only a single card is accepted. |
| Bind and draw | Find one visible, attached `CapsuleView` in the current plugin container whose `getViewItem()` contains the exact notification key. Run the original `onDraw(Canvas)` first, recheck the receiver and ownership, then overlay the background and marker. |
| Restore native rendering | Clear the binding, restore the width, and redraw on media cancellation or controller release. Remove observers when the host detaches and unhook drawing when the plugin is removed or replaced. |

See [`CapsuleHookModule.kt`](../app/src/main/java/io/github/luckyumimi/capsulehook/CapsuleHookModule.kt) for lifecycle and [`NativeCapsule.kt`](../app/src/main/java/io/github/luckyumimi/capsulehook/NativeCapsule.kt) for signatures and view discovery.

## Width and background examples

Edit `demoWidthDp` (**170 dp** by default) and `demoBackgroundArgb` (`0xA3336761`, a translucent dark teal) in [`CapsuleAppearance.kt`](../app/src/main/java/io/github/luckyumimi/capsulehook/CapsuleAppearance.kt). Set either value to `null` and rebuild to disable that example independently.

- **Width:** Recheck ownership before drawing. Set `LayoutParams.width` when `getShowingKeyList()` confirms the media card is the sole visible card in its container and `getWidthChanging()` returns `false`. On unbind, restore the width the module wrote while preserving later system changes.
- **Background:** Paint a translucent rounded overlay after native `onDraw` and before child controls are drawn. Use the laid-out view width at rest and the centered width from `getCapsuleDrawableWidth()` during animation. Redraw on unbind to remove the overlay.

## Extending the example to lyrics

1. **Find native slots by name.** Inside the verified capsule, resolve `left_section`, `right_section`, `img_primary`, `fl_title/title`, and `fl_content/content` by resource name. Check uniqueness and parent relationships. Save any visibility or layout properties you change and restore them when disabled or unbound.
2. **Handle the display cutout and animation.** Convert the cutout into local capsule coordinates and clip the same lyric layout into left and right slices. Reuse the background example's animated bounds to clip lyrics as the capsule opens and closes.
3. **Follow the verified session.** Recheck the notification key, session, and player whenever the active player changes. Listen to metadata and playback state on that session and use its playback clock; resample on seeks, pauses, and track changes.

See the [Color lyric System UI implementation](https://github.com/Luckyumimi/Color-lyric/tree/main/app/src/main/java/shop/terramoor/github/io/systemui) for the full rendering path.

## Debugging

Filter Logcat by `ColorOSCapsuleHook` to see host and plugin hook installation messages. Include the device model, firmware, System UI version, plugin version, and steps to reproduce in bug reports.

Unit tests cover media-card matching, width restoration, and animated background bounds. On a device, check player switching, pause, capsule opening and closing, simultaneous cards, and restoration after disabling the module.

## License

[Apache-2.0](../LICENSE). Build dependency: [libxposed API](https://github.com/libxposed/api) (`compileOnly`).
