# ColorOS 流体クラウドのフック

[简体中文](../README.md) · [English](README.en.md) · 日本語

[Color lyric](https://github.com/Luckyumimi/Color-lyric) をもとにした、ColorOS 17 のメディアカプセル向けフックサンプルです。モダン Xposed API 102 を使って対象のカプセルを見つけ、幅を変更し、背景色と右下の緑のマーカーを描画します。

## ビルドと動作確認

JDK 25、Android SDK Platform 37（ディレクトリ名は `android-37.0` の場合があります）、API 102 に対応した LSPosed 互換フレームワークが必要です。同梱の Gradle Wrapper を使います。

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows の PowerShell では `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug` を実行します。`app/build/outputs/apk/debug/app-debug.apk` をインストールし、フレームワークの管理画面で「システム UI」(`com.android.systemui`) を対象に有効化します。端末を再起動し、音楽を再生してメディアカプセルを確認します。すでにメディア通知がある場合は、再生し直して通知を更新します。

## フックの流れ

| 段階 | サンプルの処理 |
| --- | --- |
| システム UI に入る | `onModuleLoaded` でプロセスを、`onPackageReady` でパッケージ名と最初のパッケージかどうかを確認します。エントリーポイント、固定スコープ、API バージョンは [`META-INF/xposed`](../app/src/main/resources/META-INF/xposed/) で宣言します。 |
| プラグインを探す | `CapsulePluginContainerController` のホストビューから、ウィンドウに接続された唯一の `CapsuleContainerRoot` を探します。ルートビューのプラグイン ClassLoader で `CapsuleView` を解決します。 |
| メディアとの紐づきを確認する | `sendLiveAlertData(String)` を監視し、使えない場合は `sendDataToPlugin(String, StatusBarNotification)` に切り替えます。システム UI が発行に成功した通知だけを処理し、カプセルのカード、アクティブなプレイヤー ID、メディア行、`MediaController` のパッケージ名を照合します。アクティブ ID がないときはカードが一件だけの場合に限ります。 |
| 対象を特定して描画する | 現在のプラグインコンテナの中から、表示中かつ接続済みで、`getViewItem()` に完全一致する通知キーを持つ唯一の `CapsuleView` を探します。元の `onDraw(Canvas)` を先に実行し、対象ビューと通知の対応を再確認してから背景色とマーカーを重ねます。 |
| 純正表示に戻す | メディア通知の取り消しやコントローラーの解放時に紐づきを解除し、幅を戻して再描画します。ホスト切断時に監視を解除し、プラグインの取り外しや交換時に描画フックを解除します。 |

ライフサイクルは [`CapsuleHookModule.kt`](../app/src/main/java/io/github/luckyumimi/capsulehook/CapsuleHookModule.kt)、シグネチャとビューの探索は [`NativeCapsule.kt`](../app/src/main/java/io/github/luckyumimi/capsulehook/NativeCapsule.kt) を参照してください。

## 幅と背景のサンプル

[`CapsuleAppearance.kt`](../app/src/main/java/io/github/luckyumimi/capsulehook/CapsuleAppearance.kt) の `demoWidthDp`（初期値 **170 dp**）と `demoBackgroundArgb`（半透明の深い青緑 `0xA3336761`）を変更できます。片方を `null` にして再ビルドすると、そのサンプルだけ無効になります。

- **幅：** 描画前に対象の紐づきを再確認します。`getShowingKeyList()` が対象カード一枚を返し、`getWidthChanging()` が `false` のときに `LayoutParams.width` を変更します。紐づけ解除時はモジュールが書き込んだ幅を戻し、システム側の後からの変更は保持します。
- **背景：** 純正 `onDraw` の後、子ビューの描画前に半透明の角丸背景を重ねます。通常はレイアウト済みの幅を使い、アニメーション中は `getCapsuleDrawableWidth()` の幅を純正の中心位置に合わせます。紐づけ解除後の再描画で重ねた色を取り除きます。

## 歌詞描画への応用

1. **純正の表示枠をリソース名で探す。** 確認済みのカプセル内で `left_section`、`right_section`、`img_primary`、`fl_title/title`、`fl_content/content` を探し、一意性と親子関係を確認します。表示状態や幅を変更する場合は元の値を保存し、無効化や紐づけ解除時に戻します。
2. **カメラの切り欠きと純正アニメーションに追従する。** 切り欠きの位置をカプセル内の座標に変換し、同じ歌詞レイアウトを左右に分けてクリップします。開閉中は、背景サンプルのアニメーション描画範囲で歌詞をクリップします。
3. **検証済みのメディアセッションに追従する。** プレイヤーが変わったら通知キー、セッション、パッケージを再照合します。歌詞を表示する場合は、そのセッションのメタデータと再生状態を監視し、一時停止、シーク、曲の切り替え時に再生位置を取得し直します。

描画全体の実装は [Color lyric の System UI ソース](https://github.com/Luckyumimi/Color-lyric/tree/main/app/src/main/java/shop/terramoor/github/io/systemui) を参照してください。

## デバッグ

Logcat の `ColorOSCapsuleHook` タグで、ホストとプラグインのフック状態を確認できます。不具合報告には端末名、ファームウェア、システム UI とプラグインのバージョン、再現手順を添えてください。

単体テストはメディアカードの照合、幅の復元、背景のアニメーション描画範囲を確認します。実機ではプレイヤーの切り替え、一時停止、カプセルの開閉、複数カードの同時表示、無効化後の復元を確認します。

## ライセンス

[Apache-2.0](../LICENSE)。ビルド時の依存関係：[libxposed API](https://github.com/libxposed/api)（`compileOnly`）。
