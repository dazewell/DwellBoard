# Device diagnostics and traced smoke cycles

Reference for the `dwellboard-workflow` skill. Load this when a change adds a
decision point you cannot verify by reading the diff, or when you are running an
ADB-traced smoke cycle. Everything here is normative; the pipeline steps that
invoke it are steps 3a and 9a in `SKILL.md`.

## Temporary diagnostics for a new decision point

When the change adds a decision point that determines whether something is
shown, or which of several paths handles the same input, instrument it. Reading
the diff cannot answer "which path actually executed on the device"; a log line
answers it in seconds. A feature that passed the compile gate, an automated
review and two architect rounds can still ship unreachable, because all of
those reason about the diff and none can see the device state that picks the
branch.

- **Log only what identifies the path** — booleans, enum and state names, key
  *types*, counts, `EditorInfo.inputType` and the editor's package name.
  **Never** typed text, composing word, suggestions, clipboard content,
  dictionary words, or the code point of a character key — a keyboard sees
  passwords. If a value cannot be logged safely, log that the branch was taken
  instead.
- **Log through `helium314.keyboard.latin.utils.Log`**, not `android.util.Log`.
  It writes to logcat *and* to the in-app log buffer that **About → Save log**
  and crash reports include, so a probe is readable even without `adb`.
  Use `Log.e` / `Log.w` / `Log.i`; `Log.d`/`Log.v` are noise-filtered by
  many logcat setups and add nothing.
- **Instrument where you are uncertain, not inside the path you expect.** A
  probe in an assumed path yields silence when the assumption is wrong, and
  silence is indistinguishable from broken tooling. When the question is "which
  path ran", log a stack trace at the observed symptom —
  `Log.e(TAG, "<label>", Throwable())` — which names the real call chain in
  one run where a guessed probe takes several.
- **Never on a hot path un-gated.** Per-keystroke code (`InputLogic`,
  `RichInputConnection`, `PointerTracker`, `onDraw`, suggestion updates) gets a
  one-shot or rate-limited probe at most. Each probe answers one predeclared
  uncertainty.
- **Direct boot:** a probe must not touch credential-protected storage or
  anything unavailable before first unlock — it would crash the keyboard on the
  lock screen.
- **Own commit, single tag literal embedded in the log message text itself**
  (`DWELL_SMOKE_<slug>`), written verbatim in the PR body. Verification greps the
  tree for that string, so a tag living only in a commit message is a check
  against nothing.
- **It comes back out as a new commit**, never folded into a feature commit,
  once the smoke build has answered the reachability question.

**Marker discipline for a traced cycle.** Declare all four marker classes before
capture, all sharing the `DWELL_SMOKE_<slug>` prefix: a liveness/BEGIN marker at
an unconditionally-reached point (e.g. `LatinIME.onStartInputView`) carrying
build identity (`BuildConfig.VERSION_NAME`, `BuildConfig.APPLICATION_ID`) and
the scenario id; the expected path marker(s); the forbidden/competing path
marker(s); and an END marker. State expected counts and order **before** reading
the log. Plant them unconditionally whenever diagnostics are required — device
connectivity decides whether the trace can be *read*, not whether it exists.

This is proportional: a change with no user-visible surface earns none of it.

## ADB-traced smoke cycles

**Local `adb` tooling is always available** — an invariant, not a condition to
test. Only device *connectivity* is optional, so offer a traced cycle whenever
the markers exist, and treat only a disconnected phone as the reason you cannot
read one.

**Getting the build on and selected.** Install the staging artifact
(`gh run download`) or a local `.\gradlew.bat :app:assembleDebug` with
`adb install -r <apk>`. Debug installs as `helium314.keyboard.dazewell.debug`, release as
`helium314.keyboard.dazewell`. An installed IME is not active until enabled and selected:

```powershell
adb shell ime list -s                      # enabled IMEs
adb shell ime enable helium314.keyboard.dazewell.debug/helium314.keyboard.latin.LatinIME
adb shell ime set    helium314.keyboard.dazewell.debug/helium314.keyboard.latin.LatinIME
```

(Use `helium314.keyboard.dazewell/...` for a release-style build.) Restore dazewell's
previous default IME afterwards if you changed it — note it from
`adb shell settings get secure default_input_method` before switching.

**ADB process mechanics live in `dwellboard-process-lifecycle`** and are not
restated here: pinning the target serial, holding the exact process identity,
keeping the capture's working directory and output under an absolute `$env:TEMP`
path outside every worktree, the bounded-capture protocol (declared wall-clock
deadline, a tool wait longer than it, stop on END-marker-or-deadline), prompt
stop with bounded verification on success/failure/cancellation/timeout, never
stopping by process name, never an unqualified `adb kill-server`, the
capture-artifact cleanup obligation, and the ledger entry.

Prefer `adb logcat --pid <pid>` (from `adb shell pidof <package>`) or a
timestamp-bounded `adb logcat -T '<time>'` over `logcat -c`, which destroys the
device's buffer for every other consumer. Detect a truncated capture by the END
marker's absence, not by a byte-count guess. Without `adb`, the in-app log
(enable debug settings by tapping the version in **About**, then
**Settings → Advanced → Debug**; or **About → Save log**) carries the same
marker lines.

**Collateral scope is DwellBoard, not the phone.** The host-side filter may add
a narrow allowlist of system tags naming the package (`AndroidRuntime`,
`ActivityManager`, `InputMethodManagerService`, ANR and tombstone lines) —
nothing broader, because raw ambient logcat carries other apps' PII-adjacent
lines that the probe privacy rule does not govern.

**Retention: raw captures are private, ephemeral, and never leave the session.**
They live only under an absolute `$env:TEMP` path — never the repo, a worktree,
a PR, a commit or `FEATURES.md` — and are deleted immediately after analysis, in
the same turn as the capture. A saved in-app log exported from the phone is a
raw capture too. Never quote a raw ambient line outside the session; a report
carries only the declared marker lines plus a sanitized exception class and its
top frame, payload elided. Record the deletion and the process termination as
**separate** evidence; neither implies the other. If deletion fails, report it
and block teardown.

**Cleanup verification is three independent checks**, none evidence for the
others. (a) Grep the final head tree for the exact declared `DWELL_SMOKE_<slug>`
literal **and** the bare `DWELL_SMOKE_` prefix — a probe planted under a
mistyped slug still has to come out. (b) Inspect the final diff for any added
log call not declared permanent: every added short `Log.e(`/`.i(`/`.w(` call,
resolved against that file's actual imports (whichever `Log` it imports, added
in this diff or already there), **and** every added fully-qualified
`helium314.keyboard.latin.utils.Log.` or `android.util.Log.` call — checking
only calls behind a newly-added import would miss a probe dropped into a file
that already imported `Log`. (c) Confirm the raw capture is deleted, the
process is stopped, and the default IME is restored.

**Grade the evidence honestly.** `Evidence: ADB-traced` for a traced cycle;
`Evidence: visual + ADB collateral` for a verification build's collateral scan;
`Evidence: visual-only` for each device-absent outcome — not offered, declined,
or not connected — distinguished by its parenthetical, never collapsed into one
generic label.
