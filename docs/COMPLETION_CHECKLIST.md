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
- [~] 日本語・英語の完全対応
- [ ] 320dp・一般端末・タブレット幅のUI試験
- [ ] Compose UI Testによる主要フロー検証

## B. OpenCodeリモートクライアント

- [x] 接続先追加・編集・削除
- [x] LAN / Tailscale / HTTPS URL安全性検証
- [x] Basic認証と暗号化保存
- [x] `/global/health`、セッション、モデル、エージェント取得
- [x] 新規セッション作成・メッセージ送信
- [x] OpenCode 1.18.3 SSE `message.part.updated` / `message.part.delta`対応
- [x] 中断API
- [~] 承認APIと承認UI
- [ ] 接続時capability保存・API互換性表示
- [ ] mDNS/NSDによるLAN実行先検出
- [ ] QRコードによる接続入力

## C. Androidローカルランタイム

- [x] PRootネイティブランナー
- [x] Alpine minirootfs導入
- [x] 公式OpenCode musl配布物のSHA-256検証
- [x] Git / Bash / curl / CA証明書 / ripgrep / libstdc++の自動導入
- [x] `127.0.0.1:4097`限定起動
- [x] Foreground Serviceによる起動・停止
- [x] OpenCode 1.18.3 health・モデル・エージェント・推論の実動作
- [x] セットアップ・修復UI
- [ ] OpenCode更新確認・更新
- [ ] アトミック更新とロールバック
- [ ] ランタイム完全削除
- [ ] 空き容量・メモリ・稼働時間・ログ診断UI
- [ ] SSHを含む必須ツール診断
- [ ] Storage Access Frameworkによる外部作業フォルダ
- [ ] APIキーをKeystoreからローカルOpenCodeへ安全に設定するUI

## D. チャット・開発サーフェス

- [x] モデル・エージェント選択
- [x] 既存セッション再開
- [x] 複数message part・順不同・deltaの統合表示
- [~] reasoning / tool / commandイベントのアクティビティ表示
- [ ] ツール実行カードの詳細表示
- [ ] コマンド出力の折りたたみ表示
- [ ] ファイル一覧
- [ ] ファイル検索
- [ ] Git状態
- [ ] セッション差分
- [ ] セッションTodo
- [ ] 添付ファイル
- [ ] AndroidローカルとPCリモート間のハンドオフ

## E. モバイル統合

- [x] プッシュ・トゥ・トーク
- [x] Android TTS
- [x] VoiceInteractionService登録
- [~] ホームアシストの会話フロー
- [ ] ホームアシストでAndroidローカル・PCリモートを選択
- [ ] ホームアシスト専用作業フォルダ・モデル・エージェント設定
- [ ] 実行完了・失敗通知
- [ ] 承認待ち通知
- [ ] 通知から今回だけ許可・拒否
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
- [ ] セキュリティレビュー
- [ ] 依存ライセンス一覧・SBOM
- [ ] 署名済みRelease APK/AAB手順
- [ ] Critical / Importantゼロの独立レビュー
- [ ] `main`へ統合
- [ ] GitHub公開リポジトリ`opencode-android`作成・push

## 現在の完了判定

完成ではない。ローカルOpenCodeの起動と推論まで到達した段階であり、完成条件の未実装項目をすべて解消してから完了とする。
