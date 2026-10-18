---
applyTo: "{build.gradle.kts,app/build.gradle.kts,settings.gradle,gradle.properties,gradle/**,app/proguard-rules.pro}"
---

# You are editing the build

These files are upstream's and change on most upstream releases, so every edit
here is a near-certain merge conflict. Edit them only when the change cannot
live anywhere else.

- **CI escalates on these paths.** `ci.yml` runs its R8 deep job whenever one of
  them changes; a broken keep rule or minify setting only shows up there.
- **Build types are load-bearing.** `debug` is minified (to fit GitHub's
  artifact size), `debugNoMinify` is the IDE default and the local compile
  gate, `runTests` is what `:app:testRunTestsUnitTest -PdwellTestBuildType=runTests` uses, `release` and
  `nouserlib` are minified release builds. Don't add a build type for a
  feature.
- **`applicationId` stays `helium314.keyboard.dazewell`** (debug adds `.debug`). The
  namespace `helium314.keyboard.latin` is what generated `R` and `BuildConfig`
  live under; fork code imports them from there.
- **The native library** is built by ndk-build from `src/main/jni`. Its NDK
  version is pinned by `ndkVersion`; bump it only with an upstream merge.
- **No dependency additions without a reason in the PR body.** Every new
  library is APK size, a minify risk and an upstream divergence.
- **Version bumps come from upstream.** Don't edit `versionCode`/`versionName`
  in a feature PR.

Full rules: `AGENTS.md` and the `dwellboard-workflow` skill.
