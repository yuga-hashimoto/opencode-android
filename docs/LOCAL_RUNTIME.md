# AndroidローカルOpenCodeランタイム

## 目的

OpenCode Androidの完成形では、PCへ接続しなくてもAndroid端末内でOpenCodeを起動し、リモート実行と同じチャット・セッション・承認UIから操作できるようにする。

OpenCode本体はフォークしない。アプリ専用ストレージへLinuxユーザーランドを構築し、その内部で公式OpenCodeを起動する。

## 現在の実装範囲

現在のコードには、ローカルランタイムを安全に追加するための境界が実装されている。

```text
LocalRuntimeManager
├─ ABI判定
├─ metadata.jsonの検証
├─ OpenCodeポートの疎通確認
└─ NotInstalled / Ready / Broken / UnsupportedAbi

LocalOpenCodeBackend
└─ Ready時に127.0.0.1のOpenCodeサーバーへ接続
```

現在のAPKはrootfsやOpenCode本体をダウンロード・実行しない。UIでは「実験機能・未インストール」と表示する。

## 想定ディレクトリ

```text
filesDir/runtime/
├─ metadata.json
├─ runner/
│  └─ proot
├─ rootfs/
│  ├─ bin/
│  ├─ usr/
│  └─ home/opencode/
├─ cache/
├─ logs/
└─ rollback/
```

`metadata.json`の例:

```json
{
  "version": "1.17.20",
  "port": 4096,
  "installedAt": 1784340000000
}
```

## 必要コンポーネント

- ARM64対応のPRootまたは同等のユーザーランドランナー
- ARM64 Linux rootfs
- Bash
- CA証明書
- Git
- curl
- BunまたはOpenCodeが要求する実行環境
- OpenCode公式配布物

初期対象ABIは`arm64-v8a`とする。`x86_64`はエミュレーター検証用に後続対応する。

## インストールフロー

```text
ユーザーが「ローカル実行をセットアップ」
↓
ABI・空き容量・ネットワークを診断
↓
署名済みマニフェストを取得
↓
ランナーとrootfsを一時領域へダウンロード
↓
SHA-256と署名を検証
↓
展開して必須コマンドを診断
↓
Linux環境内へ公式OpenCodeを導入
↓
opencode serve --hostname 127.0.0.1 --port <port>
↓
/global/healthが成功したらmetadata.jsonを確定
↓
LocalOpenCodeBackendへ切り替え
```

インストール途中のデータは完成ディレクトリへ直接書かず、成功後にアトミックに切り替える。

## 更新とロールバック

OpenCode本体とAndroidアプリの更新は分離する。

1. 現在のバージョンを`rollback/`へ退避
2. 新バージョンを一時環境へ導入
3. `opencode --version`と`/global/health`を確認
4. 成功したら切り替え
5. 失敗したら直前環境へ戻す

rootfs・PRootランナーはAndroidアプリ管理版として更新し、OpenCode本体はLinux環境内で公式配布物を利用する。

## セキュリティ

- 取得物はハッシュだけでなく署名も検証する
- rootfs内のサーバーは`127.0.0.1`だけへbindする
- Androidアプリ専用ストレージ以外への書き込みはStorage Access Framework経由に限定する
- ローカルAPIキーはAndroid Keystoreで暗号化する
- 任意の未検証バイナリを自動ダウンロードしない
- ランタイムログへAPIキーやAuthorizationヘッダーを記録しない
- シェル操作の承認既定値は`once`とする

## Android上の制約

Androidローカル実行はPCの完全な代替ではない。

適する用途:

- 小規模リポジトリの調査・編集
- Git操作
- 軽いスクリプト実行
- ドキュメント作成
- 簡易テスト

対象外:

- Docker
- Android Emulator
- iOSビルド
- 大規模なGradle・Flutterビルド
- 長時間の高負荷処理

重い作業はPCリモート実行へ切り替える。

## 次の実装単位

1. 配布マニフェストと署名検証
2. PRootランナーの組み込み
3. rootfsのダウンロード・展開
4. Runtime Doctor画面
5. OpenCode導入・起動・停止
6. Foreground Serviceによるプロセス維持
7. ローカルプロバイダー認証UI
8. PCとのセッションハンドオフ
9. 更新・ロールバック
10. 実機ベンチマークとバッテリー計測
