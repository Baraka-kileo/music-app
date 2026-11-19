# Feature map: Spotify and YouTube Music

Every action a user can tap, what it does with each music account, and where it stands.
"Code" means it is written and builds; "device" means it was seen working on a phone.

Status: ✅ code done · 🧪 needs a device test · ❌ not done · ➖ not on that service

## Songs (3-dot menu, both looks, in lists and in the player)

| Action | Spotify | YouTube Music | Status |
|---|---|---|---|
| Like / unlike | Liked Songs | like (Liked Music) | ✅ 🧪 |
| Add to playlist (Saved in) | playlist edit | `browse/edit_playlist` ADD | ✅ 🧪 (tap before loading no longer says "refused") |
| Remove from this playlist | playlist edit | edit REMOVE by entry id | ✅ 🧪 |
| Play next / add to queue | app queue | app queue | ✅ 🧪 |
| Download / remove download | Spotify id key | `ytm_<video>` key | ✅ 🧪 |
| Song radio | Spotify radio playlist | YouTube Music radio (`RDAMVM`) played at once | ✅ 🧪 |
| Go to album | album id | whole `ytmusic:album:` uri | ✅ 🧪 |
| Go to artist | catalog track's artists | YouTube Music track's artists | ✅ 🧪 |
| Credits | Spotify credits | ➖ YouTube Music has none | row greyed |
| Share / copy link | open.spotify.com + Spotify Code | music.youtube.com link, no code picture | ✅ |
| Not interested, taste profile | Spotify | ➖ | Spotify only |
| Player menu (same rows) | | the player's song now carries its YouTube Music uri | ✅ 🧪 |

## Playlists

| Action | Spotify | YouTube Music | Status |
|---|---|---|---|
| Create | `playlists.create` | `playlist/create` (private) | ✅ 🧪 (new row keeps its whole uri) |
| Rename, description | playlist edit | SET_PLAYLIST_NAME / DESCRIPTION | ✅ 🧪 |
| Public / private | access | SET_PLAYLIST_PRIVACY | ✅ 🧪 |
| Delete | delete | `playlist/delete` | ✅ 🧪 |
| Reorder songs | move | MOVE_VIDEO_BEFORE | ✅ 🧪 |
| Save someone's playlist (follow) | follow | like of the playlist | ✅ 🧪 |
| Owner's menu rows (edit, privacy, delete) | owner = me | editable on YouTube Music | ✅ 🧪 |
| Change cover | upload | ➖ not offered by YouTube Music | refused with a message |
| Collaborators, profile, Blend | Spotify | ➖ | hidden on YouTube Music |
| Add whole playlist to another | merge | ❌ | Spotify only |

## Library

| Action | Spotify | YouTube Music | Status |
|---|---|---|---|
| Remove album / artist / saved playlist | remove | unlike / unsubscribe | ✅ 🧪 |
| Pin, folders | Spotify | ➖ | hidden on YouTube Music |
| Liked Songs list drops an unliked song | library key | its YouTube Music key | ✅ 🧪 |

## Artists

| Action | Spotify | YouTube Music | Status |
|---|---|---|---|
| Follow | follow | subscribe (Immersive: Follow button; Classic: Subscribe button) | ✅ 🧪 |
| Share / copy link | Spotify | music.youtube.com/channel | ✅ |
| Don't play this artist | Spotify | ➖ | Spotify only |

## Playback and sync

| Item | Status |
|---|---|
| Audio: every link checked before playing, NewPipe as backup | ✅ 🧪 |
| Plays counted in the YouTube Music account (playback, atr, watchtime reports) | ✅ 🧪 |
| Covers sharp (544 px from Google's image server) | ✅ 🧪 |
| Podcast episode starts but its player does not open and the previous song comes back | ❌ needs a device log |
| A new playlist is slow to open the first time | ❌ needs timings |

## Look (Immersive)

| Item | Status |
|---|---|
| Round and pill buttons are Liquid Glass (as the bars) | ✅ 🧪 |
| Playlist header: round shuffle, white Play, round save, "Playlist · count · length" | ✅ 🧪 |

## Messages

While YouTube Music is the account, a message that names Spotify names YouTube Music
(`AccountWords`, used by every toast and in-app notice). Nothing changes with Spotify.
