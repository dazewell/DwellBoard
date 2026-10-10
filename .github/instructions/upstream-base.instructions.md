---
applyTo: "app/src/main/{java/helium314/keyboard/{accessibility,compat,dictionarypack,event,keyboard,latin,settings},java/com/android,jni}/**"
---

# You are editing an upstream file

Everything outside `helium314.keyboard.dwell.**` comes from upstream HeliBoard
(and before it, AOSP LatinIME). This fork merges upstream regularly, and
**every line you add here is a line that has to be re-applied by hand at the
next merge.** Treat the diffstat as the cost.

- **Add as few lines as possible.** The target is a handful of injected lines
  per file, not a refactor. If you find yourself restructuring an upstream
  method, you are in the wrong file — put the logic in a fork-owned class under
  `helium314.keyboard.dwell` and call into it.
- **Prefer a fully-qualified call over a new import.** A new import line is one
  more conflict hunk; `helium314.keyboard.dwell.clippin.ClipPin.onKey(code)`
  inline is zero.
- **Mark every injection `// DwellBoard:`** with the non-obvious *why*, not the
  what. The next person reading it is resolving a conflict and needs to know
  whether the hunk still applies.
- **Hook the chokepoint, not the call sites.** Key input funnels
  `PointerTracker` → `KeyboardActionListenerImpl` → `LatinIME` → `InputLogic`;
  suggestions `DictionaryFacilitatorImpl` → `Suggest` → `SuggestionStripView`;
  text-field I/O goes through `RichInputConnection`.
- **Hot paths stay hot.** `PointerTracker`, `InputLogic`, `Suggest` and
  `RichInputConnection` run per keystroke — no I/O, no allocation in loops, no
  per-event logging. They are also where upstream warns unintended effects
  hide; behaviour can differ per host app and OS version.
- **Don't reformat, re-order, or tidy anything you did not have to touch.**
  Whitespace churn in an upstream file is a conflict with no upside.
- **Never delete upstream code to make room.** Guard it instead.
- **Don't convert languages.** A Java file stays Java, a Compose screen stays
  Compose, a legacy view stays a view.
- **`com.android.inputmethod.**` is the JNI bridge.** Its class and method
  names are bound from native code; never rename or move them.

Full rules: `AGENTS.md` and the `dwellboard-workflow` skill.
