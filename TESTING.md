# Testing Silvertongue

The build order below matches the seven stages the app was built in. Each stage is independently
verifiable, so if something breaks you can tell exactly which layer is at fault.

Everything from stage 3 onward needs a **physical device with real WhatsApp**. An emulator cannot do
it — WhatsApp requires a working phone number to activate. Stages 1 and 2 run fine on an emulator or
on any device without WhatsApp installed.

Set up a logcat window before stage 3 and leave it running:

```bash
adb logcat -c
adb logcat -s SilvertongueService:* SilvertongueOverlay:* SilvertongueWriter:*
```

---

## Stage 1 — Groq call works, no accessibility involved

This is the stage that proves the prompt and API wiring before any accessibility plumbing exists.

1. Put a real `GROQ_API_KEY` in `local.properties` (or paste one into the app's Groq field).
2. `./gradlew installDebug`, open the app.
3. In **Test without WhatsApp**, type something deliberately broken:
   `yaar can u send that file i need it urgntly`
4. Tap **Paraphrase**.

**Pass:** 2–3 rewrites appear in well under a second (measured median on `qwen/qwen3.8-27b` is
446 ms server-side; add your network RTT). They fix the spelling and grammar but
*stay casual* — "yaar" may go, but the result should not read like a corporate email, and no greeting
or sign-off should be added.

**Fail cases and what they mean:**

| What you see | Cause |
|---|---|
| "No Groq API key configured" | Key is empty in both `local.properties` and the in-app field |
| "Groq returned 401: …" | Key is wrong or revoked |
| "Groq returned 404: …" | The model ID has been retired. This already happened once: `llama-3.1-8b-instant` was pulled from Groq. List what your key can reach with `curl -s https://api.groq.com/openai/v1/models -H "Authorization: Bearer $GROQ_API_KEY"` and update `GroqProvider.DEFAULT_MODEL` |
| "Model did not return usable JSON" | The model ignored the JSON instruction; the defensive parser already tried fence-stripping and a bare-array fallback |

Also switch the provider chip to **Gemini** with a Gemini key set and repeat, to confirm the second
provider independently.

Run the parser tests too — they cover fenced JSON, preamble/trailing prose, bare arrays, blank and
duplicate suggestions, and malformed output:

```bash
./gradlew testDebugUnitTest
```

Expect 7 passing tests in `SuggestionParserTest`.

---

## Stage 2 — Permission status and settings deep links

1. With both permissions **off**, open the app. Both rows read **Not granted**, each with a button.
2. Tap **Open overlay settings** → lands directly on Silvertongue's "Display over other apps" page,
   not a generic list. Enable it, press Back.
3. **Pass:** the row flips to **Granted** with no manual refresh (the check runs in `onResume`).
4. Repeat with **Open accessibility settings** → lands on the accessibility settings page. Enable
   **Silvertongue rewrite overlay**, press Back, confirm the row flips.
5. On a Xiaomi/OPPO/vivo/OnePlus device, confirm the **Extra steps for …** card appears and names
   your ROM family. On a Pixel it should be absent.

---

## Stage 3 — Service sees the focused WhatsApp field

Requires a debug build — the log line is gated on `BuildConfig.DEBUG` and never runs in a release build.

1. Both permissions granted.
2. Open a WhatsApp chat, tap the message box, type `hello there`.
3. Watch logcat.

**Pass:** `SilvertongueService  D  Focused WhatsApp field text: hello there`

**Also verify the privacy guard:** open a *different* app with a text field — a browser address bar,
a notes app — and type in it. **No `Focused … field text` line may appear.** If one does, the package
guard in `logFocusedTextForDebug` is broken and you should stop and fix it before continuing.

**Nothing at all in logcat?** Check `Accessibility service connected` appeared at all. If it did not,
the service is not running — on the OEM ROMs listed in the README, check Autostart and battery
settings.

---

## Stage 4 — Bubble appears and disappears with field focus

1. Open a WhatsApp chat and tap the message box → the sparkle bubble appears.
2. Tap the chat area / press Back so the field loses focus → the bubble goes away.
3. Press Home, open another app → the bubble is gone. **This is the case the `packageNames` filter
   would have broken** — if the bubble follows you into other apps, the in-code package filter is
   not working.
4. Return to WhatsApp, focus the field → the bubble is back.

**Nothing appears but logcat shows the service is running?** Look for
`SilvertongueOverlay  W  Overlay permission not granted` or
`E  Overlay window rejected by WindowManager`. On MIUI the usual cause is the missing
"Display pop-up windows while running in background" toggle — see the README.

---

## Stage 5 — Suggestions render in the overlay

1. Type a broken message in WhatsApp, tap the bubble.
2. **Pass:** the panel opens essentially instantly with a shimmer, then fills with 2–3 chips.
3. **The keyboard must stay up and the message box must keep its cursor** while the panel is open.
   If the keyboard drops, `FLAG_NOT_FOCUSABLE` has been lost somewhere and insertion will fail next.
4. Long chips wrap to multiple lines rather than truncating with an ellipsis — test with a long,
   rambling sentence.
5. Tap outside the panel → it collapses back to the bubble without inserting anything.

Error states worth forcing:
- Tap the bubble with an **empty** message box → "Nothing to rewrite" toast, and **no** network
  request (nothing new in logcat).
- Turn on airplane mode and tap → an error chip appears in the panel; the app does not crash.
- Double-tap the bubble quickly → only one request fires.

---

## Stage 6 — Insertion back into WhatsApp

1. Type `see u tmrw at 5 ok`, tap the bubble, tap a suggestion.
2. **Pass:** the WhatsApp message box now contains the rewrite, **the cursor sits at the end**, the
   panel has auto-collapsed, and the send button is ready.

Check logcat for which path was taken:

```
SilvertongueService  I  Insertion outcome: SET_TEXT
```

- `SET_TEXT` — the normal path.
- `CLIPBOARD_PASTE` — `ACTION_SET_TEXT` was missing or refused; the preceding
  `SilvertongueWriter  W` line says which, and prints the node's action list. Text still lands
  correctly, but expect the Android 13+ clipboard toast.
- `FAILED` — both paths failed. The `SilvertongueWriter  E` line has the detail.

Cursor landing at position 0 instead of the end means the follow-up `ACTION_SET_SELECTION` did not
take — worth reporting, since it makes the flow feel broken even though the text is right.

---

## Stage 7 — Polish

- **Drag:** move the bubble to a new spot. Force-stop the app, reopen WhatsApp — the bubble returns
  to where you left it.
- **Edge clamping:** drag toward each screen edge; the bubble should stop at the boundary rather
  than sliding off.
- **Long-press:** long-press the bubble → "Hidden until you reopen WhatsApp" toast, bubble
  disappears. Tapping the message box again does **not** bring it back. Press Home, reopen WhatsApp,
  focus the field → the bubble is back.
- **Debounce:** covered in stage 5.
- **Battery:** leave the phone idle for an hour with the service enabled, then check
  Settings → Battery for Silvertongue. `TYPE_VIEW_TEXT_CHANGED` is throttled to one evaluation per
  250 ms and early-returns for non-WhatsApp packages, so it should be negligible.
- **Reboot:** restart the phone, open WhatsApp, focus the field. If the bubble does not appear, the
  ROM disabled the accessibility service on boot — re-enable it and check the Autostart setting.

---

## Quick regression sweep

After any change, this is the short loop that catches most breakage:

1. `./gradlew testDebugUnitTest` — parser still green.
2. In-app test field returns suggestions — provider layer fine.
3. Bubble appears on WhatsApp field focus and vanishes on Home — service and package filter fine.
4. Tap a suggestion, confirm `Insertion outcome: SET_TEXT` and cursor at end — writer fine.

---

## Verified on hardware (POCO M2 Pro, MIUI 14 / Android 12)

Stages 1–7 were run end to end on a real device against real WhatsApp. Results and the two bugs that
only appeared on hardware are recorded below.

| Stage | Result |
|---|---|
| 1. Groq call in-app | Pass — suggestions returned, tone preserved |
| 2. Permission UI + deep links | Pass — rows flip to Granted on `onResume` |
| 3. Focused-node logging | Pass — logs WhatsApp text, silent for other packages |
| 4. Bubble show/hide | Pass — appears on field focus, gone on Home |
| 5. Suggestion panel | Pass — **keyboard stayed up, field kept focus** |
| 6. `ACTION_SET_TEXT` insertion | Pass — `Insertion outcome: SET_TEXT`, cursor at end |
| 7. Long-press suppression, drag, debounce, empty guard | Pass |

### Bug 1 — bubble vanished after inserting a suggestion

`ACTION_SET_TEXT` fires `TYPE_VIEW_TEXT_CHANGED`, which re-enters `refreshOverlayState()` while the
overlay window is still tearing down. At that instant `rootInActiveWindow` reports **our own**
package, which failed the WhatsApp check and called `hide()`. Nothing re-showed the bubble until the
next accessibility event, so it stayed gone until you tapped the field again.

Fix: `refreshOverlayState()` returns early when the active window belongs to this app, leaving
overlay state untouched.

### Bug 2 — empty field paraphrased the word "Message"

WhatsApp's compose field reports its **hint** through `getText()` when empty, so `readFocusedText()`
returned `"Message"`, the empty guard never fired, and tapping the bubble produced an API call and
chips reading "Message" / "The message" / "This message".

The usual guards do not work here — measured on-device:

| | empty (hint showing) | real text |
|---|---|---|
| `text` | `Message` | ` lets meet tmrw` |
| `hintText` | `null` | `null` |
| `isShowingHintText` | `false` | `false` |
| **`textSelectionStart`** | **`-1`** | **`15`** |

WhatsApp sets neither `hintText` nor `isShowingHintText`, but it does report a text selection of
`-1` only when the field is genuinely empty. `isShowingPlaceholder()` uses that, keeping the
`isShowingHintText` check as well for apps that do set it properly.

If a future WhatsApp build changes this, the symptom is either nonsense rewrites of the hint word or
a spurious "Nothing to rewrite" toast on a field that has text. Re-measure with a temporary log of
`text` / `hintText` / `isShowingHintText` / `textSelectionStart` before changing the heuristic.

### Test-harness gotchas (not app bugs)

- **Reinstalling resets the overlay permission.** `adb install -r` flips
  `appops SYSTEM_ALERT_WINDOW` back to `ignore` and clears the accessibility service, so the bubble
  silently stops appearing after every reinstall. Re-grant both before retesting.
- **`am start` on an already-foreground activity does not re-fire `onResume`,** so the permission
  rows look stale. Background the app and return to it to test the refresh honestly.
- **The overlay is not in `uiautomator dump`,** which only captures the active window. Locate the
  bubble and chips by scanning a screenshot for the theme colours instead.
- **MIUI keeps re-enabling auto-rotate.** In landscape, Gboard opens a fullscreen extract editor with
  a DONE button; the focused node then belongs to the IME, not WhatsApp, so the bubble correctly does
  not show. Confirm the orientation before calling that a failure.

### Bug 3 — time references were invented or shifted (Hinglish)

The original prompt let the model rewrite time words freely, which corrupted meaning:

| Input | Means | Produced | |
|---|---|---|---|
| `kal office nhi aa paunga` | tomorrow | "office **today**" | wrong day |
| `parso aana` | day after tomorrow | "**Kal** aata hoon" | wrong day |
| `subah call karna` | morning, no day | "**tomorrow** morning" | invented a day |

Two prompt rules fixed it: never change, add or drop a time reference (with the Hinglish time
vocabulary spelled out), and reply in the language the user wrote in. Re-verified 8/8 on Groq and
end to end on the device.

**Language policy is deliberate:** Hinglish in, Hinglish out; English in, English out. The app
corrects spelling and grammar rather than translating. If you want English output from Hinglish
input instead, add a rule telling the model to write every rewrite in English while keeping terms of
address (yaar, bhai) as written — that variant was measured and works, it is simply not what this
build does.

Regression cases worth rerunning after any prompt edit: `kal office nhi aa paunga fever hai`,
`parso aana`, `subah call karna` (no day may appear), `see u tmrw at 5`, `reschedule to friday`,
and `k` (must come back as `k`).

### Bug 4 — permission rows were wrong on a cold start

`MainActivity` captured its `MainViewModel` from inside `setContent`, but on a cold start `onResume()`
runs **before** composition completes. `viewModel` was still null, `refreshPermissions()` was skipped,
and the screen rendered its defaults — every row reading "Not granted" regardless of the real state.
Backgrounding the app and returning fixed it, which is what made the bug easy to mistake for correct
behaviour.

Fixed by acquiring the view model with `by viewModels()` at the activity level, so it exists before
`onResume()` runs.

Regression check: force-stop the app, grant or revoke a permission over adb, then cold-start straight
into the app. The rows must be correct on the **first** frame, without backgrounding.

### Self-repair (`WRITE_SECURE_SETTINGS`)

With the one-time grant in place (see the README), verify:

1. `adb shell settings put secure accessibility_enabled 0`
2. Cold-start the app — the row reads "Not granted" and a **"Turn the service back on"** button shows.
3. Tap it. Within a few seconds `accessibility_enabled` is `1`, the enabled-services list contains the
   component, `Accessibility service connected` appears in logcat, and the row flips to "Granted".
4. Open a WhatsApp chat — the bubble is back.

Measured on device after `pm clear`: master switch `0` and services `null` beforehand; master `1`,
service listed and bound afterwards, from a single tap.

Without the grant, step 2 shows the `adb shell pm grant …` command instead of the button — that is the
expected fallback, not a failure.

### Bug 5 — the setup screen claimed "Granted" while nothing worked

Reported from real use: both rows read Granted, yet no bubble. Checking the phone by hand showed
MIUI's two "Display pop-up windows" switches were **off**, and the accessibility entry was toggled on
but annotated *"service is malfunctioning"*.

`dumpsys accessibility` confirmed it:

```
Bound services:{}
Binding services:{}
Crashed services:{{com.silvertongue.paraphraser/...ParaphraserAccessibilityService}}
```

Two separate false positives:

1. **Accessibility.** `isAccessibilityServiceEnabled()` only parsed
   `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`. A crashed or killed service stays in that string,
   so the app reported Granted for a service that was not bound. Fixed with a real liveness signal:
   `ParaphraserAccessibilityService.isRunning`, set in `onServiceConnected()` and cleared in
   `onUnbind()`/`onDestroy()`. The row now reads **Running**, or **"Switched on but not running"**
   with the repair button, and never claims Running unless the service is actually bound.
2. **Overlay.** `Settings.canDrawOverlays()` reflects only the AOSP app-op. MIUI keeps its own
   pop-up-window switches that no public API exposes, so the check passes while the overlay is still
   blocked. There is no way to query it — so on Xiaomi/OPPO/realme/vivo the row now carries an
   explicit note that the ROM has separate switches this check cannot see, instead of a bare
   "Granted".

Verified on device: with the service bound, the row reads Running and `dumpsys` shows one bound
entry; across repeated kill/restart cycles the row never read Running while `Bound services` was
empty.

**Note on "Crashed services":** MIUI marks a service crashed when its process is killed, not only on
an exception. Repeated `am force-stop` during testing (and MIUI's own memory pressure on a 4 GB
device) produces this state with no stack trace in `logcat -b crash`. Do not assume a code fault
without a trace.

## Provider fallback

`ParaphraseRepository` retries on a second provider only for `TRANSPORT`, `RATE_LIMITED`,
`SERVER_ERROR` and `MODEL_UNAVAILABLE`. `NO_KEY`, `BAD_RESPONSE` and `REFUSED` surface immediately.

`ParaphraseRepositoryTest` covers this with fake providers (7 tests): the happy path never touches the
second provider, each retryable failure falls back, each non-retryable one does not, a keyless
fallback candidate is skipped, the original error is reported when everything fails, and blank input
short-circuits before any provider is called.

**Live verification.** Point `GroqProvider.DEFAULT_MODEL` at a nonexistent id, rebuild, and run the
in-app test field. Expect `SilvertongueRepo: Groq failed, trying Gemini` in logcat and
*"Answered by Gemini after the selected provider failed"* under the results. Restore the model and
confirm the log line is gone and the label reads *"Answered by Groq"* — that is the regression check
that the happy path still makes exactly one request.

Measured on device: with a broken Groq model, `yaar kal meeting hai bhul mat jana` came back from
Gemini as *"Yaar kal meeting hai, bhool mat jaana."* — Hinglish kept, `kal` preserved, spelling fixed,
so the fallback provider honours the same language rules as the primary.

### Gemini model IDs go stale fast

Verified against a real key: `gemini-2.0-flash` (what this project originally shipped) does not exist,
and `gemini-2.5-flash` / `gemini-2.5-flash-lite` return *"no longer available to new users"*. Avoid
`gemini-3.5-flash` — it is a thinking model and truncates with `finishReason: MAX_TOKENS` at a 400-token
cap. List what a key can actually reach before changing the model:

```bash
curl -s "https://generativelanguage.googleapis.com/v1beta/models" \
  -H "x-goog-api-key: $GEMINI_API_KEY" | python3 -m json.tool | grep '"name"'
```
