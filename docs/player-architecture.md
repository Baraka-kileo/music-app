# Player architecture: account, audio source, play counting

## The problem today

The app mixes three different questions into one:

1. **Whose library is this?** (the account: Spotify or YouTube Music)
2. **Where does the sound come from?** (the audio source: Spotify's stream, YouTube audio, an extension, a download)
3. **Where is the play counted?** (Spotify's history and recommendations, or YouTube Music's)

Because they are mixed, there are three players and the results depend on which one a song lands in:

| Player | Used when | Counts plays on |
|---|---|---|
| Spotify engine (Web Player / native) | Spotify account, source = Spotify | Spotify (by itself) |
| Local player (ExoPlayer) | any song an extension or the built-in YouTube source plays; YouTube Music songs with a YouTube Music account | the account's sync, **only if "Count plays" is on** |
| Hidden YouTube Music web page (`YtmPlayback`, `playYtm`) | a YouTube Music song with no Spotify match (Spotify account); a YouTube Music song the local player could not start (YouTube Music account); "resume" of such a song | **YouTube Music**, whatever the account |

What the user sees from this:

- **Spotify account + YouTube source:** songs play in the local player. They are counted on Spotify only if "Count plays" is on, and it is **off by default on a new install**. So switching the source to YouTube silently stops Spotify from learning what you play.
- **Spotify account + a YouTube Music song** (from search, Home or a link) with no Spotify match: it goes to the hidden YouTube Music page. That is a second player with its own queue, so it is counted on **YouTube Music**, not Spotify. Next and previous then act on that page's queue, not the app's.
- **YouTube Music account:** most songs use the local player, but a failure falls back to the hidden page. You get the same mix there.
- **Screens:** they test `uri.startsWith("spotify:")` everywhere instead of asking what the account can do. So features either break on YouTube Music or are hidden without a reason.

## The design: three separate layers

```
            ┌────────────────────────────┐
  taps ───▶ │ Account                    │  library, likes, playlists, follow, home, search,
            │ (one: Spotify | YT Music)  │  history, what the menus offer (Capabilities)
            └─────────────┬──────────────┘
                          │ a song = { spotify id?, ytm id?, isrc?, title, artist, length }
            ┌─────────────▼──────────────┐
            │ Player (ONE queue)         │  the local player plays everything,
            │                            │  except when the source is Spotify's own stream
            └─────────────┬──────────────┘
                          │ asks, in the user's order
            ┌─────────────▼──────────────┐
            │ Audio sources              │  downloads › extensions › built-in YouTube › Spotify stream
            └─────────────┬──────────────┘
                          │ what was heard (song, position, playing)
            ┌─────────────▼──────────────┐
            │ Play counting              │  follows the ACCOUNT, never the source:
            │                            │  Spotify → mirror;  YT Music → play reports
            └────────────────────────────┘
```

### Rule 1: the account owns the library and decides the menus

- One account at a time, as today.
- Each account declares its **capabilities** once, as a single list the screens read:
  like, playlists (create, edit, delete, reorder, privacy, cover), follow, radio, credits, share link, share code, collaborators, folders, pins, podcasts, Jam, Connect, Blend.
- A screen asks `account.can(X)`, not `uri.startsWith("spotify:")`. What the account can't do is hidden, never offered and then refused.

| Capability | Spotify | YouTube Music |
|---|---|---|
| like, playlists, reorder, privacy, delete | ✓ | ✓ |
| follow artist | follow | subscribe |
| song radio | Spotify radio | YT Music radio |
| share link | open.spotify.com | music.youtube.com |
| Spotify Code, credits, collaborators, Blend, Jam, Connect, folders, pins, playlist cover | ✓ | – |
| podcasts | ✓ | only if a Spotify session also exists |

### Rule 2: one player, one queue

- The **local player** plays every song we stream ourselves, from any provider, in one queue: Next, Previous, radio and "up next" all live there.
- The **Spotify engine** plays only when the source for that song is Spotify's own stream (Spotify account with Spotify as the source, and podcasts).
- **The hidden YouTube Music page stops being a player.** A YouTube Music song always plays in the local player through the built-in YouTube source. That source now checks every link, then falls back to NewPipe. If even that fails, the song is skipped with a message. It is never handed to a second player.

### Rule 3: play counting follows the account, not the source

- **Spotify account:** whatever plays (YouTube audio, an extension, a download) is counted on Spotify by the mirror, using the song's Spotify id (found by ISRC or title/artist/length when the song has none).
- **YouTube Music account:** whatever plays is counted on YouTube Music by play reports, using the song's YouTube video id.
- **A song with no id on the account's service** (for example, a YouTube-only upload while on Spotify): it plays, but it isn't counted, and Stats for nerds says why.
- **Counting is on by default for both accounts.** The setting stays, so it can be turned off.

### Rule 4: a song carries all its ids

- A song keeps every id it has: `spotify`, `ytm`, `isrc`.
- Each layer takes the one it needs:
  - the source takes the `ytm` id to play YouTube audio
  - counting takes the account's id
  - menus take the account's id

  This replaces today's `spotifyTrackId` plus `url = "catalog:ytmusic:track:…"` pair, which every screen decodes in its own way.

## What changes, step by step

Each step builds, keeps Spotify and extensions working, and can be tested alone.

1. **Stop using the hidden page as a player.** In the five `playYtm(...)` calls in `SongPlayer.kt`, play the song in the local player through the built-in YouTube source. If that fails, show "couldn't play" instead of handing it over.
   - Result: one queue, and plays always counted on the account.
2. **Turn counting on by default** for a Spotify account too, when the source isn't Spotify. Keep the switch.
3. **Add a capability list per account** (`AccountCapabilities`), and move the menus to it one screen at a time: song menu, playlist menu, library row, artist page. Today's `startsWith("spotify:")` tests go away.
4. **Song ids:** add `ids: Map<provider, id>` to `SongsModel`. Fill it where songs are built (Spotify, YouTube Music, downloads), and read it in the player, counting and menus. Keep `spotifyTrackId` until nothing reads it any more.
5. **Podcasts:** always on the Spotify engine. With a YouTube Music account, the podcast pages show only if a Spotify session also exists.

## What is not changed

- Spotify as account plus Spotify as source still plays on Spotify's own engine, exactly as now.
- Extensions keep their API and order. The built-in YouTube source stays an audio source.
- Downloads keep their keys (`spotify id`, `ytm_<video>`).
