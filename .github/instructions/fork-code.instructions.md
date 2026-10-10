---
applyTo: "app/src/main/java/helium314/keyboard/dwell/**"
---

# You are editing fork-owned code

`helium314.keyboard.dwell.**` belongs to the fork, so the upstream-merge
pressure that constrains the rest of `app/src/main` does not apply. This is
where new logic belongs.

- **Self-contained feature classes.** A feature gets its own package —
  `helium314.keyboard.dwell.<feature>` — exposing a small surface the upstream
  file can call in one line. Mirror the nearest existing feature rather than
  inventing a new layout.
- **Reuse before you build.** Grep for an existing helper, dialog, preference
  composable or utility (`helium314.keyboard.latin.utils`,
  `helium314.keyboard.settings.preferences`, `...settings.dialogs`) that already
  does the thing.
- **Config goes in the existing surface**, never a new one: a `PREF_` key in
  `latin/settings/Settings.java`, its default in `latin/settings/Defaults.kt`,
  read through `SettingsValues` (or `context.prefs()` off the hot path). A
  setting's UI is a Compose `Setting` on the matching screen in
  `helium314.keyboard.settings.screens`.
- **Direct boot.** `context.prefs()` is device-protected storage and works
  before first unlock; anything else must tolerate a locked user.
- **Fall back, don't migrate.** When a stored value's range or format changes,
  clamp or default it *at the read site*. No versioned migration. The keyboard
  must never crash on an absent, unparseable or out-of-range stored value.
- **Kotlin preferred for new files**, Java 17 target. No Hilt, no Room, no new
  architecture libraries. Compose only for settings UI.
- **Log via `helium314.keyboard.latin.utils.Log`**, never keystroke content.
- **Threading is explicit.** UI work on the main thread, storage and network
  off it. State written from more than one thread needs a stated interleaving
  story before it is written, not after review finds it.
- **Testable logic is tested.** Pure logic gets a JUnit/Robolectric test under
  `app/src/test/java/helium314/keyboard/dwell/`.

Full rules: `AGENTS.md` and the `dwellboard-workflow` skill.
