# Wamda App — تطبيق ومضة ليبية

One Android app for both Wamda news sites:

- **ومضة ليبية** (Arabic): https://wamdalibya.netlify.app/
- **Libya Pulse** (English): https://abdo15542.github.io/libyapulse/

The app is a WebView shell: it always loads the live sites, so new articles
appear automatically with no app update. Bottom tabs switch languages, the last
tab is remembered, pull-to-refresh reloads, the back button navigates the
WebView history, and an offline screen with a retry button appears when there
is no connection.

## Build

Every push to `main` builds a debug APK automatically (GitHub Actions):
artifact name `wamda-app-debug`, file `app-debug.apk`.

Local build (needs Android SDK + JDK 17):

```sh
gradle assembleDebug
```

## Release signing (later, for Google Play)

A release keystore (`wamda-release.p12`) is already generated and stored
**outside this repo** — it must never be committed. To enable signed release
builds, add these GitHub secrets and extend the workflow with a release job:

- `ANDROID_KEYSTORE_BASE64` — base64 of the `.p12` file
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS` (`wamda-release`)
- `KEY_PASSWORD`

## Notes

- The app appends `WamdaApp/1.0` to its user agent so both sites hide
  AdSense ads inside the app (AdSense is for the web; apps must not show it).
- iPhones cannot install APK files. iPhone users install the sites as PWA
  from Safari: Share → Add to Home Screen.
