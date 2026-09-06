# Project Spec: WhatsApp Paraphraser Overlay (Android)

You are building a complete, working Android app from this spec. Build it end to end. Ask me only if something here is genuinely ambiguous — otherwise make a sensible choice and keep going.

## 1. What the app does

I type broken / rushed English into WhatsApp's message box. I tap a small floating button that sits over the WhatsApp UI. The app reads what I've typed, sends it to an LLM, and shows me 2–3 grammatically corrected, naturally phrased versions. I tap one, and it replaces the text in the WhatsApp input box. I hit send.

The app does **not** implement a keyboard. I keep using Gboard. The app is an accessibility service plus a floating overlay.

## 2. Hard constraints

- Kotlin, Jetpack Compose for any in-app UI. The floating overlay itself uses `WindowManager` + Compose (`ComposeView` with a `ViewTreeLifecycleOwner`/`SavedStateRegistryOwner` attached — this is the part that usually breaks, get it right).
- Min SDK 26, target SDK 34+.
- Sideloaded only. No Play Store. No signing config beyond debug.
- No inline code comments. Clean, minimal implementations. Self-explanatory names instead of comments.
- Do not use placeholder or nonsense variable names.
- Single-module app. Do not over-engineer into 6 Gradle modules.

## 3. Architecture

Three pieces:

**A. `ParaphraserAccessibilityService`** — extends `AccessibilityService`.
- Config XML: `canRetrieveWindowContent="true"`, `android:accessibilityEventTypes="typeViewFocused|typeWindowStateChanged|typeViewTextChanged"`, `android:packageNames="com.whatsapp"`, `android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"`.
- Tracks the currently focused editable `AccessibilityNodeInfo`. Keep a reference, refresh it on each relevant event, and always `recycle()` correctly / re-fetch rather than holding a stale node.
- Shows the overlay when a WhatsApp editable field is focused; hides it when it isn't.
- Exposes two operations used by the overlay: read current text from the focused node, and write replacement text via `performAction(ACTION_SET_TEXT, bundle)` with `ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE`.

**B. `OverlayController`** — owns the `WindowManager` views.
- Requires `SYSTEM_ALERT_WINDOW`. Window type `TYPE_APPLICATION_OVERLAY`.
- Collapsed state: a small circular draggable button (sparkle icon), remembers its last position.
- Expanded state: a panel showing loading state, then the suggestion chips, then a dismiss affordance. Tapping outside collapses it.
- Flags: `FLAG_NOT_FOCUSABLE` when collapsed so it never steals keyboard focus. When expanded, it must still not close the keyboard or the WhatsApp field — this is critical, since losing focus on the target node breaks text insertion. Cache the node reference *before* expanding.

**C. `ParaphraseRepository`** — the LLM call.
- Retrofit + OkHttp + kotlinx.serialization, or plain OkHttp if that's leaner. Coroutines, not callbacks.
- Provider is pluggable behind an interface `ParaphraseProvider` with a single suspend function taking raw text and returning `List<String>`.
- Ship two implementations: **Groq** (default, free tier, fast) and **Google Gemini** (free tier fallback). Also stub an **Anthropic** implementation behind the same interface.
- API keys are read from `local.properties` and exposed via `BuildConfig` fields. Never hardcode a key in source. Add `local.properties` to `.gitignore` and document the required key names in the README.

## 4. Prompt design

The provider sends a system prompt roughly along these lines (write your own, this is the intent):

- Rewrite the user's raw message into 2–3 alternatives.
- Fix grammar, spelling, sentence structure.
- **Preserve the original meaning, tone, and level of formality.** Casual stays casual. Do not turn "yaar can u send that file" into corporate English.
- Keep them short. This is a chat message, not an essay.
- Do not add greetings, sign-offs, or content the user didn't imply.
- Return strict JSON only: `{"suggestions": ["...", "...", "..."]}`. No markdown fences, no preamble.

Parsing must be defensive: strip ``` fences if present, and fall back gracefully if JSON is malformed (show an error chip rather than crashing).

## 5. UX details that matter

- Round trip must feel fast. Show a loading shimmer within 50ms of tap. Use a small/fast model (e.g. Llama 3.1 8B on Groq, or Gemini Flash).
- If the input box is empty, the button should show a brief "nothing to rewrite" toast rather than calling the API.
- Debounce: ignore repeat taps while a request is in flight.
- Suggestions should be fully visible — chat messages can be a few lines, so let chips wrap to multiple lines rather than truncating with ellipsis.
- After inserting a suggestion, collapse the overlay automatically so I can hit send immediately.
- Long-press the floating button to temporarily hide the overlay until the next time WhatsApp is opened.

## 6. Main app screen (launcher activity)

A single Compose screen that:
- Shows setup status for the two permissions, each with a button deep-linking to the right settings page: `Settings.ACTION_ACCESSIBILITY_SETTINGS` and `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- Live-updates the status when I come back from settings (check in `onResume`).
- Has a text field where I can paste a sentence and test the paraphrase call directly, without WhatsApp. This is important — I want to validate the prompt and API wiring before dealing with accessibility plumbing.
- Lets me pick the active provider (Groq / Gemini) and shows whether a key is configured.

## 7. Build order

Build and make runnable in this order, so I can verify each stage:

1. Project scaffold + the launcher screen with the in-app test field, wired to a working Groq call. **This must run and paraphrase text before you touch accessibility.**
2. Permission status UI and the settings deep links.
3. Accessibility service that logs the focused node's text from WhatsApp. Verify via logcat.
4. Floating overlay button that appears/disappears with WhatsApp field focus.
5. Wire the button to the repository and render suggestion chips.
6. `ACTION_SET_TEXT` insertion back into WhatsApp.
7. Polish: dragging, position memory, debounce, error states.

## 8. Deliverables

- Full working Gradle project.
- `README.md` with: exact steps to get a free Groq API key, where to put it in `local.properties`, the `./gradlew assembleDebug` command, the `adb install` command, and the on-device steps to grant both permissions and enable the service.
- A short `TESTING.md` describing how to verify each of the 7 build stages above.

## 9. Known pitfalls — handle these explicitly

- Compose inside a `WindowManager` overlay crashes unless lifecycle, saved-state, and view-model store owners are attached to the `ComposeView`. Implement a small `OverlayLifecycleOwner` for this.
- `AccessibilityNodeInfo` references go stale fast. Re-resolve the focused node right before writing text rather than trusting a reference cached at expand time; fall back to `findFocus(FOCUS_INPUT)` on the root node.
- WhatsApp's message field is a standard `EditText` and supports `ACTION_SET_TEXT`, but confirm at runtime and log clearly if the action isn't in the node's action list.
- `TYPE_APPLICATION_OVERLAY` on Android 12+ needs the overlay permission granted before the service tries to add a view — guard against `WindowManager.BadTokenException`.
- Don't request `TYPE_VIEW_TEXT_CHANGED` events at high frequency without throttling; it will spam and drain battery.

## 10. Non-goals

Not building: a custom keyboard/IME, multi-language support, chat history, cloud sync, analytics, or Play Store compliance. Keep it focused.