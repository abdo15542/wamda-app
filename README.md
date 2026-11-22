# Wamda Apps — تطبيقا ومضة ليبية و Libya Pulse

Two separate Android apps from one codebase (two build flavors) — one per
site, never mixed:

- **ومضة ليبية** (Arabic, `ar` flavor, package `net.wamdalibya.app`):
  https://wamdalibya.netlify.app/
- **Libya Pulse** (English, `en` flavor, package `net.libyapulse.app`):
  https://abdo15542.github.io/libyapulse/

Each app is a WebView shell around its own live site: it always loads the
live site, so new articles appear automatically with no app update.
Pull-to-refresh reloads, the back button navigates the WebView history, and
an offline screen with a retry button appears when there is no connection.
The Netlify "Powered by" badge is hidden inside the apps (injected CSS/JS);
it still shows on the website in a browser, where it is Netlify's free-tier
mark.

## Build

Every push to `main` builds both debug APKs automatically (GitHub Actions):
artifacts `wamda-app-ar-debug` (`app-ar-debug.apk`) and
`libya-pulse-app-en-debug` (`app-en-debug.apk`).

Local build (needs Android SDK + JDK 17):

```sh
gradle assembleArDebug assembleEnDebug
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

- Both apps append `WamdaApp/1.0` to their user agent so the sites hide
  AdSense ads inside the apps (AdSense is for the web; apps must not show it).
- iPhones cannot install APK files. iPhone users install the sites as PWA
  from Safari: Share → Add to Home Screen.
- `app/src/main/res/menu/bottom_nav_menu.xml` and
  `app/src/main/res/color/bottom_nav_colors.xml` are leftovers of the old
  two-tab build and are no longer used.
