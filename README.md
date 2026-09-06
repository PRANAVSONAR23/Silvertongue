# Silvertongue

A floating "rewrite this" button for WhatsApp on Android. Type a rushed message, tap the sparkle
button hovering over WhatsApp, pick one of 2–3 corrected versions, and it replaces the text in the
message box. No custom keyboard — you keep using Gboard.

Sideload only. Debug signing only. Not a Play Store app.

---

## What it is made of

| Piece | Where | Job |
|---|---|---|
| `ParaphraserAccessibilityService` | `service/` | Watches for a focused WhatsApp text field, reads it, writes the chosen rewrite back |
| `OverlayController` | `overlay/` | Owns the `WindowManager` window: draggable bubble, expanded suggestion panel |
| `ParaphraseRepository` | `paraphrase/` | The LLM call, behind a pluggable `ParaphraseProvider` interface |

Providers shipped: **Groq** (default), **Google Gemini**, and an **Anthropic** implementation.

---

## 1. Get a free Groq API key

1. Go to <https://console.groq.com>.
2. Sign in with Google or GitHub. The free tier needs no card.
3. Open **API Keys** in the left sidebar → **Create API Key**.
4. Name it anything, then copy the key. It starts with `gsk_`.
   Groq shows it exactly once — copy it now.

The app defaults to **`qwen/qwen3.8-27b`**, picked by benchmarking every chat model on Groq's free
tier against this app's actual prompt: median **446 ms** round trip, 0 parse failures in 8 runs, and
the best tone preservation of the candidates (it leaves `k` as `k` and keeps emoji and slang intact).

> Groq retires model IDs regularly — `llama-3.1-8b-instant`, which this project originally targeted,
> no longer exists on the API. If you start getting `404 model_not_found`, list what your key can
> actually reach and update `GroqProvider.DEFAULT_MODEL`:
>
> ```bash
> curl -s https://api.groq.com/openai/v1/models \
>   -H "Authorization: Bearer $GROQ_API_KEY" | python3 -m json.tool
> ```

### Optional: a Gemini key as a fallback

1. Go to <https://aistudio.google.com/apikey>.
2. **Create API key** → copy it.

The Gemini provider uses `gemini-2.0-flash`.

---

## 2. Put the key in `local.properties`

`local.properties` sits in the repo root, is already in `.gitignore`, and is never committed.
Create it (or edit the one the build generated) so it contains:

```properties
sdk.dir=/path/to/your/Android/sdk

GROQ_API_KEY=gsk_your_actual_key_here
GEMINI_API_KEY=
ANTHROPIC_API_KEY=
```

**You only need `GROQ_API_KEY`.** Leave the other two empty. Missing keys become empty strings in
`BuildConfig`, and the app reports "No … API key configured" if you select that provider — nothing
crashes.

| Provider | Free tier | Should you set it? |
|---|---|---|
| Groq | Yes, no card required | **Yes — this is the one.** Fastest round trip of the three |
| Gemini | Yes, no card required | Optional. Useful if Groq rate-limits you or retires the model ID |
| Anthropic | **No free tier**, needs prepaid credits | No. It exists so the provider interface is proven against a third API shape |

`local.properties.example` in the repo root shows the same shape.

> **You can also paste a key straight into the app.** The main screen has a field per provider.
> Anything typed there is stored on-device and overrides the `local.properties` value, so rotating a
> key does not need a rebuild. Leave the field blank to fall back to the build-time key.
>
> Neither route is secret from someone holding the APK — a `BuildConfig` string is trivially
> extractable. That is an accepted trade for a personal sideloaded app; do not ship a shared key.

---

## 3. Build

Requires JDK 17 and the Android SDK (platform 35, build-tools 35.0.0).

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew assembleDebug
```

The APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Run the unit tests (they cover the defensive JSON parsing) with:

```bash
./gradlew testDebugUnitTest
```

---

## 4. Install on the device

Run this from the repo root — the same directory you ran `./gradlew` in:

```bash
cd "/Users/pranavsonar/personal/Coding Projects/Silvertongue"
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Option A — wireless, no cable (recommended on a USB-C-only Mac)

Android 11+ has wireless debugging built in, which sidesteps cables and adapters entirely. Both the
Mac and the phone must be on the same Wi-Fi network.

1. Settings → About phone → tap **MIUI version** seven times.
2. Settings → Additional settings → **Developer options** → enable **Wireless debugging**.
3. Same screen → enable **Install via USB** (the name says USB, but this gate applies to wireless
   installs too).
4. Tap the words **"Pair device with pairing code"** — not the toggle. A dialog shows a 6-digit code
   and an address like `192.168.1.7:37105`.
5. On the Mac:

```bash
adb pair 192.168.1.7:37105     # paste the 6-digit code when prompted
```

6. Now read the address off the **main Wireless debugging screen** — the port is **different** from
   the pairing port — and connect:

```bash
adb connect 192.168.1.7:35287
adb devices                     # should list the device
```

Mixing up the pairing port and the connect port is the usual stumble here.

### Option B — over a cable

The POCO M2 Pro has a USB-C port, so USB-C ↔ USB-C works with a MacBook. Two traps:

- **The cable in the POCO box is USB-A to USB-C** (the 33 W charger is a USB-A brick), so it will not
  reach a USB-C-only Mac without an adapter.
- **Many USB-C cables are charge-only.** The phone will charge normally and `adb devices` will stay
  empty, with nothing to indicate why. If a cable seems dead, try a known data cable before debugging
  anything else.

macOS needs no drivers for `adb` — that is a Windows-only requirement.

Once connected, MIUI defaults the USB mode to *No data transfer*, and `adb` cannot see the device in
that state. Pull down the notification shade, tap **"Charging this device via USB"**, and choose
**File Transfer**.

### Getting MIUI to accept an `adb install` at all

MIUI needs **two** developer toggles, not one. The second is the one that catches people:

1. Settings → About phone → tap **MIUI version** seven times.
2. Settings → Additional settings → **Developer options** → enable **USB debugging**.
3. Same screen → enable **Install via USB**.

Without step 3 the install fails with `INSTALL_FAILED_USER_RESTRICTED`. Xiaomi usually requires a
signed-in Mi account (and sometimes an active SIM with data) before it will let you flip
**Install via USB** — that is Xiaomi's restriction, not something this app can work around.

Plug the phone in and accept the RSA fingerprint dialog. Confirm the device is visible before
installing:

```bash
adb devices
```

It should list one device as `device`. If it says `unauthorized`, the RSA dialog was not accepted;
unplug, replug, and watch the phone screen.

`adb` ships in `platform-tools`; if it is not on your `PATH`, call it by full path, e.g.
`/opt/homebrew/share/android-commandlinetools/platform-tools/adb`.

To reinstall over an existing copy after a code change, the same `-r` command is enough.

---

## 5. Grant the two permissions

Open **Silvertongue** on the phone. The first card shows both permissions live — it re-checks every
time you return to the app, so you can leave it open while you toggle settings.

1. **Display over other apps** → tap the button → enable the toggle for Silvertongue.
   Grant this one *first*: the overlay window cannot be created without it.
2. **Accessibility service** → tap the button → find **Silvertongue rewrite overlay** under
   *Downloaded apps* / *Installed services* → turn it on → accept the system warning.

Come back to the app. Both rows should read **Granted**.

### If you are on Xiaomi, OPPO, realme, vivo, iQOO, or OnePlus — read this

These ROMs need more than the two Android permissions, and when they are missing **nothing errors —
the overlay just silently never appears**, even though the permission check above says "Granted".

The app detects your manufacturer and prints the exact extra steps in a card on the main screen.
The short version:

**Xiaomi / Redmi / POCO (MIUI, HyperOS)** — verified against MIUI 14 / Android 12 on a POCO M2 Pro

- Settings → Apps → **Manage apps** → Silvertongue → **Other permissions** → turn on **both**:
  - *Display pop-up windows*
  - *Display pop-up windows while running in background* ← **this is the one that catches everybody.**
    `SYSTEM_ALERT_WINDOW` alone is not enough on MIUI. Without it the bubble never appears even
    though the permission row in the app reads **Granted**, because the Android-level check genuinely
    does pass — MIUI enforces its own separate gate on top.
- Settings → Apps → **Manage apps** → Silvertongue → **Autostart** → on.
- Settings → **Battery & performance** → App battery saver → Silvertongue → **No restrictions**.
- Open **Recents**, swipe *down* on the Silvertongue card, tap the **padlock** to lock it in memory.
  On a 4 GB device this matters more than on a 6–8 GB one: MIUI will evict the accessibility service
  under memory pressure and give you no indication that it did.
- **Accessibility on MIUI is not where the docs say.** It lives at
  Settings → **Additional settings** → Accessibility → **Downloaded apps** → *Silvertongue rewrite
  overlay*. The in-app deep-link button goes straight there, so use it rather than hunting.

**OPPO / realme (ColorOS)**
- Settings → Apps → Silvertongue → **Allow floating windows**.
- Settings → Apps → **Auto Launch** / Startup Manager → enable Silvertongue.
- Settings → Battery → Power Saving → Silvertongue → **Allow background activity**.

**vivo / iQOO (Funtouch OS, OriginOS)**
- Settings → More settings → Permission manager → Silvertongue → **Floating windows** → allow.
- i Manager → App manager → **Autostart manager** → enable Silvertongue.
- Settings → Battery → **High background power consumption** → allow Silvertongue.

**OnePlus (OxygenOS)**
- Settings → Apps → Silvertongue → **Display over other apps** → allow.
- Settings → Battery → Battery optimisation → Silvertongue → **Don't optimise**.
- Settings → Battery → More settings → turn off **Deep optimisation** and **Sleep standby
  optimisation**.

On all four families, **re-check the accessibility toggle after a reboot** — these ROMs frequently
turn it back off and give no notification that they did.

---

## 6. Use it

1. Open a WhatsApp chat and tap the message box. The sparkle bubble appears.
2. Type your message.
3. Tap the bubble → the panel shows a loading shimmer, then 2–3 rewrites.
4. Tap one → it replaces the text in the message box and the panel collapses. Hit send.

Other gestures:

- **Drag** the bubble anywhere. Its position is remembered across sessions.
- **Long-press** the bubble to hide it until the next time you open WhatsApp.
- **Tap outside** the panel, or the ✕, to dismiss it without inserting.
- Tapping the bubble with an **empty** message box shows a "Nothing to rewrite" toast and makes no
  API call.
- Repeat taps while a request is in flight are ignored.

---

## When the bubble does not appear

### Reading the Permissions card

The Accessibility row shows **Running** only when the service is genuinely bound — not merely listed
in Settings. A service that Android reports as "malfunctioning" (killed or crashed, but still switched
on) shows as **"Switched on but not running"** with the repair button, which is the state that
otherwise looks like everything is fine while nothing works.

The overlay row is less certain, and says so on Xiaomi/OPPO/realme/vivo. `Settings.canDrawOverlays()`
only reports the AOSP app-op; these ROMs keep separate "Display pop-up windows" switches that no
public API can read. If that row says Granted and the bubble still never appears, those switches are
the first thing to check.

### Where it is meant to show

| Where you are | Bubble |
|---|---|
| Home screen, or any non-WhatsApp app | Hidden |
| WhatsApp **chat list** | Hidden — there is no message box to rewrite |
| **Inside any chat** | **Shown**, immediately, without tapping the message box |
| Message box focused and typing | Shown |

All five of these must hold, or nothing appears:

1. The accessibility service is enabled **and actually bound**
2. The overlay permission is granted
3. WhatsApp (or WhatsApp Business) is the foreground app
4. A visible editable field exists in that window
5. You have not long-pressed the bubble to suppress it this session

### The failure that looks like a bug but is not

**Clearing the app's data or cache, updating the app, or rebooting turns the accessibility service
off — but leaves Silvertongue listed as enabled.** Settings shows a service that is on; it receives
no events. This is normal MIUI behaviour and it will happen repeatedly.

Confirm it from a terminal:

```bash
adb shell settings get secure accessibility_enabled          # 0 means OFF
adb shell settings get secure enabled_accessibility_services # still lists Silvertongue
```

The tell-tale is `accessibility_enabled = 0` while the service is still listed.

**Flipping `accessibility_enabled` back to 1 on its own does not fix it** — the system only binds the
service when the enabled-services list itself is rewritten.

### The one-tap fix: enable the repair button

Android does not let an app switch its own accessibility service back on — that is a deliberate
security boundary. A sideloaded app can be given that ability explicitly, though. Run this **once**,
with the phone connected:

```bash
adb shell pm grant com.silvertongue.paraphraser android.permission.WRITE_SECURE_SETTINGS
```

From then on, whenever the service has been switched off, the setup screen shows a **"Turn the
service back on"** button that re-binds it in one tap, with no trip into Settings. Until the grant is
in place the screen shows the command instead of the button.

The grant **survives clearing app data and reinstalling with `adb install -r`**; it is lost only if
you uninstall the app. Verified on this device: after `pm clear`, `WRITE_SECURE_SETTINGS` was still
granted and the repair button re-bound the service in one tap.

The overlay permission is the one part the button cannot restore — `SYSTEM_ALERT_WINDOW` is an app-op
that only the system UI can change, so after a data clear you still tap **Open overlay settings**
once and flip the toggle. On Xiaomi the app sends you straight to Silvertongue's own permission page
(`miui.intent.action.APP_PERM_EDITOR`) rather than the stock screen, which on MIUI is an
alphabetical list of every installed app you would otherwise have to scroll. Non-Xiaomi devices fall
back to the standard `ACTION_MANAGE_OVERLAY_PERMISSION` screen automatically.

### After clearing app data — the whole routine

1. Open **Silvertongue**.
2. Tap **Turn the service back on**. The Accessibility row flips to Granted.
3. Tap **Open overlay settings** → turn on **Display pop-up windows** and **Display pop-up windows
   while running in background** → press Back twice.
4. Open any WhatsApp chat. The bubble is there.

Step 2 needs the one-time `WRITE_SECURE_SETTINGS` grant above; without it, replace it with a trip to
Settings → Additional settings → Accessibility → Downloaded apps and toggle Silvertongue off and on.

### Doing it by hand instead

On the phone, toggle Silvertongue **off and back on** at Settings → Additional settings →
Accessibility → Downloaded apps. Over adb:

```bash
COMP=com.silvertongue.paraphraser/com.silvertongue.paraphraser.service.ParaphraserAccessibilityService
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
sleep 3
adb shell settings put secure enabled_accessibility_services "$COMP"
adb shell settings put secure accessibility_enabled 1
```

Confirm it actually bound — this line must appear:

```bash
adb logcat -d | grep "Accessibility service connected"
```

Enabling Autostart and setting battery to *No restrictions* (steps 2 and 3 of the MIUI list above)
makes this happen far less often, but does not eliminate it after a data clear.

### Reinstalling also resets the overlay permission

`adb install -r` sets `SYSTEM_ALERT_WINDOW` back to `ignore`. After every reinstall:

```bash
adb shell appops set com.silvertongue.paraphraser SYSTEM_ALERT_WINDOW allow
```

---

## Notes on a few design decisions

**The service listens to every app, not just `com.whatsapp`.** The spec's
`android:packageNames="com.whatsapp"` filter looks tighter but is actually broken for this use case:
with it, the service receives no event when you *leave* WhatsApp, so the bubble stays parked over
whatever you switch to. The package filter is applied in code instead
(`ParaphraserAccessibilityService.TARGET_PACKAGES`, which also covers WhatsApp Business).

The consequence is that window content from other apps does reach the service, so text logging is
gated behind **both** `BuildConfig.DEBUG` **and** a WhatsApp package check. No release build logs
field text, and no build ever logs text from a non-WhatsApp package.

**The overlay window is never focusable.** `FLAG_NOT_FOCUSABLE` stays set in both the collapsed and
expanded states. Taking focus would move input focus off WhatsApp's `EditText`, which is exactly what
breaks text insertion. The panel only needs taps, so it loses nothing — the one cost is that the Back
gesture does not dismiss the panel (tap outside or the ✕ instead).

**No `AccessibilityNodeInfo` is cached across an async boundary.** The node is re-resolved
immediately before every read and every write via `findFocus(FOCUS_INPUT)`, falling back to a
breadth-first scan for the first visible editable node. Stale nodes are the classic failure here.

**Insertion has a fallback.** `ACTION_SET_TEXT` is tried first, and the cursor is then moved to the
end with `ACTION_SET_SELECTION` (otherwise you land at position 0 and it feels broken). Only if
`ACTION_SET_TEXT` is genuinely absent from the node's action list or returns `false` does it fall
back to clipboard + `ACTION_PASTE`. It is deliberately not the default path: Android 13+ shows a
clipboard preview toast on every write, which would be noisy if it fired routinely.

---

## Non-goals

No custom keyboard/IME, no multi-language support, no chat history, no cloud sync, no analytics,
no Play Store compliance.
