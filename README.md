# Jaafar Remote Android App

A Kotlin + Jetpack Compose Android client that reads remotely managed configuration from the Render FastAPI backend.

## Before building

1. In `app/src/main/java/com/jaafar/remoteconfig/MainActivity.kt`, replace `BACKEND_URL` with the public Render URL for the backend.
2. The backend must expose `GET /api/v1/mobile/config` and return `{"message":"Hello from Telegram","enabled":true}`.
3. Build locally with Android Studio or `gradle :app:assembleDebug`.

## Firebase distribution

The GitHub Action builds an APK on every push to `master` and keeps it as an Actions artifact. To distribute through Firebase App Distribution, add these GitHub repository secrets:

- `FIREBASE_APP_ID`
- `FIREBASE_SERVICE_ACCOUNT_BASE64` — base64 of a Firebase service-account JSON file with App Distribution access.

Create the Firebase tester group named `testers`, or change the group in `.github/workflows/android.yml`.

## Important

Telegram should control content/configuration through the backend. It must never install arbitrary APKs or execute code received from Telegram. New app code should continue through a pull request, test, merge, and signed release pipeline.
