# Architecture review (2026-11-19)

How the app is built today, where it makes features hard, and what to change before the
features of issue #331 (choose where recommendations come from, save the queue, mood chips,
quality per device).

All numbers are counts in `base/` on branch `codex/before-login-revera`.

## What is good

- **Modules for the services.** `api` is a contract; `api-spotify`, `api-ytmusic`, `catalog`,
  `extensions`, `automix` and `engine-android` implement it. A new service fits this shape.
- **Both phone looks and TV share the view models** (Home, Search, Library, Player, Artist…).
  The data a screen shows is loaded once, whatever the look.
- **One queue view for the screens.** `SongPlayer.engineQueue` (current, queued, next up) is read
  by the Classic, Immersive and TV queues, whichever player is playing.
- **Tests exist:** 119 test files in the app and 140 in the modules.

## The problems

### 1. Every screen is written two times, three with TV

| | Files | Lines |
|---|---|---|
| Classic screens (`ui/screens`) | 40 | 18,600 |
| Immersive (`ui/immersive`) | 97 | 26,100 |
| TV (`ui/tv`) | 8 | 2,000 |
| Shared parts (`ui/components`) | 70 | 11,200 |

`MyNavHost` picks `ImmersiveX` or `X` for almost every page. The menus are copied too: the song
menu exists as `SongOptionsSheet` (457 lines) and `ImmersiveSongOptionsSheet` (364 lines), with
the same rows written twice. Only some sheets are shared (`LibraryItemSheet`, `PlaylistOptionsSheet`).

**Effect:** each feature is built two times, and the two drift. Seen this month: the Classic
grid had no menu; Follow was on one artist page and not the other; the song-menu fix had to be
made twice.

### 2. Screens decide by account, everywhere

154 tests like `startsWith("spotify:")`, `startsWith("ytmusic:")` or `YtmRuntime.chosen` are spread
through the app: 35 in Classic screens, 22 in Immersive, 23 in shared parts, 37 in the players,
26 in data.

**Effect:** a feature added for one service is missing or broken on the other (the "Spotify
refused" messages on YouTube Music; the edit rows that went missing). A third choice
("Both") would have to be added at each of the 154 places.

### 3. `SongPlayer` does everything

`di/SongPlayer.kt` is 3,835 lines and 178 functions in one global object. It:
- chooses the player (Spotify engine or local)
- keeps the queue and now-playing state
- does likes, radio and the messages

The screens call it directly 396 times. `LocalPlayer.kt` adds 2,346 lines.

**Effect:** any queue or playback change touches this file. It is the most likely place for one
fix to break another thing (the podcast bug lives here).

### 4. Screens load their own data

The screens:
- call the API from inside composables 85 times (`produceState` / `LaunchedEffect`)
- hold 214 `runCatching`

**Effect:** that logic is not tested, and each look writes it again.

### 5. A song is shaped like a Spotify song

`SongsModel` has:
- `spotifyTrackId`
- a YouTube Music song kept as `url = "catalog:ytmusic:track:…"` with an empty Spotify id

Each screen decodes this in its own way.

**Effect:** saving a queue, a playlist or a download across services means guessing the id
again each time.

### 6. Global state and settings are scattered

- 221 top-level `object`s, and 183 Compose states inside non-UI code (`di`, `data`)
- 112 `getSharedPreferences` calls behind 32 separate `…Pref` objects

**Effect:** new settings (a recommendation source per area, quality per device) would add more
objects with no single place to read or reset them.

## What this means for issue #331

| Feature | What blocks it today |
|---|---|
| Choose Spotify / YouTube Music / Both for recommendations | Recommendations are fetched in 8 files: `Api`, `LocalPlayer`, both song menus, both search screens, `ContextScreen`, `PlayerViewModel`. Each one picks the service from the song's id. "Both" also needs the two sessions signed in at once, and the app has one account at a time. |
| Up Next: refresh only the suggestions, mood buttons | `QueueItem` has no "where it came from" (manual, playlist, or suggested by Spotify or YouTube Music), so the app cannot replace only the suggestions. When Spotify's own engine plays, its next songs come from Spotify's server. |
| Save the queue as a playlist | It needs each song's id on the account's service (problem 5). It needs a new screen in both looks (problem 1). |
| Home with Both, mood chips | The home is read per account. Its chips are Spotify's; YouTube Music's moods are not read. |
| Quality per network and device | The settings are scattered (problem 6). The two players (Spotify engine, local) each handle quality their own way. |

## What to change, in order

Each step is small, keeps everything working, and can ship alone. No rewrite.

1. **One menu, drawn by each look.**
   - A shared `SongActions` builds the menu rows from the song and the account: label, icon, on/off, what it does. Classic and Immersive only draw them.
   - Order: song menu first (it is the copied one), then the playlist menu, the library menu and the artist page.
2. **Account capabilities** (step 3 of `player-architecture.md`).
   - Use `account.can(X)` in place of the 154 uri tests, one screen at a time.
   - "Both" becomes a value, not a new branch.
3. **Song ids** (step 4 of `player-architecture.md`).
   - A song has `ids: Map<provider, id>`.
   - The queue, playlists, downloads and counting read the id they need.
4. **Split `SongPlayer` behind the same name.** The screens keep calling `SongPlayer.x`, so nothing changes for them. Move out:
   - `QueueStore`: one queue, each item has an origin (manual, context, suggested-by-X)
   - `PlaybackRouter`: which player
   - `NowPlayingState`
   - `Recommender`: Spotify, YouTube Music or both, deduplicated by song ids
5. **Data in view models.** When a screen is touched for a feature, move its API calls from the composable into its view model, where they can be tested.
6. **One settings store** for new settings (a typed store, one place to read and reset). Old `…Pref` objects move only when touched.
7. **The looks stay, all three.** Classic (the Spotify-like look) has its users and stays.
   - A screen is split in two:
     - its **state and actions** (view model, menu rows, what each button does), written once
     - its **drawing**: one per look, and only drawing (no API calls, no account checks)
   - A new feature is then the shared state and actions, written once, plus a small drawing per look.
   - Each look's drawing is checked with preview and screenshot tests from the same state.

With 1 to 4 done, the issue's features become:
- a `Recommender` choice per area
- a "Save queue" action on `QueueStore`
- one new menu row, written once

None of them needs the same change in many places.
