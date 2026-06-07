# Quillboard

Mobile dashboards for PostHog -- view your analytics dashboards, insights, and alerts on Android.

## Features

- View PostHog dashboards and insights on mobile
- Real-time metric threshold alerts with native notifications
- Demo sandbox mode for exploring without credentials
- Line, bar, pie, table, and funnel chart visualizations
- Self-hosted PostHog instance support

## Setup

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Clone the repository
2. Open in Android Studio
3. Create a `.env` file from `.env.example` and fill in your PostHog credentials
4. Run on an emulator or physical device (API 24+)

## Trust & Security

- **No backend**: All data stays on your device. Zero server infrastructure.
- **Encrypted key storage**: Your API key is stored in Android Keystore via EncryptedSharedPreferences.
- **TLS only**: All network traffic uses HTTPS. Cleartext HTTP is blocked.
- **Read-only**: Only scoped Personal API keys (`dashboard:read`, `insight:read`, `query:read`) are accepted.
- **No telemetry**: No analytics, crash reporting, or third-party tracking.
- **Open source**: MIT licensed, reproducible builds.

See [PRIVACY.md](PRIVACY.md) for the full privacy policy.

## Creating a PostHog API Key

1. Go to your PostHog instance > Settings > Personal API keys
2. Create a new key with **only** read-only scopes:
   - `dashboard:read`
   - `insight:read`
   - `query:read`
3. Copy the `phx_...` token and enter it in the app

## Not Affiliated with PostHog

Quillboard is an independent, community-built project. It is not affiliated with, endorsed by, or connected to PostHog Inc.

## Author

**Gal Naor** — [GitHub](https://github.com/galando) · [Buy Me a Coffee](https://buymeacoffee.com/galando)

If you find Quillboard useful, consider [supporting the project](https://buymeacoffee.com/galando)!

## License

MIT License. See [LICENSE](LICENSE).
