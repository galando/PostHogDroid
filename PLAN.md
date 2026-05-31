# Quillboard — Full Implementation & Release Plan

> A native Android (Kotlin + Jetpack Compose) client for viewing PostHog dashboards,
> insights, and threshold alerts on mobile. This document is the end-to-end plan to take
> the project from its current state to a published, trustworthy, open-source app on Google Play.

---

## 0. Decisions locked

| Item | Value |
|---|---|
| App display name | **Quillboard** |
| Play Store subtitle (nominative use OK) | "Mobile dashboards for PostHog" |
| `applicationId` | `dev.galando.posthogdroid` |
| GitHub repo | `PostHogDroid`, **public** |
| License | MIT |
| Distribution | GitHub Releases (free APK) → Google Play |

---

## Phase 1 — Security scrub (MUST happen before the first git commit)

Because the repo goes public, anything committed once lives in history forever.

| # | Action | File / location |
|---|---|---|
| 1.1 | **Revoke** the hardcoded key in PostHog UI (it's compromised the moment it's public). | PostHog → Settings → Personal API keys |
| 1.2 | Delete the hardcoded-key + project fallback block. | `MainAppScaffold.kt:2219-2224` |
| 1.3 | Remove preloaded demo credentials so login starts blank. | `LoginScreen` defaults, `MainAppScaffold.kt:2200-2203` |
| 1.4 | Gate HTTP logging behind debug. | `PostHogApi.kt:84` |
| 1.5 | Remove unused `GEMINI_API_KEY`. | `.env.example`, `metadata.json` |
| 1.6 | Confirm `.gitignore` blocks secrets. | add `*.jks`, `*.keystore`, ensure `.env`, `local.properties` present |
| 1.7 | Delete `debug.keystore.base64` from repo (debug keys are throwaway; no reason to ship it). | repo root |

**1.4 detail** — replace the always-on body logger:
```kotlin
val logging = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
}
```
(`PostHogClient.createService` needs no other change; `BuildConfig` is already enabled.)

---

## Phase 2 — Trust & API-key handling

### 2.1 Read-only by construction (the strongest signal)
Document in README + show on the login screen:
> Create your key at **PostHog → Settings → Personal API keys** with scopes
> **`dashboard:read`, `insight:read`, `query:read`** only, scoped to one project.
> Quillboard cannot write to or delete anything — PostHog enforces this server-side.

### 2.2 Encrypt the key at rest
Currently stored plaintext in Room (`Database.kt:9`, `PostHogSettings.personalApiKey`). Plan:
- Add dependency `androidx.security:security-crypto`.
- Store the API key in `EncryptedSharedPreferences` (Android Keystore-backed); keep only
  **non-secret** settings (host, projectId, demoMode) in Room.
- Repository reads the key from encrypted prefs on demand; on `logout()` call `.edit().clear()`.

> Touches `PostHogSettings`, `PostHogRepository.login/logout/saveSettings/initDefaultSettingsAndDemoData`.
> Since `fallbackToDestructiveMigration()` is set, removing the column needs no migration work pre-launch.

### 2.3 Network can only leak over TLS
Add `app/src/main/res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false"/>
</network-security-config>
```
Reference it in `AndroidManifest.xml`:
`<application ... android:networkSecurityConfig="@xml/network_security_config">`.
*(If you keep "Self Host" support for plain-HTTP instances, add a per-domain
`<domain-config cleartextTrafficPermitted="true">` only for a user-entered host — otherwise
leave fully disabled.)*

### 2.4 Privacy policy (required by Play + the trust story)
Write `PRIVACY.md` (host on GitHub Pages, link in Play listing). Must state:
- The personal API key is stored **only on the device**, encrypted.
- It is transmitted **only** to the PostHog host the user configures — no other server.
- **No backend, no telemetry, no third parties.**
- Source is open at the repo URL; builds are reproducible from tagged commits.

### 2.5 Reinforce "no backend" in the listing
Add a short "How your key is handled" panel on the login screen linking to `PRIVACY.md`,
plus a "Not affiliated with PostHog" line.

---

## Phase 3 — Code-quality improvements (can ship over several releases)

| Pri | Item | Where | Plan |
|---|---|---|---|
| P0 | Real `applicationId` | `app/build.gradle.kts` | one line (below). namespace can stay `com.example` — Play only cares about applicationId. Full package rename is optional later. |
| P0 | App name → Quillboard | `res/values/strings.xml` `app_name` | one line |
| P1 | Replace polling with WorkManager | `MyApplication.kt:46-62`, `HogBackgroundReceiver.kt` | swap `while(true)` loop + `setInexactRepeating` for one `PeriodicWorkRequest` (15-min min). Drop the custom `BOOT_COMPLETED` receiver — WorkManager reschedules itself. |
| P1 | Encrypted credentials | §2.2 | as above |
| P2 | Original icon | `res/drawable/ic_posthog_custom_logo.png`, `mipmap/*` | replace with a Quillboard mark (quill/spine) |
| P2 | Split the god-file | `MainAppScaffold.kt` (2,678 lines) | break into `LoginScreen.kt`, `DashboardsScreen.kt`, `DashboardDetailsScreen.kt`, `AlertsScreen.kt`, `NotificationsScreen.kt`, `SettingsScreen.kt` |
| P2 | WebView fallback for unsupported insights | new `InsightWebView.kt` | render retention/paths/SQL/maps via PostHog share URL when native parser returns no series |
| P3 | Real unit tests | `src/test/...` (currently only Example scaffolding) | test `parseRepositoryDataJson`, `mapRemoteInsightToEntity`, `evaluateAlertsDirect` (pure logic, fast) |
| P3 | Home-screen widget (Glance) | new module | glanceable pinned metric — the real mobile value |
| P3 | Real Room migrations | `Database.kt` | replace `fallbackToDestructiveMigration()` before users have data |
| P3 | Pull-to-refresh + "last updated" | dashboards screen | trust/freshness signal |

**P0 build.gradle change:**
```kotlin
defaultConfig {
    applicationId = "dev.galando.posthogdroid"
    minSdk = 24
    targetSdk = 36
    versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
    versionName = (System.getenv("GITHUB_REF_NAME") ?: "1.0").removePrefix("v")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}
```

---

## Phase 4 — The five credentials (reference)

| # | Credential | Who makes it | Where | Needed for |
|---|---|---|---|---|
| 1 | **Upload keystore** `upload-key.jks` (+ store pwd, alias `upload`, key pwd) | You, via `keytool` | your machine | signing APK/AAB |
| 2 | **GitHub secrets** `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` | You (wrap #1) | repo → Settings → Secrets → Actions | CI signing |
| 3 | **Play Developer account** ($25 one-time) | You | play.google.com/console | publishing rights |
| 4 | **Play service-account JSON** | You | Google Cloud → IAM, linked in Play Console | optional CI auto-upload |
| 5 | **PostHog personal API key** | **End user** | their PostHog settings | running the app |

You need only **#1** to build/sign. **#5 is the user's, never yours.**

---

## Phase 5 — Generate the signing key (#1)

```bash
keytool -genkeypair -v -keystore upload-key.jks -keyalias upload \
  -keyalg RSA -keysize 2048 -validity 10000
# prompts: store password, key password, name/org → SAVE THESE in a password manager
```
- Enable **Play App Signing** in the Console so a lost upload key is recoverable
  (Google holds the real signing key).
- Keep `upload-key.jks` out of git (gitignored via `*.jks`).

The existing `signingConfigs { create("release") {...} }` in `app/build.gradle.kts` already reads
`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` and uses alias `upload` — so it matches this
keystore with **no edit needed**.

---

## Phase 6 — Git init & publish

```bash
cd /Users/galando/dev/posthog-mobile
# (Phase 1 scrub already done)
git init -b main
git add .
git commit -m "chore: initial commit (Quillboard)"

gh auth login                 # if needed
gh repo create PostHogDroid --public --source=. --remote=origin --push
```
Add `LICENSE` (MIT), `README.md` (setup + trust section + scoped-key instructions), `PRIVACY.md`.

---

## Phase 7 — Gradle wrapper (so CI/others can build)

Currently missing (`gradlew` absent). From Android Studio's terminal:
```bash
gradle wrapper --gradle-version 8.11.1
git add gradlew gradlew.bat gradle/wrapper
git commit -m "build: add gradle wrapper"
git push
```

---

## Phase 8 — Load GitHub secrets (#2)

```bash
base64 -i upload-key.jks | pbcopy        # macOS
gh secret set KEYSTORE_BASE64            # paste the clipboard
gh secret set STORE_PASSWORD             # type value
gh secret set KEY_PASSWORD               # type value
```
*(Key alias `upload` is non-secret and hardcoded in build.gradle — no secret needed. CI does
**not** need any PostHog secret: the secrets-gradle-plugin falls back to `.env.example`, and the
user supplies their key at runtime.)*

---

## Phase 9 — CI/CD workflow

Create `.github/workflows/release.yml`:
```yaml
name: Release Build
on:
  push:
    tags: ['v*']          # push a tag → build runs

permissions:
  contents: write          # needed to attach files to the Release

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }

      - name: Decode keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > upload-key.jks

      - name: Build signed AAB + APK
        env:
          KEYSTORE_PATH: ${{ github.workspace }}/upload-key.jks
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew bundleRelease assembleRelease --no-daemon

      - name: Attach artifacts to GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: |
            app/build/outputs/apk/release/*.apk
            app/build/outputs/bundle/release/*.aab
```
After this, **your "easy APK" button is: push a git tag.** No manual build steps.

---

## Phase 10 — Cut a release

```bash
git tag v1.0.0
git push origin v1.0.0
```
CI builds, signs, and attaches `app-release.apk` + `app-release.aab` to the GitHub Release for that
tag. People can sideload the APK; you upload the AAB to Play.

---

## Phase 11 — Google Play

| Step | Detail |
|---|---|
| 11.1 | Register Play Developer account — **$25 one-time**. |
| 11.2 | Create app "Quillboard" in Console; **enable Play App Signing**. |
| 11.3 | **First AAB upload is manual** — download the CI `.aab`, upload to the *Internal testing* track. |
| 11.4 | **Closed testing: 12 testers for 14 continuous days** (required for personal accounts before production access). |
| 11.5 | Complete **Data Safety** form: declare it handles a credential (API key) + analytics data, **stored on-device only, not shared**. Add the `PRIVACY.md` URL. |
| 11.6 | Store listing: title "Quillboard", subtitle "Mobile dashboards for PostHog", **original icon**, "Not affiliated with PostHog" in the description. |
| 11.7 | Promote Internal → Closed → Production. |

**Optional later (full automation, #4):** create a Google Cloud service account → grant it Play
access in Console → add `gradle-play-publisher` plugin → store JSON as secret
`PLAY_SERVICE_ACCOUNT_JSON` → add a CI step `./gradlew publishReleaseBundle`. Tags then promote to
Play automatically (after the first manual upload).

---

## Phase 12 — Monetization (realistic outlook)

- **Now:** free + open-source. Builds the trust story (no backend, auditable) that the whole
  API-key model depends on. GitHub Sponsors for goodwill.
- **Later, if there's traction:** freemium — free = 1 project read-only; Pro = multi-project +
  widgets + reliable push alerts. **Reliable push needs a backend**, which means OAuth (not pasted
  keys) and a hosting cost — a separate, larger project. Don't monetize before that exists;
  on-device polling alone can't deliver dependable alerts.
- Avoid ads (kills a "professional tool" feel) and avoid putting "PostHog" in a paid title (trademark).

---

## Execution checklist (the order to actually do it)

1. ☐ Phase 1 scrub (revoke key, delete hardcoded creds, gate logging, fix `.gitignore`)
2. ☐ Phase 3 P0 (applicationId + app_name) + Phase 2.3/2.4 (network config + PRIVACY.md)
3. ☐ Phase 6 git init + `gh repo create ... --public`
4. ☐ Phase 7 gradle wrapper
5. ☐ Phase 5 generate keystore
6. ☐ Phase 8 GitHub secrets
7. ☐ Phase 9 add `release.yml`
8. ☐ Phase 10 tag `v1.0.0` → verify CI artifacts
9. ☐ Phase 2.2 encrypt credentials, Phase 3 P1+ improvements (iterate)
10. ☐ Phase 11 Play account → manual upload → closed testing → production

---

## File-by-file change map (for when you implement)

| File | Change |
|---|---|
| `app/build.gradle.kts` | `applicationId`, env-driven `versionCode`/`versionName`; add `security-crypto` + `work-runtime-ktx` deps |
| `app/src/main/res/values/strings.xml` | `app_name` = Quillboard |
| `app/src/main/res/xml/network_security_config.xml` | **new** — cleartext disabled |
| `app/src/main/AndroidManifest.xml` | reference network config; remove `HogBackgroundReceiver` boot receiver after WorkManager swap |
| `app/src/main/java/com/example/data/api/PostHogApi.kt` | logging gated by `BuildConfig.DEBUG` |
| `app/src/main/java/com/example/ui/screens/MainAppScaffold.kt` | delete hardcoded key/creds; add trust panel; (later) split into per-screen files |
| `app/src/main/java/com/example/data/database/Database.kt` | drop plaintext `personalApiKey` column |
| `app/src/main/java/com/example/data/repository/PostHogRepository.kt` | read/write key via `EncryptedSharedPreferences` |
| `app/src/main/java/com/example/MyApplication.kt` + `HogBackgroundReceiver.kt` | replace polling/alarm with WorkManager |
| `res/drawable` + `mipmap/*` | original Quillboard icon |
| repo root | `LICENSE`, `README.md`, `PRIVACY.md`, `.github/workflows/release.yml`; delete `debug.keystore.base64` |
