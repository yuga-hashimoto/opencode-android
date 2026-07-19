# OpenCode Android 完成版チェックリスト

基準設計: `docs/superpowers/specs/2026-07-18-opencode-android-v2-design.md`

状態記号:

- `[x]` 実装・検証済み
- `[~]` 実装中または一部のみ検証済み
- `[ ]` 未実装

## A. 基盤・UI

- [x] OpenClawAssistant由来のVosk、Picovoice、Hotword、Webhook、旧画面構造を標準APKから削除
- [x] Kotlin / Jetpack Compose / Material 3のダークUI
- [x] Home / Chat / Workspaces / Activity / Settingsの5画面
- [x] RuntimeRegistryによるAndroidローカル・PCリモートの統一
- [x] 巨大なAppViewModelを機能別ViewModelへ分割
- [~] 日本語・英語の完全対応（UI表示文字列はほぼ resources 化。Context を持たないビジネスロジック層（LocalRuntimeManager / LocalRuntimeUpdater / LocalRuntimeCommandRunner / LocalRuntimeDiagnostics / LocalRuntimeTarget / RuntimeActivityRepository / RuntimeCatalogRepository / 一部ViewModelのフォールバックエラー文言）は日本語ハードコードのまま）
- [ ] 320dp・一般端末・タブレット幅のUI試験
- [ ] Compose UI Testによる主要フロー検証

## B. OpenCodeリモートクライアント

- [x] 接続先追加・編集・削除
- [x] LAN / Tailscale / HTTPS URL安全性検証
- [x] Basic認証と暗号化保存
- [x] `/global/health`、セッション、モデル、エージェント取得
- [x] 新規セッション作成・メッセージ送信
- [x] セッションの名前変更・削除（PATCH/DELETE `/session/{id}`）
- [x] OpenCode 1.18.3 SSE `message.part.updated` / `message.part.delta`対応
- [x] 中断API
- [x] 承認APIと承認UI（Chat / Activity / 通知アクション）
- [~] 接続時capability保存・API互換性表示（health version 表示）
- [x] mDNS/NSDによるLAN実行先検出
- [x] QRコードによる接続入力

## C. Androidローカルランタイム

- [x] PRootネイティブランナー
- [x] Alpine minirootfs導入
- [x] 公式OpenCode musl配布物のSHA-256検証
- [x] Git / Bash / curl / SSH / CA証明書 / ripgrep / libstdc++の自動導入
- [x] `127.0.0.1:4097`限定起動
- [x] Foreground Serviceによる起動・停止
- [x] OpenCode 1.18.3 health・モデル・エージェント・推論の実動作
- [x] セットアップ・修復UI
- [x] OpenCode更新確認・更新
- [x] アトミック更新とロールバック
- [x] ランタイム完全削除
- [x] 空き容量・メモリ・稼働時間・ログ診断UI
- [x] SSHを含む必須ツール診断
- [x] Storage Access Frameworkによる外部作業フォルダ取り込み（/workspace 配下へコピー）
- [x] APIキーをKeystoreからローカルOpenCodeへ安全に設定するUI（auth.json 同期）

## D. チャット・開発サーフェス

- [x] モデル・エージェント選択
- [x] 既存セッション再開
- [x] 複数message part・順不同・deltaの統合表示
- [x] reasoning / tool / commandイベントのアクティビティ表示
- [x] ツール実行カードの詳細表示
- [x] コマンド出力の折りたたみ表示
- [x] ファイル一覧
- [x] ファイル検索
- [x] Git状態
- [x] セッション差分
- [x] セッションTodo
- [ ] 添付ファイル
- [x] AndroidローカルとPCリモート間のハンドオフ

## E. モバイル統合

- [x] プッシュ・トゥ・トーク
- [x] Android TTS
- [x] VoiceInteractionService登録
- [x] ホームアシストの会話フロー
- [x] ホームアシストでAndroidローカル・PCリモートを選択
- [x] ホームアシスト専用作業フォルダ・モデル・エージェント設定
- [x] 実行完了・失敗通知
- [x] 承認待ち通知
- [x] 通知から今回だけ許可・拒否
- [ ] 任意ダウンロード式ウェイクワードパック
- [ ] ウェイクワードモデルの署名・SHA-256検証・削除

## F. 品質・公開

- [x] Unit Test基盤
- [x] Lint基盤
- [x] Debug APKビルド
- [x] Release APK / R8検証
- [ ] API 26実機またはエミュレーター検証
- [ ] API 34実機またはエミュレーター検証
- [x] API 36 ARM64エミュレーター検証
- [ ] Xiaomi Android 16物理端末検証
- [ ] バッテリー・メモリ・容量ベンチマーク
- [~] セキュリティレビュー（NSC・app-layer cleartext gate・通知アクション）
- [x] 依存ライセンス一覧・SBOM（THIRD_PARTY_NOTICES.md）
- [x] 署名済みRelease APK/AAB手順（docs/RELEASE.md）
- [ ] Critical / Importantゼロの独立レビュー
- [ ] `main`へ統合
- [ ] GitHub公開リポジトリ`opencode-android`作成・push

## 現在の完了判定

主要機能（ローカル/リモート、承認、通知、プロバイダキー、SAF取込、構造化ツールタイムライン、
セッション改名・削除、QR/LAN接続、実行先間ハンドオフ）は実装済み。
残課題は実機マトリクス検証、Compose UIテスト、公開レビュー、添付ファイル対応、
Context を持たないランタイム層に残る日本語ハードコード文言の解消。
