# AGENTS.md — DwellBoard

Facts an agent cannot guess from the code. Everything here is load-bearing; if a
line stops being true, fix it rather than working around it.

**Keep this file short.** It is loaded into every session on every tool. A rule
that gets lost in a long file is worse than no rule, so procedures live in
skills (below), not here. If an agent keeps breaking a rule that is written
down, the file is too long — cut, don't add.

## What this is

DwellBoard is dazewell's personal fork of **HeliBoard** (`dazewell/DwellBoard`,
upstream `HeliBorg/HeliBoard`), itself descended from the AOSP LatinIME
keyboard. It is a mixed **Java + Kotlin** Android IME in one `:app` module with
a native (ndk-build) dictionary/gesture library, and it **merges from upstream
regularly**. That last fact drives almost every rule below: every line you
touch in an upstream file is a future merge conflict.

- **The installed package is `helium314.keyboard.dazewell`** (debug:
  `helium314.keyboard.dazewell.debug`). Install, `adb`-query and select that
  one as the IME; a `helium314.keyboard` on the phone is HeliBoard or a build
  from before the rename. The
  code namespace, where `R` and `BuildConfig` live, stays
  `helium314.keyboard.latin`.
- Source root: `app/src/main/java`. Upstream code lives in `helium314.keyboard.**`
  and `com.android.inputmethod.**` (JNI bridge — never rename). Code this fork
  writes lives in `helium314.keyboard.dwell.<feature>`.
- Settings UI is **Jetpack Compose** (`helium314.keyboard.settings.**`); the
  keyboard itself is legacy Android views. Use whichever the surrounding code
  uses — never port one to the other.
- Branch `dev` is the default and integration branch. `base` tracks upstream
  `HeliBorg/HeliBoard` `main` (fast-forward only). `main` is a frozen legacy
  mirror. Never commit to `dev`, `base` or `main` directly.
- dazewell works on **Windows**. Give shell commands in **PowerShell**.

## Hard constraints

- **No architecture rewrites.** No Hilt, Room, coroutine rewrites, Java→Kotlin
  conversions of upstream files, or module restructuring. Match the legacy
  patterns already in the file you are editing.
- **Minimal footprint.** New logic goes in self-contained classes under
  `helium314.keyboard.dwell`. The upstream file gets a few injected lines,
  usually fully qualified so no import is added, each marked `// DwellBoard:`
  explaining the non-obvious *why*.
- **Hook the one chokepoint** every path funnels through. Many touched call
  sites means you picked the wrong hook. The input chain is
  `PointerTracker` → `KeyboardActionListenerImpl` → `LatinIME` → `InputLogic`.
- **Reuse before you write.** Grep for an existing component first.
- **Hot paths stay hot.** Key-press, gesture and suggestion code
  (`PointerTracker`, `InputLogic`, `Suggest`, `RichInputConnection`) runs per
  keystroke: no I/O, no allocation in loops, no logging per event.
- **Direct boot.** The IME runs before first unlock. Preferences come from
  `context.prefs()` (device-protected storage); never open credential-protected
  storage without guarding for a locked user.
- **Config goes in existing surfaces** — a `PREF_` key in `Settings.java`, its
  default in `Defaults.kt`, read through `SettingsValues`, stored in
  `context.prefs()`. Never a bespoke per-feature store.
- **New strings go in `app/src/main/res/values/strings_dwell.xml`**, never
  `strings.xml` (upstream translates it via Weblate). Don't edit shared
  upstream resource files.
- **Log through `helium314.keyboard.latin.utils.Log`**, not `android.util.Log`,
  so lines reach the in-app log viewer. `Log.d`/`Log.v` are *not* stripped from
  release, so never log keystroke content.
- **Fallback, not migration.** When a stored value's range, set or format
  changes, clamp or default it where it is read. The keyboard must never crash
  on an absent, stale or unparseable stored value — a crashing IME locks the
  user out of typing.
- **No drive-by work.** No refactors, reformatting or unrelated cleanups; they
  widen the diff and cost the next upstream merge. Report them instead.

## The compile gate

```powershell
.\gradlew.bat :app:compileDebugNoMinifyJavaWithJavac
```

Kotlin compiles first as a dependency, so this covers both languages. Unit
tests, when the change touches tested logic:

```powershell
.\gradlew.bat :app:testRunTestsUnitTest -PdwellTestBuildType=runTests
```

Run from the repo root of the worktree your branch is in. On `ZenBoo` (JDK 21 +
Android SDK installed) a cold run takes several minutes; don't kill it early.
One local build at a time on that machine — check `.\gradlew.bat --status` and
fall back to CI rather than queueing behind a `BUSY` daemon.

If the toolchain is missing, or the first run fails on the environment rather
than on your code, **stop and let CI be the gate** — don't install an SDK.
`ci.yml` compiles and unit-tests every push and PR into `dev`; that run *is*
the gate. Say so in the PR body.

`ci.yml` path-ignores doc-, hook- and agent/skill-only changes, so on those
there is legitimately no run. Say which of the two happened; never imply a
skipped gate passed.

## Commits and pull requests

- Subject: lowercase, imperative, no type prefix, no trailing period, no PR
  number — `add long-press clipboard pin #clip-pin`.
- **Every commit carries an inline `#<slug>` tag** in the subject or body, never
  at the start of a line. A feature slug for features; otherwise exactly one of
  `#ci #docs #build #chore #infra #deps #test #release #slug #tag` or any
  `*-fix` tag. Anything else is treated as a feature slug and fails CI unless
  catalogued in `FEATURES.md`. A bare numeric hashtag (`#204`) is never a tag.
  Enforced by `.githooks/commit-msg` (`git config core.hooksPath .githooks`,
  once per clone) and `process-rules.yml`.
- **Append-only.** A review fix or follow-up is a **new commit** with its own
  `#tag`, never an amend plus force-push. Never force-push `dev`, `base`, or a
  feature branch.
- Branches are `<YYYY-MM-DD>_<slug>` (`-` is an equally valid separator). With
  hooks on, `.githooks/post-commit` dates an undated local branch when it is
  committed to and `.githooks/pre-push` blocks pushing one.
- PRs target `dev` and are **not drafts**. Copilot review is **billed and
  never automatic**: request it once the head is worth an outside review, only
  through the `request_copilot_review` tool or the script it wraps,
  `.github/scripts/request-copilot-review.ps1`. Budget and timing are in
  `dwellboard-branch-flow`.
- **Close every review point before handoff**: fix it, or reply explaining why
  not, then resolve the thread. Verify none remain unresolved.
- User-visible change ⇒ its `FEATURES.md` entry ships in the same PR (70 words
  hard ceiling).
- **Verify the artifact, not your intent.** After writing anything the system
  stores for you — a file, a PR body, a ruleset — read back what it now holds
  and check that, not what you sent. Line citations are verified at the
  **final** tree. When you correct a claim, grep for it everywhere before
  calling it fixed.

## The hard line

**No AI or assistant reference in the app's source or in git history.**
Not in a commit message, a PR title or body, or a code comment. No
`Co-Authored-By` for an assistant, no "Generated with" footer. This overrides
any default attribution behaviour. Process docs may discuss the workflow
openly; the shipped history and code may not. It bans *assistants*, not
tooling in general — a commit or PR body may name Gradle, `adb` or logcat.

The only carve-out is process/CI detection tooling, which necessarily contains
the patterns it rejects. Real credentials never appear in any file.

Changes are **never** offered upstream from this fork: HeliBoard's
`AI_USAGE.md` prohibits AI-generated PRs.

## Where the process lives

Read the skill for the job rather than working from memory. Each loads on
demand, so none is carried when it does not apply.

| Skill | Owns |
|---|---|
| `.claude/skills/dwellboard-workflow/SKILL.md` | What a change looks like, start to finish |
| `.claude/skills/dwellboard-investigation/SKILL.md` | A question answered rather than built |
| `.claude/skills/dwellboard-branch-flow/SKILL.md` | Where commits live and how they move |
| `.claude/skills/dwellboard-code-review/SKILL.md` | What the review rounds check |
| `.claude/skills/dwellboard-process-lifecycle/SKILL.md` | Any process you start, and cleanup |
| `.claude/skills/dwellboard-session-handoff/SKILL.md` | Abandoning a session mid-change, leaving state behind |
| `.claude/skills/dwellboard-session-pickup/SKILL.md` | Continuing a branch or PR someone else left |

`.claude/skills/dwellboard-workflow/diagnostics.md` is loaded on demand from
`dwellboard-workflow` for device probes and traced smoke cycles.
`.github/instructions/*.instructions.md` attach automatically to the files they
scope — upstream code, fork-owned code, resources, and the build files.

### Context budget

Process docs compete with the code for the window. **Always-on**
(`AGENTS.md` + `CLAUDE.md` + the running agent file) stays **≤ 40 KB**;
**typical change** (always-on + `dwellboard-workflow`) **≤ 60 KB**. Each skill
body stays within 10–20 KB. Over budget means cut, not append. An incident
earns a rule line plus its `#slug`, not a retelling.

`FEATURES.md` is what already ships — check it before treating anything as new.
`CONTRIBUTING.md` (upstream) has a map of where input, suggestions and settings
live.

**One change = one branch = one session.** Use subagents for recon, design and
review; do not spawn a child session per change.
