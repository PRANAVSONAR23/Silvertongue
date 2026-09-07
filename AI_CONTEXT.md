# Silvertongue — start here

Entry point for any new session, human or agent. Read this file first, then open only the
documents you actually need. The goal is that nobody has to reconstruct context by reading
source file by file.

---

## What this is

An Android app that fixes your rushed WhatsApp messages in place.

You type a hurried message into WhatsApp. A small sparkle bubble floats over the WhatsApp UI.
Tap it, and 2–3 corrected rewrites appear. Tap one, and it replaces the text in WhatsApp's message
box. Hit send.

It is **not** a keyboard. You keep using Gboard. It is an accessibility service plus a floating
overlay window.

Sideloaded only. Debug signing. One user, one phone. No Play Store, no analytics, no accounts.

---

## Current state

**Working and verified end to end on real hardware.** All seven build stages from the original spec
pass on a POCO M2 Pro against real WhatsApp. 14 unit tests green.

| Piece | State |
|---|---|
| In-app test screen + Groq call | Working |
| Permission status + settings deep links | Working, MIUI-aware |
| Accessibility service reading WhatsApp | Working |
| Floating bubble show/hide | Working |
| Suggestion panel | Working, keyboard stays up |
| `ACTION_SET_TEXT` insertion | Working, `SET_TEXT` path |
| Drag, position memory, debounce, long-press hide | Working |
| Provider fallback on transient errors | Working, tested live |
| One-tap accessibility self-repair | Working |

---

## The document map

| Read this | When |
|---|---|
| **[README.md](README.md)** | Setting the app up: API keys, build, install, granting permissions, MIUI steps, troubleshooting "the bubble is missing" |
| **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** | Before changing structure. Layer boundaries, data flow, and every non-obvious design decision with its rationale |
| **[docs/TECHNICAL.md](docs/TECHNICAL.md)** | Before touching a subsystem. Implementation detail for the service, overlay, providers, prompt and settings |
| **[docs/GLOSSARY.md](docs/GLOSSARY.md)** | When a term is unfamiliar — Android accessibility and overlay vocabulary, plus project-specific names |
| **[docs/ENVIRONMENT.md](docs/ENVIRONMENT.md)** | Before building or touching the phone. Toolchain paths, device specs, adb workflow, automation gotchas |
| **[TESTING.md](TESTING.md)** | Before claiming something works. Per-stage verification and every bug found on hardware, with evidence |
| **[CHANGELOG.md](CHANGELOG.md)** | What changed and when |
| **[Paraphraser_spec.md](Paraphraser_spec.md)** | The original brief. Historical — where it disagrees with the code, the code is right and ARCHITECTURE.md explains why |

---

## Orientation in 60 seconds

Three pieces, one Gradle module, package `com.silvertongue.paraphraser`:

```
service/    ParaphraserAccessibilityService  — watches WhatsApp, reads and writes the message box
overlay/    OverlayController                — owns the WindowManager window (bubble + panel)
paraphrase/ ParaphraseRepository             — the LLM call, behind a pluggable provider interface
ui/         MainActivity + MainScreen        — setup screen, permissions, in-app test field
data/       SettingsRepository               — DataStore: provider choice, key overrides, bubble position
```

The service owns the overlay. The overlay calls the repository. The repository picks a provider.
Nothing flows the other way.

---

## Rules that will save you a day

These are load-bearing. Each one was learned by breaking it.

1. **Never let the overlay window take focus.** `FLAG_NOT_FOCUSABLE` stays set in *both* collapsed
   and expanded states. Taking focus moves input focus off WhatsApp's `EditText`, and text insertion
   then fails.
2. **Never cache an `AccessibilityNodeInfo` across an async boundary.** Re-resolve immediately before
   every read and write.
3. **Never trust a settings string for permission state.** A crashed or killed accessibility service
   stays listed in `ENABLED_ACCESSIBILITY_SERVICES`. Check the service's own liveness flag.
4. **Never test a model ID from memory.** Both providers shipped with model IDs that no longer
   existed. Call the live models endpoint before trusting one.
5. **Reinstalling resets both permissions.** After every `adb install -r`, re-grant the overlay
   app-op and re-bind the accessibility service.

---

## Known gaps and open work

Honest list of what is missing or fragile.

- **Anthropic provider is untested.** It compiles and is wired into the fallback chain, but no key
  has ever exercised it. Treat it as unverified.
- **The MIUI pop-up permission cannot be read.** `Settings.canDrawOverlays()` returns true while the
  ROM still blocks the overlay. The setup screen shows a caveat instead of a real check; there is no
  public API for it.
- **Prompt behaviour is not deterministic.** The Hinglish and time-reference rules are prompt-based.
  A model swap can regress them — rerun the regression cases in TESTING.md.
- **No instrumented tests.** Only JVM unit tests exist. Everything device-level was verified by hand.
- **Panel position is fixed** at 18% of screen height rather than anchored to the bubble, because
  IME window layering above `TYPE_APPLICATION_OVERLAY` was never confirmed.
- **English is the only UI language**, and the app targets `com.whatsapp` / `com.whatsapp.w4b` only.

---

## Working agreements

- No inline code comments. Names carry the meaning. Documentation lives in these files.
- Single Gradle module. Do not split it.
- Verify on the device before claiming a fix. `TESTING.md` says how.
- Update `CHANGELOG.md` in the same change.
