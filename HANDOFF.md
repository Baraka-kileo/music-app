# Handoff

Android music app (Kotlin, Jetpack Compose), forked from Orpheon / Myspoti (GPL-3.0: keep the
credits and licence notices). Code is in `base/`. Work branch: `glass/full-player`.

## Build

```
cd base
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew.bat :app:assembleDebug -Pabis=x86_64,arm64-v8a
```
APK: `base/app/build/outputs/apk/debug/app-debug.apk` (package `app.orpheon.beta.dev`).

## Two looks

- **Classic**: should match Spotify's app as closely as possible (`base/app/src/main/java/com/music/spotui/ui/screens/`).
- **Immersive**: the app's own glass design, inspired by Apple Music but not a copy (`.../ui/immersive/`).

## Rules

- No piracy features. Never decrypt Spotify streams. Never build in or pre-install a downloader.
- The extension system stays as it is; downloads go through extensions the user installs.
- No Spotify name, logo or brand assets.
- Don't break Spotify users. The native player stays off.
- Commit each change on its own so it can be reverted.

## Recent work (see `git log`)

Player (both looks): scrolled page cards, sticky header when scrolled, Switch to video pill
under the art above the title, verified badge, lyric line animation, title/artist hierarchy.
Queue: History, sleep timer by the title, smaller controls while open. Classic: Settings root
list, left profile drawer. Fixes: stale like toast, profile read retry, Accounts "In use".

## Open items

1. Slow first start (~10 s) and a startup ANR: `MyspotiRuntime.start` / `WebPlayerHost` creates
   the WebView eagerly. Changes how the session starts: test Spotify sign-in carefully.
2. YouTube Music skips take 2-3 s: it plays through YouTube Music's official page in a hidden
   WebView (`base/api-ytmusic/.../YtmPlayer.kt`, `ytm-page.js`). Commit 49443c0 shows the next
   song at once; next: switch songs inside the open page, keep it warm, time each step.
3. Liking right after launch sometimes says "Could not update your library".
4. Media notification sometimes showed another song while songs changed fast (not reproduced).
5. Downloads plan (UI only, engine in `base/app/.../data/offline/` stays): one-tap download with
   automatic source choice, a download queue screen, offline mode, then a smart cache.
6. Not yet checked on a device: Classic sticky header and pill, the like toast fix, YouTube Music
   next-song display.
