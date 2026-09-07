# Environment

The machine, the phone, and how to drive them. Read before building or touching the device.

---

## Development machine

MacBook Air, Apple Silicon, macOS (darwin arm64). **No Android Studio** — the toolchain is Homebrew
only, installed 2026-09-06.

| Tool | Path |
|---|---|
| JDK 17 | `/opt/homebrew/opt/openjdk@17` |
| Android SDK | `/opt/homebrew/share/android-commandlinetools` |
| `sdkmanager` | `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager` |
| `adb` | `$ANDROID_HOME/platform-tools/adb` |
| Gradle | project wrapper, 8.9 (a Homebrew Gradle also exists but is not used) |

Installed SDK packages: `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`,
`build-tools;34.0.0`.

### Every build needs this

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew assembleDebug
```

No JDK is on the default `PATH`, and shell state does not persist between tool calls — export it in
**every** shell that runs Gradle.

### Three traps that cost time

1. **`sdkmanager` is not at `$ANDROID_HOME/bin/sdkmanager`.** That path does not exist. Calling it
   inside a pipeline fails silently and the pipeline still reports exit 0, so packages appear to
   install and do not.
2. **The Gradle wrapper's default `networkTimeout=10000` is too low** on this connection — the
   distribution download times out. `gradle/wrapper/gradle-wrapper.properties` is patched to
   `networkTimeout=120000`, `retries=3`. Keep it.
3. **SDK licences must be accepted** before Gradle will build:
   `yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses`.

---

## Test device

The only device this has ever run on. Everything device-specific in the docs was measured here.

| | |
|---|---|
| Model | POCO M2 Pro (`ro.product.model`), brand POCO, manufacturer **Xiaomi** |
| Android | 12 (API 31) |
| ROM | **MIUI 14** (`ro.miui.ui.version.name = V140`) |
| RAM | 4 GB (~3.6 GB usable) |
| CPU | Octa-core, up to 2.32 GHz |
| ABI | `arm64-v8a` |
| Screen | 1080 × 2400, density 440 dpi |
| Kernel | 4.14.190 |

**4 GB plus MIUI is the harshest combination for this app.** MIUI evicts the accessibility service
under memory pressure and reports nothing when it does. Locking the app in Recents matters more here
than on a 6–8 GB phone.

An emulator is useless for stages 3–7: WhatsApp needs a real phone number. Stages 1–2 run anywhere.

---

## Connecting to the phone

Wireless debugging is the practical route — the MacBook has no USB-A, the in-box POCO cable is
USB-A→USB-C, and charge-only USB-C cables fail silently.

### First time

1. Phone: Settings → About phone → tap **MIUI version** ×7
2. Settings → Additional settings → **Developer options** → enable **Wireless debugging**
3. Same screen → enable **Install via USB** (required for `adb install` even wirelessly; usually
   wants a signed-in Mi account)
4. Tap **"Pair device with pairing code"** — note the code and the `IP:port`
5. On the Mac:

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
$ANDROID_HOME/platform-tools/adb pair 192.168.1.2:37353 762154
```

### Afterwards

Pairing is permanent. Only the connect step repeats, and **the connect port differs from the pairing
port** — read it from the main Wireless debugging screen:

```bash
adb connect 192.168.1.2:43297
adb devices
```

After pairing, `adb mdns services` usually discovers the device and connects automatically.

### Disconnecting

```bash
adb disconnect && adb kill-server
```

Then turn **Wireless debugging** off on the phone. Do **not** revoke `WRITE_SECURE_SETTINGS` or the
pairing — the first powers the in-app repair button, the second saves re-pairing.

---

## Standard command set

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
ADB=$ANDROID_HOME/platform-tools/adb
COMP=com.silvertongue.paraphraser/com.silvertongue.paraphraser.service.ParaphraserAccessibilityService

./gradlew assembleDebug testDebugUnitTest
$ADB install -r app/build/outputs/apk/debug/app-debug.apk

# install resets BOTH of these - always re-run them
$ADB shell appops set com.silvertongue.paraphraser SYSTEM_ALERT_WINDOW allow
$ADB shell pm grant com.silvertongue.paraphraser android.permission.WRITE_SECURE_SETTINGS

# rebinding the service: the LIST must be rewritten, not just the master switch
$ADB shell settings delete secure enabled_accessibility_services
$ADB shell settings put secure accessibility_enabled 0
sleep 3
$ADB shell settings put secure enabled_accessibility_services "$COMP"
$ADB shell settings put secure accessibility_enabled 1

# health check
$ADB shell dumpsys accessibility | grep -E "Bound services|Crashed services"
$ADB logcat -s SilvertongueService SilvertongueOverlay SilvertongueWriter SilvertongueRepo
```

Log tags: `SilvertongueService`, `SilvertongueOverlay`, `SilvertongueWriter`, `SilvertongueRepo`.

---

## UI automation gotchas

Hard-won, and all of them produced a wrong conclusion at least once.

- **`uiautomator dump` cannot see the overlay.** It captures only the active window, and the bubble
  and panel live in a separate one. Locate them by scanning a screenshot for theme colours instead —
  bubble `#9FA8FF` (dark primary), chips `#343757` (dark secondaryContainer).
- **`uiautomator dump` silently drops some long text nodes.** If a string you expect is missing,
  screenshot before concluding it is not rendered.
- **`am start` on an already-foreground Activity does not re-fire `onResume`.** To test permission
  refresh honestly, background the app and return.
- **`am force-stop` kills the accessibility service** and MIUI then flips the master switch off. Any
  test that force-stops must re-bind afterwards.
- **MIUI keeps re-enabling auto-rotate.** In landscape, Gboard opens a fullscreen extract editor with
  a DONE button; the focused node then belongs to the IME, not WhatsApp, so the bubble correctly does
  not show. Check orientation before calling that a bug.
- **`adb shell input text` needs `%s` for spaces**, and typing into the wrong field is easy — confirm
  focus with `dumpsys window | grep mCurrentFocus` first.

---

## Repository

`git@github.com-personal:PRANAVSONAR23/Silvertongue.git`, branch `main`.

`local.properties` is git-ignored and holds `sdk.dir` plus the API keys. `local.properties.example`
shows the shape. Never commit real keys.
