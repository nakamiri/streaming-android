# Reaream

Android 向けライブ配信アプリ。スマートフォンのカメラからYouTube、Twitch、カスタムサーバーへリアルタイム配信ができます。

## 機能

### 配信
- **対応プロトコル**: RTMP / RTMPS / SRT / RIST
- **YouTube OAuth 連携**: アカウントログインで配信枠の自動作成・管理（ブランドアカウント対応）
- **ストリームキー方式**: YouTube / Twitch / カスタムサーバー
- **画質プリセット**: 720p / 1080p / 4K、ビットレート・FPS 個別設定可
- **VBR エンコード**: H.264 / H.265 (HEVC)
- **配信ウィザード**: 3ステップで簡単セットアップ

### カメラ
- フロント / リアカメラ切り替え
- ピンチズーム (0.5x〜5x)
- トーチ（ライト）
- 映像安定化

### ウィジェットオーバーレイ
配信映像にリアルタイム情報を重ねて表示。ドラッグで位置変更、+/- でサイズ調整可能。

- **時計**: 24時間 / 12時間表示
- **位置情報**: 住所 or 緯度経度（GPS）
- **速度**: km/h / mph 切り替え
- **地図**: OpenStreetMap ミニマップ（ズーム・マーカー ON/OFF）

### その他
- Twitch チャット表示
- 配信中の情報表示（ビットレート、FPS、解像度、経過時間、接続品質）
- YouTube 配信 URL のワンタップコピー
- フォアグラウンドサービスによるバックグラウンド配信維持

## TODO

### 動作確認予定
- [ ] RTMPS 配信（Twitch 等）
- [ ] SRT 配信
- [ ] RIST 配信
- [ ] ストリームキー方式での配信
- [ ] H.265 (HEVC) エンコード
- [ ] 4K 解像度での配信
- [ ] Twitch チャット表示

### 実機テスト予定
- [ ] トーチ（ライト）
- [ ] 映像安定化
- [ ] 1080p 解像度での配信（エミュレータカメラは 720p まで）

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
