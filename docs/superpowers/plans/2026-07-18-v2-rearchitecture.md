# OpenCode Android v2 再設計・実装計画

基準設計: `docs/superpowers/specs/2026-07-18-opencode-android-v2-design.md`

## 実施順

### 1. 旧アシスタント由来資産の削除

- Voskモデル約67MBを削除
- Picovoice/Porcupine資料を削除
- Vosk依存を削除
- `hotword`パッケージ、BootReceiver、常時マイクForeground Serviceを削除
- 初回起動時のマイク権限要求を削除
- Push-to-talk、TTS、VoiceInteractionServiceはOpenCode専用機能として維持
- APKサイズを計測

完了条件:

- `grep`でOpenClaw/Picovoice/Porcupine/Vosk/Hotwordが標準ソースに残らない
- Unit Test、Lint、Debug APK成功
- Debug APKが大幅縮小

### 2. パッケージ構造の整理

- `api` → `core/api`
- URL検証 → `core/security`
- 接続保存 → `data/connection`
- backend → `runtime`
- 画面 → `feature/*`
- assistant/speech → `feature/assistant`

ロジック変更は行わず、moveとimport変更だけにする。

完了条件:

- 全テスト成功
- REST/SSE実接続が維持される

### 3. RuntimeTarget / RuntimeRegistry

- リモート・ローカルを同じ実行先モデルで扱う
- 接続先CRUD、選択、状態FlowをRuntimeRegistryへ集約
- 現行OpenCodeBackendは通信アダプターとして内部利用
- FakeRuntimeTargetを用意して各featureをテスト可能にする

### 4. ViewModel分割

- HomeViewModel
- ChatViewModel
- WorkspaceViewModel
- ActivityViewModel
- SettingsViewModel
- LocalRuntimeViewModel

AppViewModelを削除し、共有データはRepository/RuntimeRegistryのFlowで配る。

### 5. 画面再構築

下部ナビゲーション:

- ホーム
- チャット
- ワークスペース
- アクティビティ
- 設定

Connectionsはワークスペースへ統合し、Sessionsはアクティビティへ統合する。

### 6. OpenCode API 1.18対応

- OpenCode 1.18.3で実契約確認
- capability/version保持
- SSEの未知イベント診断
- message partの順序・更新・複数partを維持
- permission APIを実サーバーで確認

### 7. Androidローカルランタイム POC

推奨方式:

- Android向けPRootをソースから再現ビルド
- Alpine minirootfs
- 公式OpenCode Linux musl asset
- すべてSHA-256固定
- アプリ専用領域に展開
- `127.0.0.1`へbind

段階:

1. NDKでapp-specific PRoot runnerをビルド
2. macOS上でrunner成果物を検査
3. Androidエミュレーター/実機でAlpine `/bin/sh`起動
4. OpenCode muslバイナリの`--version`
5. `opencode serve`起動
6. Androidアプリからhealth/session/message/SSE確認
7. インストーラーUI、進捗、修復、停止、削除
8. 更新・ロールバック

第三者の`opencode-termux`バイナリは本番配布に採用しない。検証用途に限定する。

### 8. ホームアシスト再実装

- Push-to-talk
- TTS
- 既定アシスタント登録
- ホームアシスト用に実行先・作業フォルダ・モデル・エージェントを独立設定
- 常時ウェイクワードは後続の任意ダウンロードパック

### 9. 最終検証と公開

- Unit Test
- Lint
- Debug/Release build
- R8
- 320dp幅UI
- API 26/34/36
- OpenCode 1.18実接続
- Androidローカル実接続
- 独立コードレビュー
- mainへmerge
- GitHub公開リポジトリ`opencode-android`作成
- push

## コミット境界

1. `docs: redesign OpenCode Android v2`
2. `refactor: remove inherited hotword stack`
3. `refactor: reorganize OpenCode client packages`
4. `feat: introduce runtime registry`
5. `refactor: split feature state holders`
6. `feat: rebuild mobile client navigation`
7. `feat: add managed Android local runtime`
8. `feat: add optional Android assistant integration`
9. `test: complete v2 device verification`
10. `docs: prepare public OSS release`
