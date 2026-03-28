# Agents Guide

## Android エミュレータでのテスト実行

### コマンド実行時の注意事項

- **コマンド置換 `$(...)` を使わない** — 権限承認ダイアログが出るため
  - NG: `adb logcat --pid=$(adb shell pidof com.reaream.app)`
  - OK: `adb logcat -d -s "StreamingEngine:*"`（タグフィルタで代替）
- **出力リダイレクト `>` を使わない** — 同上
  - NG: `adb logcat > /tmp/log.txt`
  - OK: `adb logcat -d -s "TAG:*" | head -20`
- パイプ `|` と `&&` は使用可

### ビルド・インストール

```bash
# ビルドのみ
./gradlew assembleDebug

# ビルド + インストール
./gradlew assembleDebug installDebug

# ビルド結果確認（末尾数行で十分）
./gradlew assembleDebug 2>&1 | tail -5

# ユニットテスト
./gradlew testDebugUnitTest

# Instrumented テスト（エミュレータ必要）
./gradlew connectedDebugAndroidTest
```

### アプリ操作

```bash
# ログクリア
adb logcat -c

# アプリ再起動
adb shell am force-stop com.reaream.app && adb shell am start -n com.reaream.app/.MainActivity

# データクリア + 権限付与 + 起動（設定リセット時）
adb shell pm clear com.reaream.app && \
adb shell pm grant com.reaream.app android.permission.CAMERA && \
adb shell pm grant com.reaream.app android.permission.RECORD_AUDIO && \
adb shell pm grant com.reaream.app android.permission.ACCESS_FINE_LOCATION && \
adb shell pm grant com.reaream.app android.permission.ACCESS_COARSE_LOCATION && \
adb shell am start -n com.reaream.app/.MainActivity

# タップ操作（縦画面、Pixel 7 1080x2400 基準）
# ※ Widgets ボタン追加でボタン位置がずれるため、uiautomator で確認推奨
# Play/Stop ボタン:  bounds [391,2121][538,2268]
adb shell input tap 465 2195
# Settings ボタン:   bounds [719,2100][845,2226]
adb shell input tap 782 2163
# Widgets ボタン:    bounds [871,2100][997,2226]
adb shell input tap 934 2163
# Flip ボタン:       bounds [568,2100][694,2226]
adb shell input tap 631 2163
# Mute ボタン:       bounds [83,2100][209,2226]
adb shell input tap 146 2163
# Torch ボタン:      bounds [235,2100][361,2226]
adb shell input tap 298 2163

# ボタン位置を正確に調べる方法
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
grep -o 'content-desc="Settings[^"]*"[^>]*bounds="[^"]*"' /tmp/ui.xml

# スクリーンショット取得
adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png /tmp/screen.png
```

### ウィジェット操作

```bash
# Settings > Widgets でウィジェットを有効化する手順
# 1. Settings ボタンをタップ
# 2. Widgets メニュー位置を uiautomator で確認してタップ
# 3. 各トグルの位置を uiautomator で確認してタップ
# ※ 時計を有効にすると「表示形式」行が増えて下のトグル位置がずれるので注意
# ※ 地図を有効にすると「縮尺 (ズーム)」スライダーと「現在地マーカー」トグルが表示される
# ※ 速度を有効にすると「単位」ドロップダウンが表示される

# ウィジェット一覧:
#   時計 — 現在時刻（24h/12h形式選択可）
#   位置情報 — 住所または緯度経度
#   速度 — 移動速度（km/h or mph）
#   地図 — OpenStreetMap ミニマップ（ズーム 10-18、マーカー ON/OFF）

# 編集モード（Widgets ボタンから）:
#   各ウィジェットをドラッグで移動可能
#   +/- ボタンでサイズ変更（テキスト: 8-60, 地図: 60-400dp）
#   地図ウィジェットは上部に🔍ズーム操作ボタンあり
#   「リセット」で全ウィジェット位置・サイズを初期値に戻す

# 横画面のコントロールバー
# 2列レイアウト: 左列にズームセレクター、右列にコントロールボタン（スクロール可能）
# 横画面でボタンが見切れる場合は右列をスクロールして操作

# エミュレータの位置情報を設定
adb emu geo fix 139.6917 35.6895   # 東京
adb emu geo fix 135.5023 34.6937   # 大阪

# GPS移動テスト（速度・地図追従の確認）
# 3-4秒間隔で位置を段階的に変更すると速度が表示される
adb emu geo fix 139.7670 35.6814   # 東京駅付近
# (4秒待つ)
adb emu geo fix 139.7640 35.6790   # 南西へ移動
# (4秒待つ)
adb emu geo fix 139.7610 35.6760   # さらに南西へ
# ※ GPS+FUSEDの二重更新をスキップするため1秒以内の重複は無視される
# ※ 5秒間移動がないと速度は自動で 0 km/h にリセットされる

# 位置情報の権限付与
adb shell pm grant com.reaream.app android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.reaream.app android.permission.ACCESS_COARSE_LOCATION

# 位置情報の権限取り消し（警告バナーのテスト用）
adb shell pm revoke com.reaream.app android.permission.ACCESS_FINE_LOCATION
adb shell pm revoke com.reaream.app android.permission.ACCESS_COARSE_LOCATION
```

### YouTube OAuth 配信

YouTube アカウント連携で配信する機能。Chrome Custom Tabs + PKCE OAuth フロー。

- **認証方式**: Desktop タイプ OAuth クライアントID + client_secret + PKCE
- **ブランドアカウント**: ブラウザ上の Google OAuth ページでアカウント切替可能
- **ストリームキー方式**: ブランドアカウントで OAuth が使えない場合の代替

#### セットアップ

1. Google Cloud Console でプロジェクト作成、YouTube Data API v3 有効化
2. OAuth consent screen を Testing モードで設定、テストユーザー追加
3. Credentials → Create OAuth client ID → **Desktop application** タイプ
4. `local.properties` に追加:
   ```
   youtube.client.id=YOUR_CLIENT_ID
   youtube.client.secret=YOUR_CLIENT_SECRET
   ```
5. CI 用: GitHub Secrets に `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET` を設定

#### 配信フロー (OAuth)

1. ウィザード → YouTube → アカウント連携 → Chrome Custom Tab で Google ログイン
2. 認証後チャンネル情報取得 → 画質選択 → 配信枠設定（新規/既存）
3. 配信開始ボタン → 配信枠選択ダイアログ表示（LIVE中/配信予定の既存枠 + 新規作成）
4. 既存枠選択 → 紐づくストリームの ingestion 情報を取得 → 配信再開
5. 新規作成 → API で broadcast + stream 作成・バインド → RTMP URL/キー自動取得 → 配信
6. 左上の情報表示にリンクアイコン表示（タップで共有用 YouTube URL コピー）
7. 配信停止 → broadcast を complete に遷移

#### 配信復帰（途切れた場合）

配信が途切れた場合（アプリがバックグラウンドに移動した等）:
1. アプリに戻る → 配信ボタンを押す
2. ダイアログに「LIVE」ステータスの配信枠が表示される
3. その枠を選択 → 既存ストリームの RTMP URL/キーを再取得して配信再開

#### カメラ解像度
- CameraX の `ResolutionSelector` で配信解像度に最も近い 16:9 解像度を自動選択
- `setTargetRotation` で端末の向きに合わせた回転を適用（横画面では rotation=0）
- 実際のエンコード解像度は配信中の左上情報表示に表示される
- エミュレータのカメラは 16:9 で最大 1280x720。実機では 1920x1080 が選択される

#### 関連ファイル

- `YouTubeAuthManager.kt` — OAuth 認証、PKCE、トークン管理
- `YouTubeApiClient.kt` — YouTube Live Streaming API クライアント
- `OAuthRedirectActivity.kt` — ブラウザリダイレクト受信
- `StreamWizardScreen.kt` — ウィザード UI（認証/配信枠設定ステップ）
- `StreamConfig.kt` — `AuthType.YOUTUBE_OAUTH` / `YouTubePrivacy` enum

### 未確認機能

#### 動作確認予定
- [ ] RTMPS 配信（Twitch 等）
- [ ] SRT 配信
- [ ] RIST 配信
- [ ] ストリームキー方式での配信
- [ ] H.265 (HEVC) エンコード
- [ ] 4K 解像度での配信
- [ ] Twitch チャット表示

#### 実機テスト予定
- [ ] トーチ（ライト）
- [ ] 映像安定化
- [ ] 1080p 解像度での配信（エミュレータカメラは 720p まで）

### ログ確認

```bash
# タグフィルタでログ確認（コマンド置換不要）
adb logcat -d -s "StreamingEngine:*" "RtmpConnection:*" "RtmpSender:*"

# grep でフィルタ
adb logcat -d -s "StreamingEngine:*" | grep -E "Error|Video" | head -20

# エラーのみ
adb logcat -d -s "RtmpConnection:*" "RtmpSender:*" | grep -E "Broken|Error|failed" | head -10

# 位置情報プロバイダーのログ
adb logcat -d -s "LocationProvider:*"

# クラッシュログ
adb logcat -d | grep "AndroidRuntime" | grep -E "FATAL|Exception" | head -5
```

### テストサイクル（一連の流れ）

1. `adb logcat -c` — ログクリア
2. `adb shell am force-stop com.reaream.app && adb shell am start -n com.reaream.app/.MainActivity` — アプリ再起動
3. `sleep 3 && adb shell input tap 465 2195` — 再生ボタンタップ
4. `sleep 10 && adb logcat -d -s "StreamingEngine:*" | grep ...` — ログ確認
5. `adb shell screencap ...` — スクリーンショット確認
