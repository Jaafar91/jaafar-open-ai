# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this app is

Font Maker (`applicationId`/package `com.mjaafar.fontcreator`) is a Kotlin + Jetpack Compose
Android app: draw your own handwriting letter-by-letter to generate a real, installable TrueType
font, then use it to write on photos or to fill, sign, and stamp documents/PDFs. It is fully
offline for its actual features — no accounts/login, no analytics, no IAP. Do not add network
calls, auth, or third-party dependencies without the user explicitly asking.

This is the Android counterpart to a **from-scratch, hand-ported SwiftUI iOS app** in
`Jaafar91/jaafar-fonts-ios`. There is **no shared code** between the two — every feature is
ported by hand, file by file, and the two have genuinely diverged in places (Android supports far
more scripts — see `LanguageScript` below — iOS currently only draws Basic Latin). When porting a
fix from the iOS app (or vice versa), read the other platform's source first and translate the
intent; don't assume identical structure. **Cross-platform scope discipline**: never apply an
Android-driven UI fix to the iOS repo (or vice versa) unless the user asks for both — if a task
doesn't say which platform, ask rather than assume "logically related" work belongs on both.

**This repo is not only edited by Claude Code sessions.** `.github/workflows/telegram-feature-pr.yml`
runs an **OpenAI Codex** agent against issues titled `Telegram OpenAI feature: ...` (opened by a
Telegram bot backend) and opens its own PRs from them (branch prefix `codex/telegram-issue-<n>`).
So PR/commit history here can legitimately include non-Claude, non-human-typed contributions —
don't assume every PR came from a Claude Code session when reading git history, and don't be
surprised if PR numbers have gaps or an unfamiliar branch prefix shows up.

## Commands

There is **no local Android SDK / Gradle build capability in a typical Claude Code sandbox** —
`gradle :app:bundleRelease` (or any AGP build) needs the Android SDK and network access to
Google's Maven repo for the plugin, neither of which is available in this kind of environment.
Verify changes by:
- Careful manual reading of the diff, and (for anything algorithmic, like `TrueTypeGenerator` or
  a regex) a standalone reproduction outside Gradle — e.g. a small Python/Java snippet using the
  same regex engine — rather than assuming it compiles/behaves correctly.
- Pushing a branch and reading the real result from GitHub Actions CI (see below).

When a real Android/JVM toolchain **is** available (e.g. a human running this locally, or a CI
runner), the actual commands are:
- `gradle :app:testDebugUnitTest` — the unit test suite (JUnit, under `app/src/test/...`; four
  test files today, covering font-name normalization/uniqueness, phrase mode, completion
  navigation, and `TrueTypeGenerator` stroke thickness). This is what PR CI actually runs — treat
  it as the test command.
- `gradle :app:assembleDebug` — debug APK (used as CI's fallback artifact when release signing
  isn't configured/available).
- `gradle :app:bundleRelease` / `gradle :app:assembleRelease` — signed release AAB/APK (needs
  `ANDROID_KEYSTORE_PATH`/`ANDROID_KEYSTORE_PASSWORD`/`ANDROID_KEY_ALIAS`/`ANDROID_KEY_PASSWORD`
  env vars pointing at a real keystore; see `SIGNED_APK_SETUP.md`).
- There is no separate lint config beyond what AGP/Gradle run by default; don't invent a
  `ktlint`/`detekt` command that doesn't exist here.

**CI** (GitHub Actions):
- `.github/workflows/android.yml` (job **"build-and-distribute"**, workflow name "Build and
  distribute internal test") — runs on every PR (`opened`/`synchronize`/`reopened`) and manual
  dispatch. Always runs `gradle :app:testDebugUnitTest` first — **this is the real CI gate to
  poll for PR status**. If all four Play/keystore secrets are present *and* the PR's repo is this
  repo (not a fork, so secrets are available), it also builds a signed release AAB and uploads it
  to the **Google Play Internal testing** track (installable via the Play Store testing link, no
  APK sideload needed) — with a debug-APK-artifact fallback at every failure point along that
  path. A fork PR (no secrets) always falls through to the debug APK fallback; that's expected,
  not a misconfiguration.
- `.github/workflows/signed-release.yml` (manual `workflow_dispatch` only, `master` branch only)
  — builds and uploads the real signed production `.aab`/`.apk` as workflow artifacts for a human
  to download and push to Google Play themselves. Not run on PRs.
- `.github/workflows/telegram-feature-pr.yml` — the Codex-via-Telegram automation described
  above. Not a build gate for human/Claude-opened PRs.
- Project settings: `compileSdk`/`targetSdk = 36`, `minSdk = 24`, Java/Kotlin toolchain 17.
  `versionCode` is deliberately `(System.currentTimeMillis() / 1_000L).toInt()` (epoch seconds,
  currently ~1.79 billion) rather than a small counter — see the "epoch-seconds versionCode"
  gotcha below before touching this. `versionName` is the small, human-readable `"1.0.$N"` (CI
  run number) or `"1.0.0"` — that's what's actually shown to users, not `versionCode`.
- **R8/minification was only just enabled** (`isMinifyEnabled = true` on the release build type,
  turned on because Play Console flagged 0% obfuscation). Verified safe at the time: no
  Gson/Moshi/kotlinx.serialization, no Room, no Dagger/Hilt, no reflection anywhere — persistence
  goes through plain `org.json.JSONObject` and `LanguageScript.valueOf()/.name`, both fine under
  default R8 rules. If you add a dependency that needs reflective access to unobfuscated
  names/fields, you likely need a new rule in `app/proguard-rules.pro` (currently empty of custom
  rules) — don't assume R8 will "just work" the way it did before this was enabled.

## PR workflow

- Never commit directly to `master`. Branch off fresh `master` per unrelated fix
  (`git checkout -b <branch> origin/master`); one focused PR per bug/feature.
- Poll the **`gradle :app:testDebugUnitTest`** check run's actual conclusion for PR status, not
  `mergeable_state` (which reflects branch-protection review requirements, not CI — a `"blocked"`
  state with a green check just means a review approval is pending).
- Commit messages/PR bodies end with the attribution trailer in use at the time; squash-merge once
  green.

## Architecture

Everything lives under `app/src/main/java/com/jaafar/remoteconfig/` — `MainActivity.kt` is a
thin shell (handles share/edit intents for PDFs/images, hands off to Compose) that immediately
delegates to `fontcreator/FontCreatorScreen.kt`'s `FontCreatorApp` composable, which is the real
root. (The `remoteconfig` package name is a leftover from an earlier, unrelated version of this
app — everything under `fontcreator/` is the actual product; don't read anything into the name.)

Screens are top-level `@Composable` functions (not classes) taking a shared
`FontCreatorViewModel` — there's no per-screen ViewModel, one `AndroidViewModel` holds everything:
- **Persistence — three separate repositories** (unlike the iOS app's single `StudioStore` with
  one `AssetKind` enum): `GlyphRepository` (hand-drawn `FontProject`s), `SignatureRepository`
  (both signatures *and* stamps, as `SavedSignature`), `ImportedFontRepository` (fonts imported
  from an external `.ttf`/`.otf` — a genuinely separate list/type from `GlyphRepository`'s
  `FontProject`, not a flag on the same model like iOS's `SavedAsset.isImported`). All three live
  in `FontCreatorViewModel`'s constructor.
- **`FontModels.kt`** — `FontProject`, `GlyphDrawing`/`GlyphStroke`/`GlyphPoint`, and
  `LanguageScript`: an enum of Unicode script blocks a font project can include (Basic Latin,
  Latin Extended, Arabic, Hebrew, Greek, Cyrillic, Devanagari, CJK, Hangul, Hiragana, Katakana,
  Thai) — this multi-script support is real and significantly ahead of the iOS app's Basic-Latin-
  only editor; keep that in mind before assuming a font-drawing feature ports 1:1.
- **`TrueTypeGenerator.kt`** — the canonical, hand-written `.ttf` writer. `TrueTypeGenerator.swift`
  in the iOS repo is a byte-for-byte Swift port of *this* file — if you change font generation
  here, the iOS file is what needs mirroring (or an explicit note that it's intentionally
  diverging).
- **`FontCreatorScreen.kt`** (root nav) → `DashboardScreen.kt` → `FontsModuleScreen.kt` /
  `SignaturesModuleScreen.kt` / `StampsModuleScreen.kt` (lists) → `FontDrawingScreens.kt`
  (drawing canvas + spacing), `FontCelebrationScreen.kt`, `FontStudioScreens.kt` (multi-style
  export), `SignatureScreen.kt` (signature/stamp editor + importing a stamp from a photo).
- **`FillMarkScreen.kt`** (~1700 lines, by far the largest file) — place text/date/checkmark/
  signature/stamp marks onto a photo or PDF page and export the flattened result.
  **`DocumentUtils.kt`** is its supporting toolkit: PDF page rendering/counting, bitmap loading,
  signature rasterization, and the actual share/export plumbing.
- **`ImageTextEditorScreen.kt`** — "write on image": text layers over a picked photo.

## Gotcha: font files must be written atomically (real production SIGBUS crash)

`generatedFile(name)`'s path can be (re)written from multiple unsynchronized call sites — the
active drawing flow, preview generation, `typefaceForPreview()` — while another call site may
concurrently be loading that same file into a `Typeface` (`Typeface.Builder(file).build()`, which
**mmaps the file and parses it in native code**). Plain `File.writeBytes()` truncates the file
before writing new bytes; if a reader has it mmap'd mid-truncate, the kernel delivers **SIGBUS**
with no way for native font-parsing code to catch it — a real, reproduced-in-production crash
(fixed by commit `aa13992`, "Fix SIGBUS crash in FontCreatorViewModel.loadTypeface"). The fix —
`FontCreatorViewModel.writeFontFileAtomically(file, bytes)` — writes to a sibling temp file, then
`File.renameTo()`s it over the destination (atomic on the same filesystem, so a concurrent reader
always sees a complete old or new file, never a torn write). **Any new code path that (re)writes
a font file this app might concurrently read must go through this helper, not raw
`File.writeBytes()`.** (`Files.move()` was deliberately avoided — it needs API 26+, and this app's
`minSdk` is 24 with no core-library desugaring configured.)

## Gotcha: font-name normalization must be Unicode-aware, not ASCII-only

`normalizedFontStorageKey()` derives a font's on-disk filename/storage key from its display name.
It used to strip anything outside `[a-z0-9]`, which silently collapsed a name written entirely in
a non-Latin script (Arabic, CJK, etc. — all explicitly supported via `LanguageScript`) to an empty
key, causing `createProject()` to reject it — but `CreateFontDialog` never surfaces `vm.status`,
so the Create button just looked dead. Fixed to use `\p{L}`/`\p{N}` (any Unicode letter/digit)
instead. Any other place that derives a filesystem-safe key from a user-entered name needs the
same Unicode-aware pattern, not an ASCII assumption.

## Gotcha: `versionCode` is deliberately huge — don't "fix" it

`app/build.gradle.kts` sets `versionCode = (System.currentTimeMillis() / 1_000L).toInt()` (epoch
seconds) rather than a small incrementing number. This is intentional, per the comment right above
it: `GITHUB_RUN_NUMBER` (used for the human-readable `versionName`) resets between different CI
workflows, which could make a real Play release look "older" than a stray build from a different
workflow and get rejected by Play's monotonic-versionCode requirement; epoch seconds is guaranteed
to keep increasing across any workflow, and stays comfortably under Android's
2,100,000,000 `versionCode` ceiling for a very long time. It is **not shown to end users anywhere**
— only `versionName` ("1.0.N") is user-facing (Play Store listing, in-app if displayed). Don't
"fix" this to a smaller number without understanding why it's shaped this way first.

## Play Store submission experience

- **First app on a new developer account**: 7–14 days of review is normal (Google's own guidance,
  and the Play Developer Community forums — "is this stuck" threads consistently only start
  appearing *after* day 14). A multi-day wait with no email and no flagged item in Play Console
  isn't a problem before then.
- Internal testing track (via `android.yml`'s PR builds) is the low-friction way to test a change
  before merging — install through the Play Store testing link, no APK download/uninstall needed,
  and each PR build's `versionCode` is always a valid upgrade over the last (see the epoch-seconds
  gotcha above).
- Production releases go through `signed-release.yml` (manual, `master`-only) — a human downloads
  the resulting `.aab` and uploads it to Play Console themselves; this repo's Actions don't publish
  straight to production.

## Known reference docs (may drift from current code)

- `JOURNEY_TEST_PLAN.md` — a manual QA checklist mixing "code checked" and "manual device test
  required" rows, including notes tied to specific past fixes (e.g. FONT-03). Treat it as a
  checklist to re-verify, not a live status board — it won't auto-update as code changes.
- `SIGNED_APK_SETUP.md` — signing-key setup instructions for `signed-release.yml`; accurate as of
  this writing, cross-reference against the workflow file itself if secrets/steps seem to disagree.
