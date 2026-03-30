# Reaream

Android 向けライブ配信アプリ。スマートフォンのカメラからYouTube、Twitch、カスタムサーバーへリアルタイム配信ができます。

## 機能

### 配信
- **対応プロトコル**: RTMP / RTMPS *[^1]* / SRT *[^1]* / RIST *[^1]*
- **YouTube OAuth 連携**: アカウントログインで配信枠の自動作成・管理（ブランドアカウント対応）、アカウント切り替え対応
- **ストリームキー方式**: YouTube / Twitch / カスタムサーバー *[^1]*
- **画質プリセット**: 720p / 1080p / 4K *[^1]*、ビットレート・FPS 個別設定可（1080p は実機確認済み）
- **アダプティブ品質**: 接続品質低下時に FPS を維持しながら解像度を自動ダウングレード（1080p→720p→480p）
- **VBR エンコード**: H.264 / H.265 (HEVC) *[^1]*
- **配信ウィザード**: 3ステップで簡単セットアップ
- **熱対策トグル**: 配信中にプレビュー描画とエンコード bitrate を抑える省熱モードに切り替え可能。端末温度表示と黒画面モードにも対応
- **端末温度表示**: `Settings > Display > Show Device Temperature` から ON/OFF を切り替え可能。配信待機中でも左上に常時表示
- **左上 HUD の常時表示**: 待機中でも解像度と音声メーターを確認可能。配信中は `LIVE` / bitrate / fps / uptime も追加表示

### カメラ
- フロント / リアカメラ切り替え
- ピンチズーム (0.5x〜5x)
- トーチ（ライト）
- 映像安定化: `Auto / Optical / Electronic` 切り替え、対応方式の表示 *[^2]*

### ウィジェットオーバーレイ
配信映像にリアルタイム情報を重ねて表示。`Widgets` ボタンからその場で ON/OFF を切り替えられ、`配置を編集` からドラッグ移動、+/- によるサイズ調整、キャンセル/保存ができます。

- **時計**: 24時間 / 12時間表示
- **位置情報**: 住所 or 緯度経度（GPS）
- **速度**: km/h / mph 切り替え
- **地図**: OpenStreetMap ミニマップ（ズーム・マーカー ON/OFF）

### その他
- Twitch チャット表示 *[^1]*
- 配信中の情報表示（ビットレート、FPS、解像度、経過時間、接続品質）
- **音声レベルメーター**: 左上 HUD に `IN / OUT` を `dBFS` で表示。マイク入力と配信へ送る音量を別々に確認可能
- YouTube 配信 URL のワンタップコピー
- **テストトーン音声入力**: `Settings > Audio > Audio Source` から 1kHz などの固定音を直接配信に載せて、マイクや周囲音の影響を切り分け可能
- **配信中の live 調整**: 配信しながら Video Bitrate / Audio Bitrate / ミュート / Gain / Audio Source を変更可能
- **配信画面の Live モーダル**: 下部バー右端の `Live` ボタンから、配信を止めずに bitrate / Audio Source / Gain を変更可能
- フォアグラウンドサービスによるバックグラウンド配信維持

[^1]: 実装済み・動作確認予定
[^2]: 設定画面で選択中カメラの対応方式を表示。効き具合は端末依存のため実機確認を継続

## 必要環境

- Android 8.0 (API 26) 以上
- カメラ・マイク搭載端末

## ビルド

```bash
# デバッグビルド
./gradlew assembleDebug

# リリースビルド
./gradlew assembleRelease

# インストール
./gradlew installDebug

# テスト
./gradlew testDebugUnitTest
```

### Release keystore と CI Secrets

GitHub の tag release で署名付き APK を作るには、release 用 keystore と Secrets が必要です。

1. keystore を作成:

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias reaream \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

2. `release.keystore` を安全な場所にバックアップ
3. keystore を base64 化して GitHub Secrets に登録

macOS:

```bash
base64 -i release.keystore | pbcopy
```

Linux:

```bash
base64 -w 0 release.keystore | xclip -selection clipboard
```

登録する Secrets:

- `KEYSTORE_BASE64`: `release.keystore` の base64 文字列
- `KEYSTORE_PASSWORD`: keystore のパスワード
- `KEY_ALIAS`: 例 `reaream`
- `KEY_PASSWORD`: key のパスワード
- `YOUTUBE_CLIENT_ID`: YouTube OAuth 用
- `YOUTUBE_CLIENT_SECRET`: YouTube OAuth 用

リポジトリ設定場所:

1. GitHub のリポジトリを開く
2. `Settings`
3. `Secrets and variables` → `Actions`
4. `New repository secret` から追加

`release.yml` の挙動:

- `Manual Build` は secrets を任意 branch で使わないよう `main` ブランチ実行のみ許可
- `v*` tag push で実行
- `KEYSTORE_BASE64` などが揃っていれば signed `release` APK を作成
- keystore が無ければ unsigned `release` APK を作成
- tag 名に `-` を含む場合は GitHub Release が prerelease 扱いになる

注意:

- `release.keystore` は絶対にコミットしない
- keystore とパスワードを失うと、同じアプリの更新配布が困難になる

### YouTube OAuth セットアップ（任意）

YouTube アカウント連携を使う場合:

1. [Google Cloud Console](https://console.cloud.google.com/) でプロジェクト作成
2. YouTube Data API v3 を有効化
3. OAuth consent screen を Testing モードで設定
4. Credentials → **Desktop application** タイプの OAuth クライアント ID を作成
5. `local.properties` に追加:

```properties
youtube.client.id=YOUR_CLIENT_ID
youtube.client.secret=YOUR_CLIENT_SECRET
```

## 技術スタック

| カテゴリ | ライブラリ |
|---|---|
| UI | Jetpack Compose + Material 3 |
| カメラ | CameraX 1.4 |
| 配信 | RootEncoder 2.6 (RTMP) / カスタム SRT・RIST 実装 |
| 認証 | Chrome Custom Tabs + PKCE OAuth 2.0 |
| トークン保管 | EncryptedSharedPreferences |
| 設定保存 | DataStore + Kotlinx Serialization |
| 地図 | OpenStreetMap タイルレンダリング |
| HTTP | OkHttp 4 |
| テスト | JUnit / MockK / Robolectric / Espresso |

## ライセンス

[MIT License](LICENSE)
