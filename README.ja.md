# OpenCode Android

**OpenCodeをAndroidローカルまたはPCリモートで使う、非公式のオープンソースAndroidクライアントです。**

OpenCode本体はフォークせず、同じREST/SSE APIをAndroid内ランタイムとPC上の`opencode serve`の両方で利用します。

> [!IMPORTANT]
> OpenCode AndroidはOpenCode公式プロジェクトではありません。

[English README](README.md)

## 主な機能

- Android端末内へのOpenCode、Alpine Linux、Git、bash、curl、ripgrepの自動セットアップ
- PC・Mac・Linux上のOpenCodeへLANまたはTailscale経由で接続
- Androidローカル／PCリモートの実行先切り替え
- OpenCodeのモデル・プロバイダー・エージェントを動的取得
- 新規セッション作成、既存セッション再開、セッション名の変更・削除
- QRコードまたはLAN上のmDNS検索によるPC接続先の追加
- SSEによる回答・実行状況・承認要求のリアルタイム受信
- reasoning・ツール実行・コマンド出力を折りたたみ表示できる構造化チャットタイムライン
- チャットヘッダーのメニューから会話をAndroidローカル／PCリモートの別の実行先へ引き継ぎ
- 危険なツール操作の許可・拒否
- Android音声認識によるプッシュ・トゥ・トーク＋ウェイクワード検出
- Android Text-to-Speechによる回答読み上げ
- Androidの既定デジタルアシスタントとして起動
- 接続情報をAndroid Keystoreで暗号化保存
- 日本語・英語表示

## クイックスタート

### オンデバイス実行（PC不要）

1. [Releases](https://github.com/yuga-hashimoto/opencode-android/releases/latest)からAPKをインストール
2. アプリを開く → **作業先 → このAndroid端末 → この端末へセットアップ**
3. ランタイムのダウンロード・インストールを待つ（約2分）
4. AIコーディングエージェントとチャット開始

### PCリモート実行

1. PCでOpenCodeサーバーを起動:

```bash
OPENCODE_SERVER_PASSWORD='your-strong-password' \
  opencode serve --hostname 0.0.0.0 --port 4096 --mdns
```

2. Android端末にAPKをインストール
3. アプリ → **作業先 → 接続先を追加**
4. PCのIPを入力（または**LANで検索**／**QRで追加**で自動発見）

### セキュリティ

- ポート4096をインターネットへ直接公開しないでください
- LANまたはTailscaleでの利用を推奨
- 公開ネットワークではHTTPSリバースプロキシを使用
- 危険操作は自動承認されません
- LAN上の平文HTTPは接続先ごとに明示的許可が必要

## ビルド

必要環境: JDK 17、Android SDK、Python 3、ネットワーク接続（初回のみ）

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

## コントリビューション

[CONTRIBUTING.md](CONTRIBUTING.md)を参照してください。

## 設計資料

- [OpenCode Android v2設計書](docs/superpowers/specs/2026-07-18-opencode-android-v2-design.md)
- [第一完成版の実装計画](docs/superpowers/plans/2026-07-18-initial-mvp.md)
- [Androidローカル実行設計](docs/LOCAL_RUNTIME.md)

## 第三者ソフトウェア

ランタイム生成処理の一部は、MITライセンスのHermes Agent Android実装に含まれる汎用Termuxパッケージ解決・展開処理をOpenCode向けに再設計しています。詳細は[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)を参照。

## ライセンス

[MIT](LICENSE)
