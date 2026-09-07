# Technical reference

Implementation detail per subsystem. Read the relevant section before changing that subsystem.
For layer boundaries and rationale see [ARCHITECTURE.md](ARCHITECTURE.md); for vocabulary see
[GLOSSARY.md](GLOSSARY.md).

---

## 1. Accessibility service

`service/ParaphraserAccessibilityService.kt`

### Configuration

`res/xml/accessibility_service_config.xml`:

```xml
android:accessibilityEventTypes="typeViewFocused|typeWindowStateChanged|typeViewTextChanged"
android:accessibilityFeedbackType="feedbackGeneric"
android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
android:canRetrieveWindowContent="true"
android:notificationTimeout="100"
```

There is intentionally **no `android:packageNames`** — see ARCHITECTURE.md.

### Event handling

`TYPE_VIEW_TEXT_CHANGED` is the noisy one. It is filtered twice before any work happens:

1. Dropped immediately if `event.packageName` is not in `TARGET_PACKAGES`
   (`com.whatsapp`, `com.whatsapp.w4b`) — a string comparison, no node access.
2. Throttled to one evaluation per **250 ms** (`TEXT_CHANGE_THROTTLE_MS`).

`typeViewTextChanged` was kept rather than dropped because focus events alone miss cases where
WhatsApp restores an already-focused field without re-firing `TYPE_VIEW_FOCUSED`.

### State refresh

`refreshOverlayState()` runs on every surviving event:

1. If the active window belongs to **this app**, return without touching state. Prevents the overlay
   from hiding itself while tearing down after an insertion.
2. Compute `isTargetForeground` from `rootInActiveWindow.packageName`.
3. On a false→true transition, clear long-press suppression (that is what "hidden until you reopen
   WhatsApp" means).
4. If not WhatsApp, hide. Otherwise resolve an editable node; hide if none, show the bubble if one.

### Node resolution

```
rootInActiveWindow
  └─ package must be in TARGET_PACKAGES
     └─ findFocus(FOCUS_INPUT), accepted if isEditable
        └─ else breadth-first scan for the first visible editable node
           (capped at MAX_NODES_SCANNED = 400)
```

The BFS fallback is why the bubble appears as soon as you open a chat, without tapping the message
box — the compose field is a visible editable descendant even when unfocused.

### Placeholder detection — the non-obvious part

WhatsApp returns its **hint** from `getText()` when the field is empty. Naively this makes the app
paraphrase the word "Message". The usual guards do not work; measured on a POCO M2 Pro:

| | empty (hint showing) | real text |
|---|---|---|
| `text` | `Message` | ` lets meet tmrw` |
| `hintText` | `null` | `null` |
| `isShowingHintText` | `false` | `false` |
| **`textSelectionStart`** | **`-1`** | **`15`** |

WhatsApp sets neither hint flag, but reports a text selection of `-1` only when genuinely empty.
`isShowingPlaceholder()` uses that, keeping `isShowingHintText` as well for apps that behave properly.

`readFocusedText()` also strips zero-width characters (`U+200B`–`U+200D`, `U+FEFF`) — WhatsApp's
search field prefixes one, and Kotlin's `trim()` does not remove them.

**If WhatsApp changes this**, the symptom is either nonsense rewrites of the hint word, or a spurious
"Nothing to rewrite" on a field that has text. Re-measure with a temporary log of
`text` / `hintText` / `isShowingHintText` / `textSelectionStart` before changing the heuristic.

### Writing text back

`service/TextFieldWriter.kt`, returns `InsertionOutcome` = `SET_TEXT` | `CLIPBOARD_PASTE` | `FAILED`.

1. If `ACTION_SET_TEXT` is in the node's action list, perform it with
   `ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE`.
2. On success, `ACTION_SET_SELECTION` with start = end = `text.length` to put the caret at the end.
   Without this the caret lands at 0 and the app feels broken.
3. Only if the action is absent or returns false: put the text on the clipboard, select all via
   `ACTION_SET_SELECTION`, then `ACTION_PASTE`, then move the caret to the end.

The outcome is logged at INFO on every insertion, which is the fastest way to diagnose a failure.

### Logging and privacy

The service receives window content from every app. Field text is logged **only** when both hold:

```kotlin
if (!BuildConfig.DEBUG) return
if (node.packageName?.toString() !in TARGET_PACKAGES) return
```

No release build logs field text, and no build logs text from a non-WhatsApp package. Verify this by
typing into a browser address bar and confirming logcat stays silent.

### Liveness

`isRunning` is a `@Volatile` companion flag set in `onServiceConnected()` and cleared in `onUnbind()`
and `onDestroy()`. The setup screen reads it. This is what distinguishes "listed in settings" from
"actually bound" — see the permissions section below.

---

## 2. Overlay

`overlay/OverlayController.kt`

### Window

A **single** `WindowManager` window whose `LayoutParams` are swapped between states.

| | Collapsed | Expanded |
|---|---|---|
| width / height | `WRAP_CONTENT` | `MATCH_PARENT` / `WRAP_CONTENT` |
| gravity | `TOP or START` | `TOP or START` |
| position | remembered `bubbleX`/`bubbleY` | `x=0`, `y` = 18% of screen height |
| extra flag | — | `FLAG_WATCH_OUTSIDE_TOUCH` |

Shared flags, in both states:

```kotlin
FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_ALT_FOCUSABLE_IM
```

Type is `TYPE_APPLICATION_OVERLAY`, format `TRANSLUCENT`.

The panel is anchored at a fixed 18% of screen height rather than next to the bubble, because whether
the IME window layers above `TYPE_APPLICATION_OVERLAY` was never confirmed. Predictably clear of the
keyboard beat visually ideal.

### Attachment guard

`ensureAttached()` returns false and logs if `Settings.canDrawOverlays()` is false, and catches
`WindowManager.BadTokenException` around `addView`. Both paths are expected on MIUI.

The `ComposeView` is created with a `ContextThemeWrapper(context, R.style.Theme_Silvertongue)`,
because a bare Service context has no theme and Compose needs theme attributes to resolve.

### Compose in a non-Activity window

`overlay/OverlayLifecycleOwner.kt` implements `LifecycleOwner`, `ViewModelStoreOwner` and
`SavedStateRegistryOwner`, and attaches all three via `setViewTree*Owner` before `addView`. Order
matters: `performAttach()` → `performRestore(null)` → move to `CREATED` → `RESUMED`. Without this the
`ComposeView` crashes on first composition.

`ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` ties disposal to that owner.

### States

`OverlayUiState` = `Collapsed` | `Loading` | `Suggestions(items, fallbackProvider)` | `Failed(message)`.

Transitions:

- **tap bubble** → read text; blank ⇒ toast and stay collapsed; otherwise `Loading`, expand, launch request
- **result** → `Suggestions` or `Failed`
- **tap chip** → write text; success ⇒ collapse; failure ⇒ `Failed`
- **tap ✕ or outside** → collapse, cancel any in-flight job
- **long-press bubble** → suppress until WhatsApp is re-entered

Debounce is a single `requestJob`; taps are ignored while it is active.

### Gestures and position

Two `pointerInput` blocks on the bubble: `detectTapGestures` (tap, long-press) and
`detectDragGestures`. Drag updates `bubbleX`/`bubbleY`, clamped to screen bounds, and persists to
DataStore on drag end. `SettingsRepository.UNSET_POSITION` (-1) means "use the default", which is
160 px from the right edge at 55% of screen height.

---

## 3. Providers

### Interface

```kotlin
interface ParaphraseProvider {
    val id: ProviderId
    suspend fun paraphrase(rawText: String): List<String>
}
```

Deliberately one suspend function. Key access is injected as
`fun interface ApiKeySource { suspend fun currentKey(): String }`, so a provider never knows where
keys come from.

### Failure taxonomy

`ParaphraseException` carries a `ParaphraseFailure`:

| Failure | Raised when | Falls back? |
|---|---|---|
| `EMPTY_INPUT` | blank input, before any call | no |
| `NO_KEY` | key blank for that provider | no |
| `TRANSPORT` | `IOException` from OkHttp | **yes** |
| `RATE_LIMITED` | HTTP 429 | **yes** |
| `SERVER_ERROR` | HTTP ≥ 500 | **yes** |
| `MODEL_UNAVAILABLE` | HTTP 404 | **yes** |
| `BAD_RESPONSE` | other non-2xx, empty body, unparseable JSON | no |
| `REFUSED` | Anthropic `stop_reason: "refusal"` | no |

HTTP→failure mapping lives in `NetworkCall.failureForStatus`.

### Fallback

`ParaphraseRepository.paraphrase()`:

1. Blank input ⇒ `EMPTY_INPUT`, no provider called.
2. Call the active provider. Success ⇒ `ParaphraseOutcome(suggestions, provider, usedFallback=false)`.
3. If the failure is not retryable, surface it unchanged.
4. Otherwise iterate the other providers in `ProviderId` order, skipping any with a blank key. First
   success wins, with `usedFallback=true`.
5. All fail ⇒ return the **original** error, not the last one.

### Wire formats

| | Groq | Gemini | Anthropic |
|---|---|---|---|
| Endpoint | `api.groq.com/openai/v1/chat/completions` | `generativelanguage.googleapis.com/v1beta/models/{model}:generateContent` | `api.anthropic.com/v1/messages` |
| Auth | `Authorization: Bearer` | `x-goog-api-key` | `x-api-key` + `anthropic-version: 2023-06-01` |
| JSON mode | `response_format: {type: json_object}` | `responseMimeType: application/json` | prompt only |
| System prompt | `messages[0]` role system | `systemInstruction` | `system` field |
| Output cap | `max_tokens: 400` | `maxOutputTokens: 1024` | `max_tokens: 1024` |

Anthropic additionally sets `output_config.effort = "low"` and checks `stop_reason == "refusal"`
before reading content.

### Model IDs go stale — verify, never assume

Both shipped defaults were dead on arrival when first tested against live APIs.

| Provider | Current default | Notes |
|---|---|---|
| Groq | `qwen/qwen3.8-27b` | Chosen by benchmark: ~446 ms median, 0/8 parse failures. The Llama family is **gone** from Groq; `llama-3.1-8b-instant` 404s |
| Gemini | `gemini-3.1-flash-lite` | `gemini-2.0-flash` does not exist; `gemini-2.5-flash*` return "no longer available to new users"; `gemini-3.5-flash` is a thinking model that truncates with `MAX_TOKENS` |
| Anthropic | `claude-opus-5` | Untested — no key has ever been configured |

List what a key can actually reach before changing a model:

```bash
curl -s https://api.groq.com/openai/v1/models -H "Authorization: Bearer $GROQ_API_KEY"
curl -s https://generativelanguage.googleapis.com/v1beta/models -H "x-goog-api-key: $GEMINI_API_KEY"
```

---

## 4. Prompt and parsing

`paraphrase/Prompts.kt` holds one system prompt. Its load-bearing rules:

- 2–3 alternatives, chat-length, no greetings or sign-offs, no invented content
- preserve meaning, tone and formality
- **never change, add or drop a time reference**, with the Hinglish time vocabulary spelled out
  (`aaj`, `kal`, `parso`, `subah`, `dopahar`, `shaam`, `raat`, `baje`)
- **reply in the language the user wrote in** — Hinglish stays Hinglish, English stays English
- strict JSON: `{"suggestions": [...]}`, no fences

The last two rules were added after measuring real failures: `kal` (tomorrow) became "today",
`parso` became `kal`, and `subah call karna` grew a "tomorrow" that was never there.

`SuggestionParser.parse()` is deliberately paranoid, in order:

1. strip ``` fences
2. take the substring from the first `{` to the last `}`, decode as `{"suggestions": [...]}`
3. failing that, take the first `[` to the last `]` and decode as a bare array
4. trim, drop blanks, dedupe, cap at 3
5. empty result ⇒ `BAD_RESPONSE`

Covered by `SuggestionParserTest` (7 tests): fenced JSON, preamble and trailing prose, bare arrays,
blanks and duplicates, malformed output.

---

## 5. Settings and keys

`data/SettingsRepository.kt`, DataStore Preferences, store name `silvertongue_settings`.

| Key | Purpose |
|---|---|
| `active_provider` | selected `ProviderId`, default `GROQ` |
| `api_key_<PROVIDER>` | in-app override, blank means unset |
| `overlay_x`, `overlay_y` | bubble position, `-1` = unset |

Key resolution: **in-app override wins, otherwise `BuildConfig`**. Build-time keys come from
`local.properties` via `buildConfigField`, which means they are plaintext in the APK. Acceptable for
a sideloaded personal app; never ship a shared key this way.

`SettingsRepository` implements `ProviderPreferences` so the repository can be tested with fakes.

---

## 6. Permissions and self-repair

Two Android permissions plus, on some ROMs, vendor gates that cannot be queried.

| Check | API | Reliable? |
|---|---|---|
| Overlay | `Settings.canDrawOverlays()` | Only for the AOSP app-op. MIUI/ColorOS pop-up switches are invisible to it |
| Accessibility listed | `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` | Stays populated for a crashed service — **not** proof it works |
| Accessibility running | `ParaphraserAccessibilityService.isRunning` | Yes. This is what the UI shows |

`ui/AccessibilityServiceRepair.kt` re-binds the service when the app holds
`WRITE_SECURE_SETTINGS`: remove the component from the list, set the master switch to 0, wait 600 ms,
write the list back with the component, set the switch to 1, then confirm. Setting the master switch
alone does **not** rebind — the list itself must be rewritten.

`ui/SettingsLauncher.kt` prefers `miui.intent.action.APP_PERM_EDITOR` with `extra_pkgname`, which
lands directly on the app's own permission page. MIUI's stock overlay screen is an alphabetical list
of every installed app. Non-Xiaomi devices fall back to `ACTION_MANAGE_OVERLAY_PERMISSION`.

---

## 7. Build

| | Value |
|---|---|
| AGP / Gradle / Kotlin | 8.7.3 / 8.9 / 2.0.21 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Compose BOM | 2024.12.01 |
| OkHttp / kotlinx.serialization / DataStore | 4.12.0 / 1.7.3 / 1.1.1 |
| Signing | debug only, release reuses it |

`testOptions.unitTests.isReturnDefaultValues = true` is required — `ParaphraseRepository` calls
`android.util.Log`, which otherwise throws "not mocked" in JVM tests.

`gradle-wrapper.properties` carries `networkTimeout=120000` and `retries=3`; the 10 s default times
out downloading the distribution on this connection.
