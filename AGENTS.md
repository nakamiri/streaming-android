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
```

### アプリ操作

```bash
# ログクリア
adb logcat -c

# アプリ再起動
adb shell am force-stop com.reaream.app && adb shell am start -n com.reaream.app/.MainActivity

# タップ操作（縦画面、Pixel 7 1080x2400 基準）
# Play/Stop ボタン:  bounds [467,2121][614,2268]
adb shell input tap 540 2195
# Settings ボタン:   bounds [846,2100][972,2226]
adb shell input tap 909 2163
# Flip ボタン:       bounds [669,2100][795,2226]
adb shell input tap 732 2163
# Mute ボタン:       bounds [109,2100][235,2226]
adb shell input tap 172 2163
# Torch ボタン:      bounds [285,2100][411,2226]
adb shell input tap 348 2163
# Zoom 0.5x:         bounds [311,1974][422,2100]
adb shell input tap 366 2037
# Zoom 1x:           bounds [422,1974][533,2100]
adb shell input tap 477 2037
# Zoom 2x:           bounds [533,1974][644,2100]
adb shell input tap 588 2037
# Zoom 5x:           bounds [644,1974][770,2100]
adb shell input tap 707 2037

# スクリーンショット取得
adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png /tmp/screen.png
```

### ログ確認

```bash
# タグフィルタでログ確認（コマンド置換不要）
adb logcat -d -s "StreamingEngine:*" "RtmpConnection:*" "RtmpSender:*"

# grep でフィルタ
adb logcat -d -s "StreamingEngine:*" | grep -E "Error|Video" | head -20

# エラーのみ
adb logcat -d -s "RtmpConnection:*" "RtmpSender:*" | grep -E "Broken|Error|failed" | head -10
```

### テストサイクル（一連の流れ）

1. `adb logcat -c` — ログクリア
2. `adb shell am force-stop com.reaream.app && adb shell am start -n com.reaream.app/.MainActivity` — アプリ再起動
3. `sleep 3 && adb shell input tap 540 2200` — 再生ボタンタップ
4. `sleep 10 && adb logcat -d -s "TAG:*" | grep ...` — ログ確認
5. `adb shell screencap ...` — スクリーンショット確認
