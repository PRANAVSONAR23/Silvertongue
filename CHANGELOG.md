# Changelog

Notable changes to Silvertongue. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions are milestone markers, not published releases — this app is sideloaded only.

Add an entry in the same change that makes it. Anything a future session would be surprised by
belongs here, especially model-ID swaps and platform workarounds.

---

## [Unreleased]

### Added
- Project documentation set: `AI_CONTEXT.md` as the entry point, plus `docs/ARCHITECTURE.md`,
  `docs/TECHNICAL.md`, `docs/GLOSSARY.md` and `docs/ENVIRONMENT.md`, so a new session can get context
  without reading the source file by file.
- This changelog.

---

## [0.2.0] — 2026-09-07

Automatic provider fallback. Commit `ba02f34`.

### Added
- `ParaphraseRepository` retries once on a second configured provider when the active one fails with
  a transient error (`TRANSPORT`, `RATE_LIMITED`, `SERVER_ERROR`, `MODEL_UNAVAILABLE`). `NO_KEY`,
  `BAD_RESPONSE` and `REFUSED` surface immediately.
- Typed failure taxonomy: `ParaphraseFailure` on `ParaphraseException`, with HTTP status mapping in
  `NetworkCall`.
- `ParaphraseOutcome` reports which provider answered and whether it was a fallback. Shown as
  "via Gemini" in the panel header and "Answered by …" on the setup screen, so a silent fallback never
  hides a broken primary.
- `ProviderPreferences` interface, decoupling the repository from Android DataStore.
- `ParaphraseRepositoryTest` — 7 tests covering the fallback matrix with fake providers.

### Changed
- Gemini model `gemini-2.0-flash` → **`gemini-3.1-flash-lite`**. The old ID does not exist on a live
  key; `gemini-2.5-flash` and `2.5-flash-lite` return "no longer available to new users"; and
  `gemini-3.5-flash` is a thinking model that truncates with `MAX_TOKENS` (3 of 4 test messages
  failed). Gemini output cap raised 400 → 1024 tokens.
- `testOptions.unitTests.isReturnDefaultValues = true`, required because the repository calls
  `android.util.Log`.

### Verified
- Fallback exercised live by pointing Groq at a nonexistent model: logcat showed
  `Groq failed, trying Gemini`, and Gemini returned `Yaar kal meeting hai, bhool mat jaana.` —
  Hinglish preserved, `kal` intact.
- Baseline unchanged after restoring the model: zero fallback log lines, "Answered by Groq", overlay
  still returns 3 chips.

---

## [0.1.0] — 2026-09-07

First working app, verified end to end on a POCO M2 Pro against real WhatsApp. Commit `14093e9`.

### Added
- `ParaphraserAccessibilityService` — tracks the focused WhatsApp field, reads it, writes rewrites back.
- `OverlayController` — single `WindowManager` window with a draggable bubble and a suggestion panel,
  Compose inside via `OverlayLifecycleOwner`.
- `ParaphraseRepository` with Groq, Gemini and Anthropic providers behind `ParaphraseProvider`.
- Setup screen: live permission status, settings deep links, provider picker, per-provider key entry,
  and an in-app test field that works without WhatsApp.
- `TextFieldWriter` — `ACTION_SET_TEXT`, caret moved to the end, clipboard + `ACTION_PASTE` fallback
  only on genuine failure.
- MIUI-aware setup guidance detected from `Build.MANUFACTURER`.
- One-tap accessibility self-repair via `WRITE_SECURE_SETTINGS` (granted once over adb).
- `SuggestionParserTest` — 7 tests for defensive JSON parsing.
- `README.md` and `TESTING.md`.

### Changed from the original spec
- **Dropped `android:packageNames`** from the service config; the package filter moved into code.
  With the filter, no event arrives when the user leaves WhatsApp, so the bubble stayed parked over
  other apps. Text logging is now gated on both `BuildConfig.DEBUG` and a WhatsApp package check.
- **No `AccessibilityNodeInfo` is cached.** The spec suggested caching before expanding; nodes go
  stale across a network round trip, so only the text is captured and the node is re-resolved before
  writing.
- **`FLAG_NOT_FOCUSABLE` stays set in both overlay states**, so the WhatsApp field never loses focus.
  Back does not dismiss the panel; the ✕ or a tap outside does.
- Groq model `llama-3.1-8b-instant` → **`qwen/qwen3.8-27b`**. Groq has retired the Llama family
  entirely; the original ID 404s. Chosen by benchmark: ~446 ms median, 0/8 parse failures, best tone
  preservation.

### Fixed during device bring-up
Each of these was found on hardware, not in review.

- **Bubble vanished after inserting a suggestion.** `ACTION_SET_TEXT` fires a text-changed event that
  re-enters the state refresh while the overlay is tearing down, when `rootInActiveWindow` briefly
  reports *this* app. Events from our own window are now ignored.
- **Empty field paraphrased the word "Message".** WhatsApp returns its hint from `getText()` and sets
  neither `hintText` nor `isShowingHintText`; only `textSelectionStart == -1` distinguishes an empty
  field. Zero-width characters are stripped too.
- **Time references were invented or shifted in Hinglish.** `kal` (tomorrow) became "today", `parso`
  became `kal`, and `subah call karna` grew a "tomorrow". Fixed with two prompt rules: never change a
  time reference, and reply in the language the user wrote in.
- **Permission rows were wrong on a cold start.** `MainActivity` captured its `MainViewModel` inside
  `setContent`, but `onResume()` runs before composition on a cold start, so the refresh was skipped
  and every row rendered its default. Now acquired with `by viewModels()`.
- **The setup screen claimed "Granted" while nothing worked.** A crashed or killed service stays in
  `ENABLED_ACCESSIBILITY_SERVICES`; the row now requires a real liveness flag and reads "Running" or
  "Switched on but not running". The overlay row cannot be fixed the same way — MIUI's pop-up switches
  are unreadable — so it carries an explicit caveat instead.
- **Window insets ignored** — the title collided with the status bar and content sat under the
  navigation bar under targetSdk 35 edge-to-edge enforcement.
- **Overlay settings deep link** now uses `miui.intent.action.APP_PERM_EDITOR` on Xiaomi, landing on
  the app's own permission page rather than an alphabetical list of every installed app.
