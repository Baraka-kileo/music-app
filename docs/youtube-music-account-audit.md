# YouTube Music account: feature audit and device checklist

Audited branch: `codex/before-login-revera`, commit `8d24f9e7`. This is a source-code audit, not proof that a live account or phone passed. Spotify behavior is the comparison target, not a promise of identical service features. Keep Classic and Immersive in all UI tests; preserve extensions and Spotify behavior.

## What the code establishes

- A YouTube Music account uses a separate web session, account library, catalog and page-backed player. This is different from Spotify account + built-in YouTube audio source.
- YouTube-native tracks play through the YouTube Music web page (`YtmPlayback`, `YtmPlayer`), bypassing the Spotify-ID-based extension/audio route. Thus selecting the Melo-style built-in audio source does not currently guarantee it plays YouTube-native songs.
- Page-backed playback supports play, pause, seek, next and previous. Its queue is not exposed to the app, and queue edits, shuffle and repeat commands are not implemented in this route. YouTube's own page manages Up Next; verify exact behavior on a signed-in account.
- Library reads, likes listing, playlist reads, homepage, history and metadata search are implemented with limits. Library save/remove, liked status (`contains` always false), playlist creation, edits and deletion explicitly fail or return no editable lists.
- Native YouTube songs do not carry Spotify IDs. Offline song enqueue and collection membership currently use Spotify IDs; their offline handling for YouTube-native songs is not implemented by that path. Do not assume Spotify playlist downloading behavior works for YouTube playlists.
- The generic Spotify song-radio helper calls account extras, which the YouTube account adapter refuses. This is not equivalent to the YouTube web page's own Up Next. Recommendations must be tested per route.
- Spotify lyrics extras and video preparation depend on Spotify identity in relevant paths; other title/artist lyric providers exist, so test actual results instead of calling all lyrics unavailable.
- YouTube search is parsed from one search response with no further pagination or search suggestions. Home categories and several Spotify-specific account features return empty or refuse the request.
- Sign-in uses a separate Google/YouTube WebView profile. Google may reject embedded sign-in. Do not collect credentials, cookies or verification codes in reports.

## Test setup

- [ ] Record APK version, account provider, audio-source order, signed-in status, phone model, Android and WebView version. Avoid passwords/tokens.
- [ ] Repeat each applicable flow with Spotify account and YouTube Music account, in both Classic and Immersive.
- [ ] Compare a native YouTube result, Spotify result played via YouTube audio, a downloaded track and an extension-provided track. Record which player/source actually played.
- [ ] Test Wi-Fi, metered data, offline, app restart, and a second device where relevant. Distinguish expected service restrictions from defects.

## Checklist

### Account and navigation
- [ ] Sign in, cancel, return from verification, restart, sign out and switch back to Spotify. Verify correct identity, isolated account data and clear errors.
- [ ] Open Home, Library, Search, Settings and both looks; check account switching invalidates stale data.
- [ ] Verify Google account selection and consent through a supported sign-in route; document blocked WebView separately.

### Discovery and content
- [ ] Home shelves load and open the expected album, artist or playlist; check refresh and empty/error states.
- [ ] Search songs, artists, albums, playlists and mixed results. Verify labels, IDs, artwork, titles, durations, pagination, suggestions, unicode, no-result and offline states.
- [ ] Open artist, album, playlist and liked-songs pages; test > first page and large playlists. Verify context and order when launching playback.
- [ ] Verify recently played, saved albums/artists and playlist list; compare with YouTube Music directly.
- [ ] Verify song recommendations and radio after a single song and after playlist end, with autoplay on/off and repeat/shuffle. Identify service and actual next IDs.

### Playback and routing
- [ ] Play native YouTube track from search, playlist, liked songs and album, then pause/resume/seek/next/previous.
- [ ] Test playlist start, subsequent items, transitions, retry, shuffle, repeat, queue visibility, add/remove/reorder, Play next and remote controls.
- [ ] Select built-in YouTube audio, Spotify audio and a working extension in turn; verify actual provider, fallback and no late source bounce for Spotify and YouTube-native tracks.
- [ ] Test current-track and next-track buffering, rapid skips, quality, matching version, network loss/recovery, background playback, calls and audio focus.
- [ ] Test lock-screen, notifications, headphones, Bluetooth, Android Auto, Cast and multi-device controls; report unsupported capabilities clearly.

### Library and offline
- [ ] Like/unlike and confirm status survives restart and agrees with YouTube Music; currently account writes are unsupported.
- [ ] Create, rename, delete, follow and edit a YouTube playlist and add/remove/reorder songs; currently these writes are unsupported.
- [ ] Download a native YouTube song, album and playlist; verify progress, errors, retry, remove and offline playback; currently download indexing is Spotify-ID-based.
- [ ] Download a Spotify playlist via built-in YouTube audio; add/remove songs on device and externally; verify auto-sync and shared-song retention.
- [ ] Test cache eviction, free space, interrupted transfers, app restart and download/source attribution without marking partial files complete.

### Lyrics, video and extras
- [ ] Check plain, line-synced and word-synced lyrics, providers, translations and offline lyrics for both Spotify and native YouTube items; test alternate recordings and seek timing.
- [ ] Test music-video availability, matching, sync, switching and return to audio on native YouTube and Spotify tracks.
- [ ] Test artist/album links, share, explicit versions, history, likes, sleep timer, equalizer, speed and AutoMix on both player paths.
- [ ] Verify app reports Spotify-specific Connect, Jam, Blends and profile features as unavailable rather than claiming YouTube supports them.

## Recommended implementation order

1. Make YouTube-native IDs first-class across song models, queue, source routing and offline identity. Direct YouTube IDs should bypass Spotify matching; keep Spotify IDs and existing extensions intact.
2. Unify local-player queue semantics and source selection for native YouTube tracks, or explicitly define page-backed capabilities. Implement queue, repeat, shuffle, recommendations and source changes against the chosen player with tests.
3. Implement provider-specific library writes and playlist editing only after validating service behavior; handle unsupported or changed APIs explicitly.
4. Extend downloads, auto-sync and caching to provider-qualified IDs. Retain validation, account isolation and existing Spotify downloads.
5. Make lyrics and video use provider-aware identity and metadata, preserving version/sync checks.
6. Exercise both looks and both providers on real signed-in devices, with regression tests for Spotify and extensions.

## Source references

- `base/app/src/main/java/com/music/spotui/di/YtmRuntime.kt`
- `base/app/src/main/java/com/music/spotui/di/YtmPlayback.kt`
- `base/app/src/main/java/com/music/spotui/di/SongPlayer.kt`
- `base/app/src/main/java/com/music/spotui/extensions/ExtensionAudio.kt`
- `base/app/src/main/java/com/music/spotui/di/AccountApi.kt`
- `base/api-ytmusic/src/main/kotlin/app/myspoti/ytmusic/YtmAccountApi.kt`
- `base/api-ytmusic/src/main/kotlin/app/myspoti/ytmusic/YtmLibrary.kt`
- `base/api-ytmusic/src/main/kotlin/app/myspoti/ytmusic/YtmPlayer.kt`
- `base/app/src/main/java/com/music/spotui/data/offline/OfflineCollections.kt`

## Automated test observation (2026-11-18)

`api-ytmusic:test`: 18 tests passed; both `YtmPageScriptTest` tests failed in the Node VM because the test context does not define `navigator`, which the page script reads. This is a harness defect or incompatibility, not by itself proof that the actual Android WebView playback fails. A real-device test remains necessary. The app has not been modified by this audit.
- Focused app tests `MetadataOrderTest` and `YouTubeTransferTest` passed. They do not verify a signed-in YouTube Music session or end-to-end playback.
