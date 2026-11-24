# ColorOS 流体クラウドのフック

[简体中文](../README.md) · [English](README.en.md) · 日本語

[Color lyric](https://github.com/Luckyumimi/Color-lyric) をもとにした、ColorOS 17 のメディアカプセル向けの最小実装です。モダン Xposed API 102 を使い、対象のメディアに紐づくことを確認したカプセルの右下に小さな緑の点を描画します。純正レイアウトは置き換えません。歌詞、ジャケット画像からの配色、設定画面は含みません。

## ビルドと動作確認

JDK 25、Android SDK Platform 37（ディレクトリ名は `android-37.0` の場合があります）、API 102 に対応した LSPosed 互換フレームワークが必要です。同梱の Gradle Wrapper を使います。

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows の PowerShell では `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug` を実行します。`app/build/outputs/apk/debug/app-debug.apk` をインストールし、フレームワークの管理画面で「システム UI」(`com.android.systemui`) を対象に有効化します。端末を再起動した後、音楽を再生して純正のメディアカプセルを表示します。モジュールの読み込み前から通知が表示されている場合は、再生し直して通知を更新してください。ランチャーアイコンはありません。

## フックの流れ

| 段階 | サンプルの処理 |
| --- | --- |
| システム UI に入る | `onModuleLoaded` でプロセスを、`onPackageReady` でパッケージ名と最初のパッケージかどうかを確認します。エントリーポイント、固定スコープ、API バージョンは [`META-INF/xposed`](../app/src/main/resources/META-INF/xposed/) で宣言します。 |
| プラグインを探す | `CapsulePluginContainerController` のホストビューから、ウィンドウに接続された唯一の `CapsuleContainerRoot` を探します。`CapsuleView` はシステム UI ではなく、ルートビューが使うプラグインの ClassLoader で解決します。 |
| メディアとの紐づきを確認する | `sendLiveAlertData(String)` を監視し、使えない場合は `sendDataToPlugin(String, StatusBarNotification)` に切り替えます。システム UI が発行に成功した通知だけを処理し、カプセルのカード、アクティブなプレイヤー ID、メディア行、`MediaController` のパッケージ名を照合します。アクティブ ID がないときはカードが一件だけの場合に限ります。 |
| 対象を特定して描画する | 現在のプラグインコンテナの中から、表示中かつ接続済みで、`getViewItem()` に完全一致する通知キーを持つ唯一の `CapsuleView` を探します。元の `onDraw(Canvas)` を先に実行し、対象ビューと通知の対応を再確認してから点を描きます。純正の幅アニメーションを検出したフレームでは描きません。 |
| 純正表示に戻す | メディア通知の取り消しやコントローラーの解放時に紐づきを解除して再描画します。ホスト切断時に監視を解除し、プラグインの取り外しや交換時に描画フックを解除します。前提が合わなければ純正表示をそのまま使います。 |

ライフサイクルは [`CapsuleHookModule.kt`](../app/src/main/java/io/github/luckyumimi/capsulehook/CapsuleHookModule.kt)、シグネチャとビューの探索は [`NativeCapsule.kt`](../app/src/main/java/io/github/luckyumimi/capsulehook/NativeCapsule.kt) を参照してください。

## 歌詞描画への応用

1. **純正の表示枠をリソース名で探す。** 確認済みのカプセル内で `left_section`、`right_section`、`img_primary`、`fl_title/title`、`fl_content/content` を探し、一意性と親子関係を確認します。表示状態や幅を変更する場合は元の値を保存し、無効化や紐づけ解除時に戻します。
2. **カメラの切り欠きと純正アニメーションに追従する。** 切り欠きの位置をカプセル内の座標に変換し、同じ歌詞レイアウトを左右に分けてクリップします。開閉に追従させる場合は `getWidthChanging()` と `getCapsuleDrawableWidth()` の値で描画範囲を決め、途中の幅を確定後の幅として扱わないようにします。サンプルはアニメーション自体を制御しません。
3. **検証済みのメディアセッションに追従する。** プレイヤーが変わったら通知キー、セッション、パッケージを再照合します。歌詞を表示する場合は、そのセッションのメタデータと再生状態を監視し、一時停止、シーク、曲の切り替え時に再生位置を取得し直します。

描画全体の実装は [Color lyric の System UI ソース](https://github.com/Luckyumimi/Color-lyric/tree/main/app/src/main/java/shop/terramoor/github/io/systemui) を参照してください。

## 互換性とトラブルシューティング

クラス名、メソッド、ビュー構造は Color lyric が対応している ColorOS 17 の実装に基づきます。Android の公開 API ではなく、すべてのファームウェアで動作するとは限りません。フックの状態は Logcat の `ColorOSCapsuleHook` タグで確認できます。不具合報告には端末名、ファームウェア、システム UI とプラグインのバージョン、再現手順を添えてください。通知キー、セッショントークン、システム APK は共有しないでください。

ローカル単体テストはカードの一意性、プレイヤー ID の完全一致、情報が不足したカードの除外を確認します。実機でのフック動作は別途確認が必要です。プレイヤーの切り替え、一時停止、カプセルを閉じる操作、メディア以外のカード、無効化後の復元を確認してください。実行時の処理は端末内で完結し、ネットワークやモデルを呼び出すことも、歌詞や通知内容をログに記録することもありません。

## ライセンス

[Apache-2.0](../LICENSE)。[Color lyric](https://github.com/Luckyumimi/Color-lyric) の実装をもとに再構成しています。[libxposed API](https://github.com/libxposed/api) はコンパイル時のみの依存関係です。ColorOS のシステムファイルは配布していません。
