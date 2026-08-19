# Font Creator

A Kotlin + Jetpack Compose Android app for creating and importing fonts, writing on images, and filling, signing, or stamping documents.

## Pull-request testing

Every pull request runs tests and creates a release Android App Bundle (AAB) artifact.

When the required repository secrets are configured, the same workflow also uploads the AAB to the **Google Play Internal testing** track. Install it through the Play Store testing link to test an update before merging the pull request—no APK download or uninstall is needed.

The workflow uses GitHub's run number as Android's version code, so each PR test build is a valid upgrade.

### Required GitHub secrets

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` — the complete JSON of a Google Cloud service account that has Google Play Console release access.

Configure the service account in Play Console under **Users and permissions**, then grant it release access for this app. The workflow only targets the **internal** track; it never publishes to closed testing or production.

## Important

Telegram should control content/configuration through the backend. It must never install arbitrary APKs or execute code received from Telegram. New app code should continue through pull requests, testing, merge, and a signed release pipeline.
