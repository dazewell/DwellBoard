---
applyTo: "app/src/main/{res,assets}/**"
---

# You are editing resources

Almost all of this tree is upstream. Adding to the fork's own files is free;
editing an upstream one costs a merge conflict.

- **New strings go in `values/strings_dwell.xml`.** Never `strings.xml` — that
  is upstream's file and its translations are managed through Weblate.
- **Don't touch translations.** Only the default `values/` locale is authored
  here; never add `values-xx/strings_dwell.xml` by hand.
- **Reuse an existing drawable or string** where one fits. A new asset should
  be the exception you can justify.
- **Layouts and locale data are upstream content.** `assets/layouts/**`,
  `assets/locale_key_texts/**` and dictionaries are parsed by `KeyboardParser`
  / `TextKeyData`; changing one changes every user of that layout. Add a new
  file rather than editing a shared one when the change is fork-only.
- **Match the surrounding naming.** Look at neighbouring entries in
  `strings_dwell.xml` before inventing a key; prefix fork-only keys `dwell_`.

Full rules: `AGENTS.md` and the `dwellboard-workflow` skill.
