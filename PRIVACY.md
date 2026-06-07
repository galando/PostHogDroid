# Quillboard Privacy Policy

**Last updated:** May 2026

## Data Storage

- Your PostHog Personal API key is stored **only on your device**, encrypted at rest using Android EncryptedSharedPreferences (backed by Android Keystore).
- The key is **never** stored in the Room database or any plaintext file.
- All dashboard and insight data is cached locally in the app's private sandbox. No external service has access.

## Data Transmission

- Your API key is transmitted **only** to the PostHog host URL you configure (e.g., `https://app.posthog.com` or your self-hosted instance).
- All network requests enforce **TLS (HTTPS)**. Cleartext HTTP is blocked by default.
- No data is sent to any third-party service, analytics provider, or telemetry endpoint.

## No Backend

- Quillboard has **zero server infrastructure**. There is no backend, no database server, no API proxy.
- The app communicates directly between your device and your PostHog instance.

## No Telemetry

- Quillboard does **not** collect, transmit, or store any usage analytics, crash reports, or tracking data.
- No third-party SDKs with tracking capabilities are included.

## Read-Only Access

- Quillboard accepts **only** PostHog Personal API keys with read-only scopes (`dashboard:read`, `insight:read`, `query:read`).
- The app performs no write, delete, or mutation operations against the PostHog API.

## Open Source

- Quillboard is open source under the MIT License. Source code is available for audit.
- Builds are reproducible from source.

## Not Affiliated with PostHog

- Quillboard is an independent, community-built project. It is not affiliated with, endorsed by, or connected to PostHog Inc.
- "PostHog" is used nominatively to describe compatibility with the PostHog platform.

## Contact

For privacy questions, open an issue on the [project repository](https://github.com/galando/PostHogDroid) or contact the author.

**Author:** Gal Naor
**Repository:** https://github.com/galando/PostHogDroid
**Support:** https://buymeacoffee.com/galando
