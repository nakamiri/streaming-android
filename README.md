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

### カメラ
- フロント / リアカメラ切り替え
- ピンチズーム (0.5x〜5x)
- トーチ（ライト）
- 映像安定化 *[^2]*

### ウィジェットオーバーレイ
配信映像にリアルタイム情報を重ねて表示。`Widgets` ボタンからその場で ON/OFF を切り替えられ、`配置を編集` からドラッグ移動、+/- によるサイズ調整、キャンセル/保存ができます。

- **時計**: 24時間 / 12時間表示
- **位置情報**: 住所 or 緯度経度（GPS）
- **速度**: km/h / mph 切り替え
- **地図**: OpenStreetMap ミニマップ（ズーム・マーカー ON/OFF）

### その他
- Twitch チャット表示 *[^1]*
- 配信中の情報表示（ビットレート、FPS、解像度、経過時間、接続品質）
- YouTube 配信 URL のワンタップコピー
- **テストトーン音声入力**: `Settings > Audio > Audio Source` から 1kHz などの固定音を直接配信に載せて、マイクや周囲音の影響を切り分け可能
- フォアグラウンドサービスによるバックグラウンド配信維持

[^1]: 実装済み・動作確認予定
[^2]: 映像安定化は実機での動作確認予定

## 必要環境

- Android 8.0 (API 26) 以上
- カメラ・マイク搭載端末

## ビルド

```bash
# デバッグビルド
./gradlew assembleDebug

# インストール
./gradlew installDebug

# テスト
./gradlew testDebugUnitTest
```

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
