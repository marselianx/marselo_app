# Marselo Anime – Android app

A WebView wrapper that opens **https://anime.marse.lol** and nothing else, with ad blocking.

## Get the APK (pick one)

**A) GitHub (no installs needed)**
1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the repo → **Actions** → *Build APK* → wait for the green tick (~3 min).
3. Open the finished run → **Artifacts** → download `MarseloAnime-apk` → unzip → `app-debug.apk`.

**B) Android Studio**
Open this folder → let Gradle sync → **Build ▸ Build APK(s)** → `app/build/outputs/apk/debug/app-debug.apk`.

**C) Command line** (JDK 17 + Android SDK installed): `./gradlew assembleDebug`

Install: copy the APK to your phone and open it (allow "install unknown apps" for your file manager/browser).

## What it blocks
- **Ads / trackers:** every request to a host in `app/src/main/assets/blocklist.txt` gets an empty response, including requests made from inside the video player iframes. Add domains there and rebuild.
- **Pop-ups:** `window.open` / `target=_blank` windows are refused.
- **Redirect hijacks:** the app can only navigate to anime.marse.lol. Taps on AniList, MyAnimeList, YouTube, Discord and theanimecommunity.com links open in your normal browser; every other off-site jump is dropped.
- Fullscreen video works and rotates to landscape.

## Limits
- The block list is hand-picked, not the full EasyList. A new ad domain from a player site will get through until you add it.
- Ads that the video server serves from its *own* domain can't be told apart from the video, so they can't be blocked.
- Debug-signed APK: fine for your own phone, not for the Play Store.
