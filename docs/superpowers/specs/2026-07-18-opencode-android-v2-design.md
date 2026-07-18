# OpenCode Android v2 設計書

更新日: 2026-07-18  
状態: 実装基準  
対象: Android専用・OpenCode専用クライアント

## 1. 目的

OpenCode Androidは、OpenCodeをスマートフォンから安全かつ直感的に操作するためのネイティブAndroidクライアントである。

主な利用形態は次の2つ。

1. PC、Mac、Linux、VPSで動く`opencode serve`へ接続するリモート実行
2. Android端末内へ管理ランタイムを導入し、端末内でOpenCodeを動かすローカル実行

アプリは単なるチャット画面ではなく、OpenCodeの実行先、ワークスペース、セッション、ツール操作、承認、実行ログ、モデル、エージェントを一貫して管理する。

## 2. 非目標

- OpenCode本体をフォークしない
- Hermes、OpenClaw、Claude Codeなど他エージェントの統合基盤にはしない
- Android端末をDockerやAndroid Emulatorの代替にはしない
- 音声アシスタント機能をアプリの中核にしない
- 旧OpenClawAssistantの画面構造、Webhook、音声常駐設計を引き継がない

## 3. プロダクト原則

### 3.1 OpenCodeを中心にする

画面とデータモデルはOpenCodeの概念に合わせる。

- Runtime: OpenCodeが動く場所
- Workspace: OpenCodeが操作するディレクトリ・プロジェクト
- Session: 会話と作業の単位
- Run: 1回の実行とツール操作
- Approval: 危険操作の承認要求
- Model / Agent: 実行構成

### 3.2 初心者にも分かる言葉を使う

API、SSE、プロバイダーなどの技術用語は必要な場所だけに限定する。

例:

- Runtime → 実行先
- Session → セッション
- Provider → AIサービス
- Permission → 操作の承認
- Workspace → 作業フォルダ

### 3.3 高度な機能を段階的に見せる

通常利用では、実行先・モデル・エージェントを選んで依頼するだけにする。

詳細設定は以下へ分離する。

- 接続の詳細
- HTTP許可
- 認証
- ローカルランタイム診断
- 音声・ホームアシスト
- 実験機能

### 3.4 ローカルとリモートで同じ操作体験

UIは`OpenCodeBackend`ではなく、より上位の`RuntimeTarget`を参照する。

ローカルとPCで以下を共通化する。

- ヘルスチェック
- ワークスペース
- セッション
- モデル・エージェント
- メッセージ送信
- SSEイベント
- 承認
- 中断

### 3.5 安全性を既定値にする

- HTTPSを既定とする
- HTTPはloopback、RFC1918、CGNAT、Tailscale、IPv6 link-local/ULAに限定する
- APIキーやサーバーパスワードはAndroid Keystoreで保護する
- `always`承認は明示操作が必要
- ファイル削除、Git push、シェル、外部送信を視覚的に区別する
- 認証情報をログへ出さない
- ローカルランタイムは`127.0.0.1`のみへbindする

## 4. 画面構成

下部ナビゲーションは5項目。

### 4.1 ホーム

目的: 現在の状態と次の操作を一目で把握する。

表示:

- 現在の実行先
- 接続状態とOpenCodeバージョン
- 現在の作業フォルダ
- 選択モデル・エージェント
- 新しい依頼
- 実行中タスク
- 承認待ち
- 最近のセッション
- ローカルランタイム状態

### 4.2 チャット

目的: OpenCodeへ依頼し、実行経過と結果を確認する。

表示:

- 実行先セレクター
- 作業フォルダセレクター
- モデルセレクター
- エージェントセレクター
- セッションタイトル
- ユーザー・アシスタントメッセージ
- reasoning、tool、command、file changeを折りたたみカードで表示
- 承認要求
- 実行中止
- 添付・音声入力
- 新規セッション

メッセージはOpenCodeの`messageID`と`partID`を保持し、複数part・更新イベント・順不同到着を安全に統合する。

### 4.3 ワークスペース

目的: 実行先と作業プロジェクトを管理する。

表示:

- Androidローカル
- PC / Mac / Linux / VPS接続先
- 接続状態
- お気に入り作業フォルダ
- 最近使った作業フォルダ
- 接続追加・編集
- ローカルランタイムのセットアップ・更新・修復・停止

旧`Connections`画面はこの画面へ統合する。

### 4.4 アクティビティ

目的: 現在・過去の作業を追跡する。

タブ:

- 実行中
- 承認待ち
- セッション
- ログ

旧`Sessions`単独画面はこの画面へ統合する。

### 4.5 設定

分類:

- AIサービスとモデル
- 既定エージェント
- セキュリティ
- 表示・言語
- 通知
- 音声とホームアシスト
- ローカルランタイム
- データと診断
- このアプリについて

音声とホームアシストは任意機能であり、初回起動時にマイク権限を要求しない。

## 5. 音声・ホームアシスト設計

### 5.1 標準APK

標準APKに常時ウェイクワード用音声モデルを同梱しない。

標準搭載:

- Android `SpeechRecognizer`によるプッシュ・トゥ・トーク
- Android TTS
- `VoiceInteractionService`による既定アシスタント候補
- 電源ボタン長押し、ホームジェスチャーからの起動

### 5.2 ウェイクワード

ウェイクワードは将来の任意ダウンロードパックとする。

- 初期状態OFF
- 専用モデルパックを明示ダウンロード
- ダウンロード容量と電池消費を表示
- Foreground Serviceと常時通知が必要
- モデルは署名・SHA-256検証
- 削除可能

旧Voskモデル、Picovoice資料、BootReceiver、常時起動コードは標準APKから削除する。

## 6. アーキテクチャ

```text
app/
├─ core/
│  ├─ api/             OpenCode REST/SSE契約
│  ├─ security/        URL検証、暗号化、認証
│  ├─ model/           共通ドメインモデル
│  └─ util/
├─ data/
│  ├─ connection/      接続先保存
│  ├─ settings/        アプリ設定
│  ├─ cache/           セッション・実行状態キャッシュ
│  └─ repository/
├─ runtime/
│  ├─ RuntimeTarget.kt
│  ├─ RuntimeRegistry.kt
│  ├─ remote/          opencode serve接続
│  └─ local/           Androidローカル管理
├─ feature/
│  ├─ home/
│  ├─ chat/
│  ├─ workspace/
│  ├─ activity/
│  ├─ settings/
│  └─ assistant/       任意機能
└─ service/
   └─ LocalRuntimeService.kt
```

### 6.1 RuntimeTarget

```kotlin
interface RuntimeTarget {
    val id: String
    val type: RuntimeType
    val displayName: String
    val state: StateFlow<RuntimeState>

    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun health(): OpenCodeHealth
    suspend fun listSessions(): List<OpenCodeSession>
    suspend fun listProviders(): ProviderCatalog
    suspend fun listAgents(): List<OpenCodeAgent>
    suspend fun listWorkspaces(): List<WorkspaceRef>
    fun events(): Flow<OpenCodeEvent>
}
```

`RemoteRuntimeTarget`と`LocalRuntimeTarget`が実装する。

### 6.2 状態管理

単一の巨大な`AppViewModel`へ全責務を集約しない。

- `RuntimeViewModel`
- `HomeViewModel`
- `ChatViewModel`
- `WorkspaceViewModel`
- `ActivityViewModel`
- `SettingsViewModel`
- `LocalRuntimeViewModel`

共有状態は`RuntimeRegistry`とRepositoryからFlowで購読する。

### 6.3 永続化

- 接続先・秘密情報: EncryptedSharedPreferences / Keystore
- 非秘密設定: DataStore
- キャッシュ・お気に入り・履歴インデックス: Room
- OpenCodeの実データを独自DBへ複製しすぎない

## 7. Androidローカルランタイム

### 7.1 方針

OpenCode本体は改変しない。Androidアプリ専用領域にLinux互換ユーザーランドを構築し、公式OpenCodeのLinux musl配布物を起動する。

初期対応:

- arm64-v8a
- x86_64はエミュレーター検証用

### 7.2 コンポーネント

```text
files/runtime/
├─ manifests/
├─ versions/
│  └─ <runtime-version>/
│     ├─ runner/
│     ├─ rootfs/
│     ├─ opencode/
│     └─ metadata.json
├─ current -> versions/<runtime-version>
├─ home/
├─ projects/
├─ cache/
└─ logs/
```

- Android向けにビルドしたPRoot runner
- Alpine minirootfs
- 公式`opencode-linux-*-musl.tar.gz`
- Git、CA証明書、SSH、ripgrepなど必要パッケージ

### 7.3 配布マニフェスト

ランタイムマニフェストはアプリのリリースに含め、各ファイルのURL、サイズ、SHA-256、対象ABIを固定する。

動的に「最新版URL」だけを信用しない。

OpenCode更新時はGitHub Release APIから取得したasset digestを検証し、利用者へ更新内容を表示する。

### 7.4 インストール状態

```text
NotInstalled
Checking
Downloading(component, progress)
Verifying(component)
Extracting(component)
Configuring
Starting
Ready(version, port)
UpdateAvailable(current, latest)
Stopped(version)
Broken(stage, reason, recoverable)
Unsupported(reason)
```

### 7.5 Foreground Service

ローカルOpenCodeサーバーの稼働中は低優先度Foreground Serviceを利用する。

- 通知から停止
- ログ表示
- メモリ使用量表示
- バッテリー最適化の説明
- OS再起動後は自動起動しない

### 7.6 プロジェクトアクセス

AndroidのStorage Access Frameworkで選択したフォルダを、ローカルランタイムの`/workspace`へ橋渡しする。

初期版ではアプリ専用`projects/`を標準とし、外部フォルダ編集は明示的な権限付与後に利用する。

## 8. OpenCode API対応

対象はOpenCode 1.18系のREST/SSE API。

最低限対応:

- `/global/health`
- `/session`
- `/session/{id}/message`
- `/session/{id}/abort`
- `/session/{id}/permissions/{permissionID}`
- `/provider`
- `/agent`
- `/event`

API差分を吸収するため、サーバーバージョンとcapabilityを接続時に保存する。

不明イベントは破棄せず、診断ログへ安全に記録する。

## 9. UIデザイン

### 9.1 色

- 背景: 深いチャコール〜濃紺
- Surface: 青みのあるダークグレー
- Primary: 落ち着いた青
- Success: 低彩度の緑
- Warning: 低彩度の琥珀
- Error: 柔らかい赤

### 9.2 情報設計

- 1カード1目的
- 状態はアイコン、文言、色の3要素で示す
- 赤だけで危険を示さない
- 主要操作は画面下部の親指範囲
- 技術情報は折りたたみ
- 320dp幅でも操作可能

### 9.3 チャット表示

- 通常文章を主役にする
- ツール操作はタイムライン
- コマンドと差分は等幅フォント
- 長いログは折りたたみ
- 実行中、承認待ち、停止済みを明確に表示

## 10. 削除対象

再設計時に以下を削除する。

- `app/src/main/assets/model/**`
- `app/src/main/assets/README.md`のPicovoice説明
- Vosk依存
- `hotword/**`
- `BootReceiver`
- 標準APK内の常時ウェイクワード処理
- 旧音声設定を`AppUiState`へ混在させる構造
- `Connections`と`Sessions`を独立トップレベルにしたナビゲーション
- 単一`AppViewModel`への過度な責務集中
- 旧設計書の「OpenClawAssistantを複製して作る」前提

`VoiceInteractionService`とプッシュ・トゥ・トークは、OpenCode専用の独立featureとして再実装する。

## 11. 実装フェーズ

### Phase A: 基盤整理

- v2設計を基準化
- 旧音声モデル・不要依存・権限を削除
- パッケージをcore/data/runtime/featureへ整理
- RuntimeRegistry導入
- ナビゲーション再構築

### Phase B: クライアント完成度

- ホーム
- チャット
- ワークスペース
- アクティビティ
- 設定
- 実行カード・承認UI
- モデル・エージェント選択
- 接続管理

### Phase C: ローカルランタイム

- runnerビルド
- rootfs/OpenCodeマニフェスト
- インストーラー
- Foreground Service
- 診断・更新・修復・削除
- ローカル実接続

### Phase D: モバイル機能

- プッシュ・トゥ・トーク
- TTS
- 既定アシスタント登録
- 通知から承認
- 任意ウェイクワードパック

### Phase E: リリース

- セキュリティレビュー
- 実機試験
- ベンチマーク
- APK/AAB署名手順
- GitHub公開
- main統合

## 12. 完成版の高度機能

完成版はチャットだけでなく、スマートフォンからOpenCodeの作業全体を管理できることを必須とする。

### 12.1 開発サーフェス

- OpenCode APIからファイル一覧・検索結果・Git状態を取得する
- セッション差分をファイル単位・行単位で表示する
- セッションTodoを表示し、進行状況を追跡する
- reasoning、tool、command、file changeをタイムラインとして統合する
- 長いコマンド出力とログを折りたたみ表示する
- アプリ側でOpenCodeのDBや作業ファイルを直接解析せず、公式APIを境界とする

### 12.2 実行先と作業の移動

- AndroidローカルとPCリモート間で、依頼文・作業フォルダ・モデル・エージェント・必要な会話コンテキストを引き継ぐハンドオフを提供する
- OpenCodeのセッションIDを異なるサーバー間で同一視せず、新しいセッションとして安全に引き継ぐ
- 複数PC接続先を管理する
- LAN上のOpenCode候補をmDNS/NSDで検出し、ユーザー確認後に登録する
- QRコードによる接続情報入力を提供する

### 12.3 ローカルランタイム管理

- セットアップ、起動、停止、更新、修復、完全削除
- 更新前の空き容量確認、SHA-256検証、アトミック切替、失敗時ロールバック
- Git、Bash、curl、CA証明書、SSH、ripgrepなどの必須ツール診断
- ログ、メモリ使用量、保存容量、稼働時間の表示
- Storage Access Frameworkで許可された外部フォルダを作業フォルダとして扱う
- OS再起動後は無断自動起動しない

### 12.4 モバイル統合

- プッシュ・トゥ・トークとTTS
- Androidの既定デジタルアシスタント候補
- ホームアシスト専用の実行先・作業フォルダ・モデル・エージェント設定
- 実行完了・失敗・承認待ち通知
- 通知から今回だけ許可・拒否できる承認操作
- 任意ダウンロード式ウェイクワードパックの導入・検証・削除
- ウェイクワード利用時の電池消費、常時通知、マイク利用を明示する

### 12.5 品質と公開

- 日本語・英語の主要画面で未翻訳文字列を残さない
- API 26、API 34、API 36で主要フローを確認する
- 320dp幅、一般的なスマートフォン幅、タブレット幅で主要操作を確認する
- セキュリティレビュー、依存ライセンス一覧、SBOMまたは依存一覧を用意する
- 署名済みRelease APK/AABの再現可能な手順を文書化する

## 13. 完了条件

次の全項目を満たすまで完成とは扱わない。

- OpenClawAssistant由来の不要資産・説明・依存が残っていない
- Debug APKが旧93MBから大幅に縮小する
- PC上のOpenCode 1.18系へ接続できる
- AndroidローカルOpenCodeをセットアップ・起動・停止・更新・修復・削除できる
- ローカルとリモートで同じチャット・承認・実行表示を利用できる
- セッション、モデル、エージェント、承認、SSE応答が実APIで動く
- ファイル、検索、Git状態、差分、Todoを実APIから表示できる
- AndroidローカルとPCリモート間のハンドオフが動く
- 複数PC、mDNS検出、QR入力が動く
- ホームアシストがローカル・リモート双方で動く
- 通知承認と任意ウェイクワードパックが動く
- ローカルランタイム更新の検証とロールバックが動く
- 外部作業フォルダを明示権限のもとで利用できる
- 320dp幅で主要操作が切れない
- Unit Test、Compose UI Test、Lint、Debug/Release buildが成功する
- API 26/34/36のAndroid実機またはエミュレーターで主要フローを確認する
- 独立レビューでCritical/Importantが解消される
- mainへ統合し、公開GitHubリポジトリへpushする
