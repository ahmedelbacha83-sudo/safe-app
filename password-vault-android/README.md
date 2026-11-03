# Vault - Android app

Password manager: logo tiles, tap to copy, PIN-encrypted, fully offline.

## Publish to Play Store - complete checklist

### 1. Get the project onto GitHub (from your phone browser)
- github.com -> New repository -> name it e.g. `vault-android`
- Upload all these files (web upload works fine on mobile)

### 2. Build the AAB in the cloud
- Follow the one-time setup in `.github/workflows/build.yml`
  (keystore -> base64 -> GitHub Secrets)
- Actions tab -> run workflow -> download `app-release.aab`
- The keystore is your app's identity: save it in 2+ places. Losing it
  means you can never update the app again. (Google Play App Signing can
  recover you if you enable it when publishing - do that.)

### 3. Google Play Developer account - $25 one-time
- play.google.com/console -> create account, pay $25, verify ID

### 4. Privacy policy (REQUIRED before review passes)
- This repo includes PRIVACY.md - turn on GitHub Pages
  (Settings -> Pages -> deploy from main branch) and you get a free
  public URL like https://you.github.io/vault-android/PRIVACY.md
- Paste that URL in Play Console

### 5. Create the app listing
- Upload the AAB
- App name: Vault, short description, full description, screenshots
  (take them on your phone), app icon 512x512, feature graphic 1024x500

### 6. Data safety form
- Declare: does NOT collect or share data; data stored on device only;
  encryption in transit & at rest = yes (your PIN/AES).

### 7. Submit for review
- First review takes 1-7 days. Password managers get checked carefully -
  the honest privacy policy and no-network behavior are your friends.

## Notes
- `app/src/main/assets/index.html` is the whole app. Edit it, push, and
  the cloud build produces a new AAB automatically.
- minSdk 24 = Android 7.0 and up.
