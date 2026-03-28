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

# エミュレータの位置情報を設定
adb emu geo fix 139.6917 35.6895   # 東京
adb emu geo fix 135.5023 34.6937   # 大阪

# 位置情報の権限付与
adb shell pm grant com.reaream.app android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.reaream.app android.permission.ACCESS_COARSE_LOCATION

# 位置情報の権限取り消し（警告バナーのテスト用）
adb shell pm revoke com.reaream.app android.permission.ACCESS_FINE_LOCATION
adb shell pm revoke com.reaream.app android.permission.ACCESS_COARSE_LOCATION
```

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
