# Releasing Quillboard

## How a release works

Pushing a tag `v*` (e.g. `v1.2.3`) triggers `.github/workflows/release.yml`, which:

1. Builds a signed AAB and APK using the upload keystore from GitHub secrets.
2. Uploads the AAB to Google Play (**internal track**) — only if the
   `PLAY_SERVICE_ACCOUNT_JSON` secret is configured; skipped otherwise.
3. Publishes a GitHub Release with the AAB and APK attached.

To cut a release:

```bash
# bump versionCode and versionName in app/build.gradle.kts first, then:
git tag v1.2.3
git push origin v1.2.3
```

> Google Play rejects an AAB whose `versionCode` was already uploaded, so always
> bump `versionCode` in `app/build.gradle.kts` before tagging.

## Required GitHub secrets

| Secret | Purpose |
|---|---|
| `KEYSTORE_BASE64` | Upload keystore (`upload-key.jks`), base64-encoded |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password (alias `upload`) |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Cloud service-account JSON for Play upload (optional — enables step 2) |

## One-time Google Play setup (manual, cannot be automated)

These steps require a human in the Play Console:

1. **Register a Play Developer account** at [play.google.com/console](https://play.google.com/console) ($25 one-time).
2. **Create the app** "Quillboard" in the Console with package name
   `dev.galando.posthogdroid`, and **enable Play App Signing**.
3. **Upload the first AAB manually**: download `app-release.aab` from a GitHub
   Release (or build locally) and upload it to the *Internal testing* track.
   Google requires the first artifact of a new app to go through the Console UI.
4. Complete the **store listing** (title "Quillboard", subtitle "Mobile
   dashboards for PostHog", original icon, "Not affiliated with PostHog" in the
   description) and the **Data Safety form** (API key + analytics data stored
   on-device only, never shared; link the privacy policy from `PRIVACY.md`).
5. **Closed testing**: personal developer accounts must run a closed test with
   12 testers for 14 continuous days before production access is granted.
6. Promote Internal → Closed → Production in the Console.

## One-time setup for automatic CI uploads

After the app exists in the Play Console:

1. In Google Cloud Console, create a **service account** and download its JSON key.
2. Enable the **Google Play Android Developer API** for that Cloud project.
3. In Play Console → *Users and permissions*, invite the service account's email
   and grant it release permissions ("Release to testing tracks" is enough for
   the internal track).
4. Add the JSON file's contents as the `PLAY_SERVICE_ACCOUNT_JSON` repository
   secret on GitHub.

From then on, every `v*` tag automatically uploads to the internal track.
Promotion to closed testing / production stays a manual Console action (change
`track:` in the workflow later if you want to publish further automatically).

**Note:** while the app is still in *draft* (never published to any track), the
Play API only accepts releases with `status: draft` — if the upload step fails
with a "draft app" error before your first manual publish, temporarily set
`status: draft` in the workflow's Play upload step.
