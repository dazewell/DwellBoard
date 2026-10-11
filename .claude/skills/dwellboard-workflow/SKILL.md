---
name: dwellboard-workflow
description: "Dazewell's process for adding or changing anything in the DwellBoard repo (a personal HeliBoard keyboard fork, GitHub dazewell/DwellBoard). Trigger this whenever work touches that repo: adding a feature, fixing a bug, preparing a commit or PR for it, or when dazewell references \"the usual process\" for DwellBoard. Also trigger when he gives new or corrected guidance about how this workflow should run — this file is meant to be edited, not just read. Covers: the one-change-one-session rule and why child sessions were dropped, the reuse-first / minimal-footprint hook style with concrete hook points and config surfaces, the two review rounds, the compile gate and its CI fallback, the fallback-not-migration rule, temporary diagnostics and the smoke build, the FEATURES.md entry, on-device testing, and when a PR actually gets opened."
---

# DwellBoard contribution workflow

`AGENTS.md` holds the repo's facts and hard rules — read it first, and do not
expect them repeated here. This file is the **procedure**: what a change looks
like, in order, and the concrete addresses to build it at.

## One change, one branch, one session

A change runs start to finish in **one session**. Recon, design, implementation,
review and handback share one trace, because the decisions made early have to
still be available to the code written late.

**Do not spawn a child session per change.** A child cannot see the conversation
that produced it, so every constraint has to be re-serialised into a brief — and
what a brief drops is exactly what an implementer needs: the `file:line`
citations and the reasoning behind the rejected alternative. Coordination across
sessions was the single largest source of lost time on the sibling fork this
process came from, ahead of any bug.

**Subagents are how you buy context back.** `dwellboard-scout` for read-only
recon, `dwellboard-ux` for behaviour and placement, `dwellboard-architect` for
both review rounds. They report into your trace without flooding it, and none
can commit. Give each an objective, an output format and explicit boundaries.
The vendored audit agents in `.github/agents/` are not part of this pipeline.

Two or more **genuinely independent** changes at once — disjoint files, disjoint
hooks, no ordering between them — can run as parallel sessions dazewell starts
himself. If they share an upstream file or a hook, they are not independent: run
them one at a time in one session.

## The pipeline, in order

### 1. Recon, then design review (round 1 of 2)

Dispatch `dwellboard-scout` and check `FEATURES.md` before asking dazewell
anything — and remember HeliBoard itself ships a lot behind settings. **If it
already ships, stop and say so** — that is a good outcome. For a user-visible
change, run `dwellboard-ux` next so the questions you ask are about real design
forks rather than mechanics.

Then put the plan to `dwellboard-architect` (round 1) **before writing a line**:
does this fight the architecture, will it survive the next upstream merge, is
there a simpler hook point, does something equivalent already ship? The persona
and the checklist live in `dwellboard-code-review`. Do not start implementing
until you and that reviewer are aligned — a plan defect caught here costs a
paragraph; caught after implementation it costs the branch.

**Proportionality.** If you could describe the diff in one sentence, skip the
plan and round 1. Over-process is a real failure, not a safe default.

**A design gate for stateful or concurrent changes — a real gate, not a note.**
If recon *or implementation* reveals the change touches a cache, asynchronous
work, or invalidation — any two of those, or any one plus multi-threading —
implementation **stops** until a short state-and-interleaving spec goes through
review: what state exists, who writes it, on which thread, what clears it, and
which interleavings matter. Route it through round 1, or a **round 1.5** the
moment the risky part surfaces mid-implementation, because **a design review
conducted before the hard part existed has not reviewed the hard part.** Say
that plainly rather than assuming round 1 covered it.

**State the trade-off budget in one line:** what may be spent for correctness —
an extra lookup, some memory, a slower rare path. **Never** extra work per
keystroke. Without an explicit budget an implementer optimizes by default, then
defends that optimization through several review rounds arguing a false economy.

**Cost optional slices separately.** Name the implementation, review, build or
device-test overhead unique to each optional or distinct-risk subfeature, never
hidden inside a total. Mid-flight, **stop and report** if a slice reopens
design, introduces a mechanism serving only itself, accumulates repeated
Critical/Important findings, or costs an extra device cycle — give its unique
cost, whether the rest is healthy, and the options: keep, simplify, substitute,
drop. Whether it is worth it is dazewell's product decision, not yours.

### 2. Branch

Cut a short-lived branch off `dev`, named `<YYYY-MM-DD>_<short-slug>` — **the
date prefix is mandatory**; `_` or `-` between date and slug, hyphens inside
the slug. `.github/scripts/new-branch-name.ps1 <slug>` prints one. An undated
`clip-pin` is wrong: fix it before the first push. One change per branch.

Topology (`dev`, `base`, `main`), the `#tag` every commit carries, how upstream
reaches `dev` (by hand, via `base`) and the no-force-push rule are normative in
`dwellboard-branch-flow`.

### 3. Implement with minimum footprint

Upstream files move as little as possible, so the next upstream merge stays
cheap.

**Reuse before you build.** Grep for an existing component, helper, flow or
pattern that already does the thing. Key behaviour goes through existing key
codes (`keyboard/internal/keyboard_parser/floris/KeyCode.kt`) and toolbar keys
(`ToolbarKey` in `latin/utils/ToolbarUtils.kt`); clipboard through
`latin/ClipboardHistoryManager.kt`; settings rows through the composables in
`helium314.keyboard.settings.preferences` (e.g. `SwitchPreference`). Write new
code only when nothing fits — and say so.

**Hook the chokepoint.** For anything touching a core flow, find the single
place all paths funnel through rather than patching many call sites. Input runs
`PointerTracker` → `KeyboardActionListenerImpl.onCodeInput` →
`LatinIME.onCodeInput` / `onTextInput` → `InputLogic`; hook as late in that
chain as still sees every source you need (touch, gesture, hardware key,
toolbar).

**Design as hooks.** New logic goes in self-contained classes under
`helium314.keyboard.dwell.<feature>`. The upstream file gets a few injected
lines, usually **fully qualified so no import is added**, each marked
`// DwellBoard:` explaining the non-obvious *why*. Mirror an existing fork
feature when adding a similar one. Use whichever UI toolkit the surrounding code
uses: Compose for settings, legacy views for the keyboard — never port one to
the other.

**Common hook points** (paths under `app/src/main/java/helium314/keyboard/`):

- `latin/LatinIME.java` — the `InputMethodService`: `onStartInputView`,
  `onUpdateSelection`, `onCodeInput`, `onTextInput`; editor lifecycle and
  `EditorInfo`.
- `latin/inputlogic/InputLogic.java` — what a code point or event does to the
  text; composing state; **hot path**.
- `latin/RichInputConnection.java` — every read/write to the editor; behaviour
  varies by app and OS version; **hot path**, upstream's most dangerous area.
- `keyboard/KeyboardActionListenerImpl.kt` — key actions before `LatinIME`.
- `keyboard/KeyboardSwitcher.java`, `keyboard/MainKeyboardView.java` —
  which keyboard/view is shown; rebuilt on layout and theme change.
- `latin/Suggest.kt`, `latin/DictionaryFacilitatorImpl.kt`,
  `latin/suggestions/SuggestionStripView.kt` — suggestion creation to display.
- `app/src/main/assets/layouts` + `keyboard/internal/keyboard_parser/` —
  layouts and popups; prefer a layout/data change over code when it suffices.
- `settings/screens/*Screen.kt` — Compose settings screens.

**Config & persistence** — existing surfaces only:

- A `PREF_` key in `latin/settings/Settings.java`, its default in
  `latin/settings/Defaults.kt`, the parsed value as a field on
  `latin/settings/SettingsValues.java` (read via `Settings.getValues()` on the
  input path — never `prefs()` per keystroke).
- Storage is `context.prefs()` (`latin/utils/Ktx.kt`), which is
  device-protected and readable before first unlock. Never a bespoke store, and
  never credential-protected storage without a locked-user guard.

**Fork-owned resources.** New strings go in
`app/src/main/res/values/strings_dwell.xml`, never `strings.xml`. Reuse existing
drawables and strings where one fits.

**Fall back, don't migrate.** When a stored value's range, set or format
changes, clamp it to the nearest valid value or replace it with a default **at
the point it is read**. No data rewrite, no versioned migration, no per-value
grandfathering. The floor this does not lower: the keyboard must not crash on an
out-of-range, absent or unparseable stored value, anywhere it is read — a
crashing IME locks the user out of typing. What it lowers is the obligation to
preserve the old value's *meaning*.

Migration is the exception and must be argued for, with its cost stated the
moment it is proposed — justified only when falling back loses something the
user would actually miss and could not trivially recreate. Preservation
machinery is the part that breaks: a clamp is one expression at a read site,
while preserving an out-of-range value means tracking which values are legacy,
keeping them distinguishable, and stopping every write path from laundering one
into the other.

**Target the diffstat of a comparable feature.** `git show --stat <commit>` on
the nearest equivalent: a handful of files, most of the diff in new code, only a
few lines touching anything upstream.

### 3a. Temporary diagnostics for a new decision point

When the change adds a decision point that determines whether something is
shown, or which of several paths handles the same input, **instrument it**. The
same applies when the effect is gated on state you never watched happen — a
popup still open, an input view not yet started. Reading the diff cannot answer
"which path actually executed on the device"; a log line answers it in seconds.
The rules — what may and may not be logged (never keystroke content), logging
through `helium314.keyboard.latin.utils.Log`, where to place a probe, the
`DWELL_SMOKE_<slug>` tag literal, the four marker classes, and the obligation to
remove them in their own commit — are in [`diagnostics.md`](diagnostics.md).
Read it before planting a probe.

This is proportional: a change with no user-visible surface earns none of it.

### 4. Compile gate

The command (`.\gradlew.bat :app:compileDebugNoMinifyJavaWithJavac`, plus
`:app:testRunTestsUnitTest -PdwellTestBuildType=runTests` when tested logic moved), the machine budgets and
the CI fallback are in `AGENTS.md`. Run it in the worktree the change lives in,
so it compiles the actual branch. Don't move on until it is clean, and never
claim you compiled something you did not — show the command and the tail of its
output.

Gradle daemon and cleanup obligations are normative in
`dwellboard-process-lifecycle`.

### 5. Code review (round 2 of 2)

Once it compiles — or once it is pushed and `ci.yml` is the gate — take the
**real diff** back to `dwellboard-architect`. Run round 2 on a **different model
family** from the one that wrote the code: a model tends to be blind to its own
mistakes in the same places.

Then review **the whole, on the final state of the code** — not the lines, and
not the diff. A change that arrived as a dozen small fixes reads fine
hunk-by-hunk and can still leave a muddled state machine. What triggers this is
what *happened*, not just up-front size: repeated fixes in one region, a design
that shifted mid-flight, or several review rounds. The repeated-fix trigger is
defined in `dwellboard-code-review`.

Verify each finding before implementing it — a reviewer can be wrong for *this*
codebase. Push back with `file:line` evidence rather than performative
agreement; when a finding is right, just fix it. **A reviewer's prescribed fix
is binding**: implement the named mechanism or contest it with evidence before
shipping a different one. Silently shipping a cleverer variant of a rejected
approach is how one finding becomes three review rounds.

### 6. FEATURES.md rides along

**User-visible ⇒ a `FEATURES.md` entry in the same PR**, under the right
`## section`, with a `### Feature name` heading marked `<!-- #slug -->`. Plain
prose in dazewell's voice, matching its neighbours — read three before writing.

**70 words, hard ceiling — count them.** Prose under the heading; images and
shortcut tables excluded. Most entries are ~35. Two beats: what it does and
where you find it, then its setting and default. Cut edge cases, failure
behaviour, interaction step-by-steps, storage and lifetime detail, rationale,
and how it used to work. A sentence opening with *unless*, *except* or *note
that* comes out; one caveat survives only if a user would misuse the feature
without it.

If your feature extends one that already has an entry, add your `<!-- #slug -->`
to that heading and fold the behaviour in — then re-count the merged entry and
cut old detail. **When you shorten an entry, re-check every surviving sentence
against the code**, not just the ones you rewrote: dropping a qualifier is the
cheapest way to lose words and the easiest way to make a sentence false.

### 7. Commit

Subject style, the `#<slug>` tag and the append-only rule are in `AGENTS.md`;
the exempt set and the full protocol are in `dwellboard-branch-flow`.

### 8. Merge-forward, don't rebase in the loop

`dev` is the trunk. Bring `dev` into your branch when you need what landed
there; never rebase a pushed branch. Details in `dwellboard-branch-flow`.

### 9. Open a PR into `dev` — then build the APK

For a user-visible feature this is a standing step, not something to wait to be
told: once the change is ready, **commit → push → open the PR**, so dazewell
always has a route to a test build. Opening it and every later push triggers
`ci.yml` (compile + unit tests; an R8 deep job when build files change) and
`process-rules.yml` (branch name + commit tags). Neither produces an APK.

The APK comes from **`staging.yml`**. Request it with
`.\.github\scripts\request-apk-build.ps1 -PullRequest <n> -Wait` (it applies the
**`build-apk`** label to the PR, reuses an existing build for the head, and
returns the artifact link) — never label by hand
(it also runs on every push to `dev` and on manual dispatch). It uploads the
`.apk` unzipped as a **GitHub Actions artifact**; a push to `dev` also replaces
`DwellBoard.apk` on the rolling **`staging`** pre-release (the README link).
Nothing is sent anywhere else. `gh run download` unpacks the APK (it is a zip),
so fetch it with the artifact-API `curl -o` recipe in `dwellboard-branch-flow`
(to a dir outside the repo) and install with `adb install -r <apk>`. Every
staging build (PR label, dev push, dispatch) is the release variant signed with
the committed `app/release.keystore` (passwords in the `LOCAL_PROPERTIES`
secret / gitignored `local.properties`): it installs as `helium314.keyboard` and
updates the installed build in place. Only if the secret is missing does it fall
back to the debug variant, `helium314.keyboard.debug`, with a throwaway key. If
the phone has a differently-signed build under that id the install fails, and only uninstalling resolves it — ask before doing that.
A local alternative is `.\gradlew.bat :app:assembleDebug`, which installs
side by side as `helium314.keyboard.debug`; say which one you handed over.

PR mechanics — requesting the billed Copilot review, its budget, the severity
floor, and thread resolution — are normative in
`.github/agents/dwellboard-implementer.agent.md` and are not restated here.

**Request the verification build only once review has settled** — round 2 clean,
any final-state pass clean, nothing Important or above outstanding. An APK
requested earlier is stale the moment a later round finds a Critical, and
dazewell's time on a stale build is the most expensive waste in the pipeline.

**A UI-facing change earns a second, earlier build first — the smoke build.**
Review reads the diff; it cannot see whether a control's precondition is ever
true on a device. So for anything a user can see or tap, once it compiles,
request a build and ask **one** question: does the control appear, and can you
reach it? Not correctness, not edge cases — reachability only. Round 2 and any
final-state pass run **after** that comes back positive; there is no point
reviewing the craftsmanship of code nobody can reach.

The smoke build is disposable by design and is never described as a build to
verify behaviour against. A UI-facing change costs at most two builds, one per
purpose, never two for the same purpose. A change with no user-visible surface
gets no smoke build at all.

**When a build is up, ask for the test explicitly — never bury it in a
handback.** A test request is a *blocking* request for dazewell's hands, and
prose he has to read to the end to discover is prose he may not reach for hours.
Issue an explicit `ask_user` prompt: it interrupts deliberately, stays visibly
unanswered until he acts, and his reply comes back attached to the question.

Make it **specific and short**: which build to install (artifact and the PR or
commit it came from, or the debug APK), the exact thing to try, and what a pass
versus a fail looks like. "Please test the build" wastes the interruption;
"Install the staging artifact from PR #12, copy three things, open clipboard
history and long-press the second — a pin option should appear and survive a
reboot" spends it well. **One round at a time** — a list of six checks is a
task, not a question, and it stalls. The same applies to any request for his
hands or eyes, such as a screenshot for `FEATURES.md`.

### 9a. ADB-traced smoke cycles

**Local `adb` tooling is always available** — an invariant, not a condition to
test. Only device *connectivity* is optional, so offer a traced cycle whenever
the markers exist, and treat only a disconnected phone as the reason you cannot
read one. The capture protocol, IME selection, the retention and privacy rules,
the three independent cleanup checks and the evidence-grading labels are in
[`diagnostics.md`](diagnostics.md); the process mechanics are normative in
`dwellboard-process-lifecycle`.

### 10. Land it

Landing into `dev` is dazewell's decision by default. The merge procedure, and
the narrow conditional authority under which a session holding a named approval
may press the button, are normative in `dwellboard-branch-flow`. **Never write
"ready to merge"** — nothing in this pipeline establishes it, because nobody
typed on the build. Say what you actually verified. Nothing is ever offered
upstream: HeliBoard's `AI_USAGE.md` forbids AI-generated PRs.

## Priorities: protect the irreplaceable thing first

1. **Risk of losing or corrupting the irreplaceable thing** — what the user
   typed into the editor, their clipboard history, personal dictionary, custom
   layouts and settings. Nothing outranks this.
2. **Behavioural correctness the user would notice** — right output, no crash,
   no typing lag, no regression in an existing flow.
3. **Maintainability and elegance** — lean code, a clean state machine, fewer
   moving parts, a diff the next upstream merge survives.

When two conflict, the lower number wins, and say which you traded.

## Verifying a claim about the system

Prefer evidence over inference. A claim about what the code does is settled by
reading the code at a `file:line`, not by reasoning about what it probably does.
A claim about what the *device* does is settled by a log line, not by reading
the diff. When you cannot settle it either way, say so — an uncited "this is
immune by construction" is treated as false. The editor applies
`InputConnection` edits asynchronously and some apps ignore or reorder them, so
any claim about what the text field holds after a call needs evidence, not
reasoning.

## Keeping this current

This file is meant to be edited. When dazewell corrects how the workflow should
run, change it here in the same session, and fix anything it contradicts rather
than leaving both. The bias is **subtraction**: this file is read in full every
time it is loaded, and a rule lost inside a long file is worse than no rule. If
an agent keeps breaking a rule that is written down, treat the length as the
cause.
