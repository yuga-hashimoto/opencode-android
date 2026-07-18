# OpenCode Android

**AndroidからOpenCodeを操作する、非公式のオープンソースクライアントです。**

同じアプリから、PC・Mac・Linux上の`opencode serve`へ接続して、チャット、セッション再開、モデル選択、ツール承認、音声入力、ホームアシスト、ウェイクワードを利用できます。

> [!IMPORTANT]
> OpenCode AndroidはOpenCode公式プロジェクトではありません。OpenCode本体をフォークせず、公式サーバーAPIへ接続します。

## 現在できること

- PC上のOpenCodeへLANまたはTailscale経由で接続
- OpenCodeのバージョン・接続状態を確認
- 接続済みプロバイダーとモデルをOpenCodeから取得
- `build`などのOpenCodeエージェントを選択
- 新規セッションの作成、既存セッションの再開
- SSEによる回答と実行状態のリアルタイム受信
- シェル・ファイル操作などの承認要求にAndroidから回答
- Android音声認識による入力
- Android Text-to-Speechによる回答読み上げ
- Androidの既定デジタルアシスタントとして起動
- Voskによるオフラインのウェイクワード検知
- 日本語・英語表示
- 接続情報をAndroid Keystoreで暗号化保存

## 画面構成

- **ホーム**: 接続状態、実行先、最近のセッション
- **チャット**: 会話、モデル・エージェント選択、音声入力、承認
- **接続**: PC接続先の追加、接続テスト、切り替え
- **セッション**: OpenCodeの既存セッション一覧と再開
- **設定**: ホームアシスト、ウェイクワード、TTS、連続会話

## 必要環境

### Android

- Android 8.0（API 26）以上
- マイク権限
- ウェイクワード使用時は通知権限とバックグラウンド動作の許可

### PC側

- OpenCodeがインストール済みであること
- Android端末から到達可能なLANまたはTailscale接続

## PC側のOpenCodeを起動する

強いパスワードを設定してOpenCodeサーバーを起動します。

```bash
OPENCODE_SERVER_PASSWORD='replace-with-a-strong-password' \
  opencode serve \
  --hostname 0.0.0.0 \
  --port 4096
```

OpenCode Androidでは次を入力します。

```text
表示名: Mac mini
URL: http://192.168.1.10:4096
ユーザー名: opencode
パスワード: 上で設定したパスワード
```

Tailscaleを使う場合は、PCのTailscale IPまたはMagicDNS名を入力します。

```text
http://100.x.y.z:4096
http://your-mac.tailnet-name.ts.net:4096
```

### セキュリティ上の注意

- インターネットへ直接ポート4096を公開しないでください。
- LANまたはTailscaleでの利用を推奨します。
- 公開ネットワークを経由する場合は、HTTPSリバースプロキシを使用してください。
- サーバーパスワードを設定しない運用は推奨しません。
- Androidアプリは危険操作を自動承認しません。

## Androidアプリの初期設定

1. OpenCode Androidを起動します。
2. 下部の**接続**を開きます。
3. 右下の追加ボタンを押します。
4. PCのURL、ユーザー名、パスワードを入力します。
5. LAN上のHTTPを使う場合は、確認項目をオンにします。
6. **接続テスト**でOpenCodeのバージョンが表示されることを確認します。
7. 保存後、**チャット**からモデルとエージェントを選びます。

無料モデルを含む利用可能なモデルは、Androidアプリへ固定で埋め込まず、接続中のOpenCodeが返すプロバイダー情報をそのまま表示します。そのため、OpenCode側のモデル追加・変更へ追従できます。

## ホームアシスト

1. アプリの**設定**を開きます。
2. **既定のデジタルアシスタントに設定**を押します。
3. Androidの設定画面でOpenCode Androidを選択します。
4. ホームジェスチャー、画面下隅からのスワイプ、または端末が割り当てるアシスト操作で起動します。

端末によっては、電源ボタン長押しやCircle to Searchが優先されます。その場合は端末設定でアシスタント起動方法を変更してください。

ホームアシストは、通常のチャット画面で最後に選択した接続先、プロバイダー、モデル、エージェントを使用します。

## ウェイクワード

1. **設定 → ウェイクワード検知**をオンにします。
2. 起動フレーズを設定します。初期値は`open code`です。
3. マイクと通知の権限を許可します。
4. 常時通知が表示されている間、Voskが端末内でフレーズを検知します。

音声はウェイクワード検知のために端末内で処理し、録音ファイルとして保存しません。

Android・端末メーカーの省電力制御により、画面消灯後にサービスが停止される場合があります。長時間利用する場合は、OpenCode Androidをバッテリー最適化の対象外にしてください。

Android 11以降では、再起動直後のバックグラウンド状態からマイク用Foreground Serviceを開始できません。端末を再起動した後は、OpenCode Androidを一度開くとウェイクワード検知が再開します。

## Androidローカル実行について

リポジトリには、Android内OpenCodeをリモート実行と同じAPIとして扱うための次の境界を実装しています。

- `LocalRuntimeManager`
- `LocalRuntimeStatus`
- `LocalOpenCodeBackend`

現時点のAPKにはLinux rootfs、PRoot、Bun、OpenCode本体のインストーラーを同梱していません。そのため、**現在利用できる実行方式はPCリモート実行です**。

ローカル実行の設計と残作業は[docs/LOCAL_RUNTIME.md](docs/LOCAL_RUNTIME.md)に記載しています。

## ビルド

Android Studioにプロジェクトを開くか、JDK 17とAndroid SDKを設定してGradle Wrapperを実行します。

```bash
./gradlew testDebugUnitTest assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

実機へインストール:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## テスト

- URL安全性とLAN・Tailscale判定
- 接続情報のシリアライズ
- OpenCode REST APIとBasic認証
- SSEイベントの解析
- セッション・ストリーミング・承認状態
- 接続画面の入力検証
- ホームアシスト設定解決
- ウェイクワード正規化
- Androidローカルランタイム診断

```bash
./gradlew testDebugUnitTest
```

## 設計資料

- [プロダクト設計書](docs/superpowers/specs/2026-07-18-opencode-android-design.md)
- [第一完成版の実装計画](docs/superpowers/plans/2026-07-18-initial-mvp.md)
- [Androidローカル実行設計](docs/LOCAL_RUNTIME.md)

## ライセンス

MIT License
