# OpenCode Android

**OpenCodeをAndroidローカルまたはPCリモートで使う、非公式のオープンソースAndroidクライアントです。**

OpenCode本体はフォークせず、同じREST/SSE APIをAndroid内ランタイムとPC上の`opencode serve`の両方で利用します。

> [!IMPORTANT]
> OpenCode AndroidはOpenCode公式プロジェクトではありません。

## 主な機能

- Android端末内へのOpenCode、Alpine Linux、Git、bash、curl、ripgrepの自動セットアップ
- PC・Mac・Linux上のOpenCodeへLANまたはTailscale経由で接続
- Androidローカル／PCリモートの実行先切り替え
- OpenCodeのモデル・プロバイダー・エージェントを動的取得
- 新規セッション作成、既存セッション再開
- SSEによる回答・実行状況・承認要求のリアルタイム受信
- 危険なツール操作の許可・拒否
- Android音声認識によるプッシュ・トゥ・トーク
- Android Text-to-Speechによる回答読み上げ
- Androidの既定デジタルアシスタントとして起動
- 接続情報をAndroid Keystoreで暗号化保存
- 日本語・英語表示

## 画面構成

- **ホーム**: 現在の実行先、モデル、エージェント、最近のセッション
- **チャット**: 会話、音声入力、モデル切り替え、承認操作
- **作業先**: Androidローカルランタイム、PC接続先、作業フォルダ
- **履歴**: 実行中タスク、承認待ち、セッション、イベントログ
- **設定**: ホームアシスト、TTS、連続会話、現在の構成

## Androidローカル実行

**作業先 → このAndroid端末 → この端末へセットアップ**を押すと、Foreground Serviceで次を実行します。

1. APKに含まれる最小のAndroidネイティブPRootランナーを確認
2. Alpine Linux minirootfsを公式CDNからダウンロード
3. OpenCodeの公式muslバイナリをGitHub Releasesからダウンロード
4. 両方のSHA-256を検証
5. 一時領域へ安全に展開
6. Alpine内へGit、bash、curl、ripgrep、CA証明書を導入
7. `127.0.0.1:4097`でOpenCodeサーバーを起動
8. Androidアプリをローカル実行先へ切り替え

ダウンロードまたは展開に失敗した場合、既存ランタイムは維持されます。セットアップ後は起動、停止、修復・再導入を作業先画面から操作できます。

### 固定バージョン

初期ランタイムマニフェストは次を固定しています。

- Alpine Linux 3.24.1
- OpenCode 1.18.3
- arm64-v8a／x86_64

ランタイムマニフェストはOpenCode本体から独立しており、将来のアプリリリースで更新できます。

## PCリモート実行

PC側で強いパスワードを設定してOpenCodeサーバーを起動します。

```bash
OPENCODE_SERVER_PASSWORD='replace-with-a-strong-password' \
  opencode serve \
  --hostname 0.0.0.0 \
  --port 4096
```

Androidアプリの**作業先**画面で接続先を追加します。

```text
表示名: Mac mini
URL: http://192.168.1.10:4096
ユーザー名: opencode
パスワード: 上で設定したパスワード
```

TailscaleではPCのTailscale IPまたはMagicDNS名を利用できます。

```text
http://100.x.y.z:4096
http://your-mac.tailnet-name.ts.net:4096
```

### セキュリティ

- ポート4096をインターネットへ直接公開しないでください。
- LANまたはTailscaleでの利用を推奨します。
- 公開ネットワークではHTTPSリバースプロキシを使用してください。
- Androidアプリは危険操作を自動承認しません。
- LAN上の平文HTTPは、ユーザーが接続先ごとに明示的に許可した場合だけ利用できます。

## モデルとプロバイダー

モデル一覧はアプリへ固定で埋め込まず、選択中のOpenCode実行先から取得します。OpenCode側で無料モデル、OpenRouter、OpenAI互換プロバイダーなどが追加・変更された場合も、アプリ更新なしで一覧へ反映できます。

Androidローカル実行では、初期状態でOpenCodeが提供する利用可能なモデルを使用します。APIキーやOAuthが必要なプロバイダーのネイティブ設定UIは今後の拡張対象です。

## ホームアシスト

1. **設定 → 既定のデジタルアシスタントに設定**を押します。
2. Android設定でOpenCode Androidを選択します。
3. ホームジェスチャー、画面下隅スワイプ、または端末のアシスト操作で起動します。

ホームアシストは最後に選択した実行先、モデル、エージェントを利用します。常時ウェイクワードモデルは標準APKへ同梱していません。音声入力はマイクボタンまたはホームアシスト起動後に使用します。

## ビルド

必要環境:

- JDK 17
- Android SDK
- Python 3
- ネットワーク接続（初回のみTermuxパッケージ生成に使用）

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

初回ビルドでは、固定ロックに記録されたTermuxパッケージからPRoot・bash・必要ライブラリを生成します。各パッケージはSHA-256で検証され、生成物は`build/generated`へキャッシュされます。APKへ含まれるネイティブランナーは全ABI合計で約5MBです。

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

実機へインストール:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## リリース

`vX.Y.Z` 形式のタグをpushすると、`.github/workflows/release.yml` がテスト・Lint・署名済みRelease APKをビルドし、GitHub Releasesタブへ公開します。リポジトリに `RELEASE_KEYSTORE_BASE64` 等のシークレットが設定されている場合は署名済みAPKが、未設定の場合はunsigned APKがアップロードされます。

```bash
git tag v0.1.0
git push origin v0.1.0
```

詳細は [docs/RELEASE.md](docs/RELEASE.md) を参照。

## テスト対象

- URL安全性、LAN・Tailscale判定
- 接続情報のシリアライズ
- OpenCode REST API、Basic認証、SSE再接続
- セッション、ストリーミング、複数text part、承認状態
- RuntimeRegistryと実行先切り替え
- ランタイムカタログとイベント共有
- ローカルランタイム状態診断
- ランタイムマニフェストとSHA-256形式
- Release R8ビルドとLint

## 設計資料

- [OpenCode Android v2設計書](docs/superpowers/specs/2026-07-18-opencode-android-v2-design.md)
- [第一完成版の実装計画](docs/superpowers/plans/2026-07-18-initial-mvp.md)
- [Androidローカル実行設計](docs/LOCAL_RUNTIME.md)
- [リリース手順](docs/RELEASE.md)
- [実機検証チェックリスト](docs/DEVICE_VALIDATION.md)
- [デバイスマトリクス](docs/device-matrix.md)
- [完成版チェックリスト](docs/COMPLETION_CHECKLIST.md)

## 第三者ソフトウェア

ランタイム生成処理の一部は、MITライセンスのHermes Agent Android実装に含まれる汎用Termuxパッケージ解決・展開処理をOpenCode向けに再設計しています。OpenCode、Alpine Linux、Termuxパッケージを含む詳細は[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)を参照してください。

## ライセンス

MIT License
