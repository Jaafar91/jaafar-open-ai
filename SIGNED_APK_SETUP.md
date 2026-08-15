# Signed APK setup

GitHub's normal pull-request artifact is a debug APK. It is intentionally not used as the long-lived installation channel.

To install updates without Android's **package conflict** message, create one permanent signing key and configure the GitHub secrets below. Keep the key and passwords private. If they are lost, existing users cannot update to a differently signed APK.

## 1. Create the signing key on your own computer

Run this in PowerShell and choose strong passwords when prompted:

```powershell
keytool -genkeypair -v -keystore jaafar-release.jks -alias jaafar-release -keyalg RSA -keysize 2048 -validity 10000
```

Back up `jaafar-release.jks` securely. Never commit it or send it in a chat.

## 2. Copy the keystore as Base64

Run:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\jaafar-release.jks")) | Set-Clipboard
```

## 3. Add GitHub repository secrets

Open **Settings → Secrets and variables → Actions → New repository secret** in this repository and add:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The value copied in step 2 |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore password |
| `ANDROID_KEY_ALIAS` | `jaafar-release` (or the alias you chose) |
| `ANDROID_KEY_PASSWORD` | The key password |

## 4. Build the installable release

After this pull request is merged:

1. Open **Actions → Build Signed Release APK**.
2. Choose **Run workflow** while the selected branch is `master`.
3. Download the `app-release-apk` artifact.

Uninstall the old debug app from the phone once, then install this signed release APK. Each later signed release APK from this workflow has the same signing identity and a higher version code, so Android installs it as an update and retains app data.

The signing workflow runs only from `master`. Pull-request code does not receive the permanent signing key.
