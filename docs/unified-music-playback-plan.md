# Unified music account and playback architecture

Baseline: `codex/before-login-revera`, commit `8d24f9e7`. Analysis only; no behavior changed. This builds on `youtube-music-account-audit.md`. Source statements are static inspection, not device verification.

## User problem and confirmed code paths

- Spotify account + YouTube audio: Spotify tracks carry Spotify IDs; the app searches for a YouTube match, caches the mapping and streams through the local ExoPlayer. Extensions and Spotify fallback participate according to source order.
- YouTube Music account + YouTube track: the app routes the track to the YouTube Music web page (`SongPlayer.playCatalogSong`, `YtmPlayback.Engine`), not the built-in YouTube resolver. The page supplies its own queue and recommendations; its queue is not exposed through the app player. Queue editing, shuffle and repeat are not implemented in that player adapter.
- Built-in YouTube audio is added to the audio source list only when `SongPlayer.accountPlays` is true, meaning Spotify account. In `Extensions.savedLine`, the YouTube-account branch returns extension IDs without the built-in ID. This explains a missing source option without assuming an extension is required.
- `ExtensionAudio.playList` rejects tracks without a Spotify ID, and `AudioRoute`/`LocalPlayer` are Spotify-ID-oriented. Adding a menu entry alone cannot make native YouTube tracks use the local resolver or source switch.
- `SourcePicker` returns without drawing if it finds no non-account source. The immersive player hides the source label when it has no label and the source visibility preference, ad state, source availability and local-player state affect display. Its `remember(registry, lead)` does not explicitly depend on the account; stale choices after an account switch are plausible but need a reproduction. The report that it appears only after opening a playlist is not yet proven to have a single cause.
- YouTube account API reads some library/home/search data but refuses library and playlist writes; saved checks return false. Native YouTube downloads are blocked by Spotify-ID-based offline entry points. Spotify-specific Connect, Jam, Blends and profile calls are explicitly refused for YouTube account. Page-backed YT playback is not guaranteed ad-free.
- Source switching in the quality picker is session-only; Settings holds the persistent source order. That distinction and presenting built-in functionality beside extension installation makes the UI hard to understand.
- Delay is observed, not measured. YouTube-native web playback wakes and loads a separate web app, then opens the track. Spotify-to-YouTube local playback has matching, stream resolution and buffering; native YouTube IDs can skip the matching step. We cannot attribute measured delay until instrumented.

## Reference projects

- Melo commit `0674e5d`: a YouTube video ID is a direct `ytm://track` item; Spotify tracks without a signed-in Spotify account use `ytm://match` and search/match. Signed-in Spotify playback follows Spotify's own path, so its default routing is not identical to our selectable YouTube source. Media3 handles playback and queue; next-item preload and up to two YouTube-ahead cache fills are implemented. GPL-3.0; preserve credits, plus third-party notices for reused code.
- BitChord commit `acf219a`: native queue/coordinator and YouTube playback, with YouTube radio and direct YouTube media items. It also supports Spotify library integration. It is not evidence that all our account features can be copied without further work. GPL-3.0; retain notices.
- Flow commit `10b7d34`: local-copy routing and managed downloadable media with range transfer, validation, recovery and metadata. Its own YouTube IDs avoid Spotify matching for YouTube-native content. Service and licensing requirements still apply.

## Recommended model

1. Account: Spotify, YouTube Music, or none. Determines library, playlist ownership, account writes, history and default discovery/radio provider.
2. Song identity: a provider-qualified canonical ID (`spotify:track:...`, `ytmusic:track:...`, or local), plus metadata and optional verified cross-service matches. No hard-coded assumption every song has a Spotify ID.
3. Audio provider: Spotify, YouTube Music built-in, local download or optional extensions. An explicit persistent default and optional current-song override. Show availability and failure details; do not advertise a provider for a track until its route is actually supported.
4. Queue and discovery: one app-owned queue when local audio is active, regardless of account. Track where context/recommendations came from. Spotify playback and web-page playback remain separate adapters with explicit limitations; do not pretend all queues can be made identical.
5. Shared player interface: playback state, queue, seek, source, repeat/shuffle, error and capabilities exposed consistently to both UI looks. Local Media3 engine can use `DirectYouTube(videoId)` for native YT and `MatchedYouTube(spotifyId)` for Spotify tracks. Preserve the proven existing Spotify-to-YouTube implementation as first adapter, reuse its resolver and range/cache code, and introduce direct-ID support without a match search.
6. Downloads and cache: provider-qualified identity, separate explicit download and opportunistic cache; migration of existing indexes and encryption with regression checks. Don't assume matching or remote downloads are always available.
7. Lyrics/video: use canonical identity + actual recording metadata and duration. Preserve provider-specific features and matching; warn when alignment differs. Never silently replace a sped-up/live version with a normal cut.

## Behavior for common cases

- Spotify playlist + YouTube audio: Spotify account/playlist/radio chooses tracks; map each once to YouTube; play locally. The optional Spotify background mirror is a separate queue and does not make both recommendation lists identical.
- YouTube playlist + built-in YouTube audio: use the playlist's YouTube video IDs directly; native local player and queue; request YouTube continuation/radio when supported. No Spotify search or extension needed.
- Spotify audio: use the existing Spotify player and its own queue/capabilities; preserve current behavior and account requirements.
- Extension: optional installed provider, with supported identity/metadata and explicit failure handling; existing extensions remain available. A YouTube-specific provider should receive an appropriate identity through a backwards-compatible API.
- Downloaded track: local file first when valid; UI shows Downloaded, not a misleading network provider.

## UI for ordinary users

- Setup: one screen for Music account, one simple Audio source setting (Automatic, YouTube Music, Spotify if applicable, Advanced sources). Explain which account owns the library.
- Settings: built-in sources always visible when available; Extensions separate as optional additions. Keep persistent default distinct from a temporary song override.
- Player: stable source row in both looks from app startup, showing Playing via YouTube Music / Spotify / Downloaded, plus loading or unavailable state; tapping opens choices. Don't infer actual source from selected preference.
- If a capability isn't supported, disable with a short reason or hide provider-specific controls; don't present a silently working-looking option. Queue view labels playlist vs explicit queue vs recommended, and recommendation service where it matters.
- Source menu needs account, registry, provider capabilities and current player as observable dependencies. Test account switches and cold startup; don't claim the suspected stale remember is the sole playlist-related issue.

## Staged implementation and validation

0. Preserve branch and working playback. Add timestamps: tap, metadata ready, match-cache hit/miss, search, stream resolution, player ready, first audible frame, queue fill. No credentials, full URLs or tokens. Compare p50/p95 on device, cold/warm, Wi-Fi/mobile, cache on/off.
1. Fix account-dependent source-list logic and observable UI state with regression tests; UI exposes only working actions. Independently reproduce the startup/playlist issue and record root cause.
2. Add provider-qualified track/queue identity and direct YouTube video-ID route through existing local player. Test no Spotify lookup on native YT, correct versions, cache keys, error/cancellation and original Spotify playback.
3. Add YouTube-native playlist queue and radio continuation with explicit autoplay, repeat/shuffle, cancellation and duplicate policy; retain Spotify queue behavior and mirror controls. Verify both screens and rapid Next.
4. Implement native YouTube downloads, playlist sync, metadata and audio/lyrics validation, with safe migration and retention of existing Spotify files; test offline and interrupted operations.
5. Implement actual account mutations with provider-specific APIs and clear unsupported states; test login, account switch, reading and writing on real accounts. Research current service policies and rate limits before distribution.
6. Lyrics/video integration and alignment, then measure latency and optimize on evidence: direct IDs, bounded ahead preparation, avoid UI blocking, retries and expiry. No blanket speed promise.
7. Test signed-in Spotify and YouTube accounts, no-account, all source choices, existing extensions, both looks, playback failure and offline scenarios. See companion audit checklist.

## Open product choices

- When YouTube Music is the account, should its built-in audio be the default for newly selected tracks? Recommended: yes, with an accessible page-player fallback only when supported.
- When the user has a Spotify playlist and chooses YouTube audio, should Spotify remain the default recommendation service? Recommended: yes, since the context is Spotify, with a clearly labelled optional YouTube radio.
- Spotify mirror: communicate that the app's queue and Spotify Connect queue may differ; exact two-way synchronization requires a separate design and tests.

## YouTube account likes, artist pages and visual media

- Existing account adapter can read the YouTube Music home, history, playlists and liked songs through its session; success and completeness still require live device validation. Web-page playback uses that page's Up Next; the separate Spotify-style recommendation helper is not a YouTube radio implementation.
- `YtmUserLibrary.contains` always returns false and `add`/`remove` throw `NotOnYtm`: there is no working account-side like/unlike write in this adapter. Do not claim a heart changed the YouTube Music account until an account read verifies it. Distinguish a local favorite from a service like.
- `YtmParse.artist` already attempts to read header description, songs, albums, related artists and images; its `Artist` constructor leaves follower, monthly listener and verified fields at defaults. A missing bio may be a parser/renderer/UI issue or upstream absence. Audit and test each stage.
- Add a provider-specific artist profile model with provenance and optional fields: YouTube bio, channel subscribers where reliably available, channel/artist identity and verified badge only if verifiable; Spotify followers and monthly listeners remain distinctly labeled. Missing data is unknown, never fabricated. Spotify metrics must not be presented as YouTube figures. Match cross-service artists with IDs and conservative evidence; do not force match ambiguous artists.
- The official YouTube Data API channel resource documents descriptions, thumbnails and public subscriber counts (rounded), but no equivalent Spotify monthly listeners or general verified-badge field in its documented representation. API keys, quotas, policy and artist-channel mapping need review.
- Canvas: distinguish Spotify Canvas (short looping visual), official YouTube music video and generic fallback artwork. Current Canvas paths expect Spotify identity, and YouTube account adapter refuses Spotify extras. Do not claim a YouTube official equivalent to Spotify Canvas; investigate authorized, available visuals. A full music video can be an optional video mode, not falsely labeled Canvas, and needs recording matching and synchronization; avoid downloading or looping arbitrary third-party clips without rights.
- For account likes: a provider-specific repository with verified read, write, failure and refresh, user-facing pending/error state, reconciliation after restart, account-switch isolation and tests with both providers. Confirm authorized service endpoints and policies first. Keep existing Spotify behavior.
- Device tests: load existing YouTube likes and personalized home, like/unlike in app then verify in YouTube Music and vice versa, restart/network errors, own playlists; test artist description, source labels, subscriber count, verified state only where evidenced, and visual media in Classic/Immersive. Test account switching and fallback attribution.

## BitChord YouTube Music account research (commit acf219a7e4a7f52f026d4336d5c7ac5cf7bfafcc)

Evidence: `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/Innertube.kt`, `YtMusicRepository.kt`, `app/src/main/java/com/music/bitchord/ui/MainViewModel.kt`, `playback/Autoplay.kt`, `auth/YtMusicLoginScreen.kt`. This is code review, not a live verified account test; endpoint behavior can change.

- Account: the Android login opens a Google/YouTube Music web flow, then captures session context and lets users confirm the correct account/channel. Subsequent YouTube Music web-client-style InnerTube requests use account context. Google OAuth policy and WebView sign-in reliability require separate research; do not copy embedded Google OAuth as the supported route by assumption. Never log or expose cookies, tokens, email, or codes.
- Home and library: authenticated requests retrieve personalized feeds, likes, saved songs and shelves. Library fetches multiple feeds concurrently; liked-song IDs are seeded from the first page and remaining pages are read in the background, with repeated-token protection. Failures of individual feeds may result in partial data, so our UX must distinguish partial/unknown from empty.
- Likes: rates a YouTube video ID as like/dislike/neutral through InnerTube endpoints. Its response handling checks body errors even on HTTP success. UI applies the change optimistically and rolls back on failure. Liked status is built from account data and local session overrides, not a hardcoded false. It refreshes/invalidates library state after success. Reconcile with the service after account change and restart; handle offline/retry and out-of-order taps.
- Likes vs saved items: BitChord treats them as distinct server states; in the inspected code unliking can also attempt removal from the library using a feedback token. That is BitChord's product choice, not a required behavior. For our app, default to a like action changing the like only; make removing from library a separate explicit action unless product behavior is deliberately specified.
- Playlists: code creates/deletes, adds/removes and reorders through playlist mutation requests. It preserves per-entry IDs (`setVideoId`) so duplicate tracks can be removed/reordered precisely; requests check returned status. Safe ordering requires a complete current list and handling failure/partial results. Review account ownership, duplicates, concurrent edits, privacy and rollback.
- Artist: subscription is a channel-specific action separate from likes. It requires correct artist-to-channel mapping and on failure restores UI state. Saved album/playlist is also distinct from song rating.
- Radio/recommendations: `next` uses a YouTube video seed and radio playlist request; repository parses watch queue. Autoplay deduplicates, appends within a limit, and if the source item isn't a YouTube video it first seeks a YouTube match. This is a YouTube radio queue, not proof the same results will appear in YouTube Music app or that every request is personalized.
- Playback and identity: YouTube video IDs are first-class in the native local queue; Spotify-import tracks may require matching before YouTube playback/radio. A result's source, account and playlist owner must remain distinct.
- Risks/limits: endpoints and session flows are private/undocumented and can change; they are not the official YouTube Data API. Verify current service terms, data usage, account safety, region and quota behavior before distribution. GPL-3.0 applies to BitChord-derived source and existing Myspoti notices remain; preserve source attributions for any actual port. Do not claim every BitChord feature works for all accounts or that optimistic UI proves account sync.

### Proposed staged implementation based on this research

1. Define provider-qualified track, account/channel and playlist-entry IDs; model independent rating, saved library and subscription state. Add safe account-bound repository contracts and fake-server tests.
2. Implement read-side YouTube account state with pagination, unknown/loading/error states and account-switch cache isolation. Show personalized home and liked tracks without pretending data is complete.
3. Implement like/unlike through a provider adapter only after verifying permitted integration; inspect response body, support race-safe optimistic update/rollback, refresh and cross-app verification. Preserve Spotify implementation.
4. Add playlist writes with per-entry IDs, ownership/privacy and complete-list guards, then subscriptions and album save. Keep a local playlist separate and explicitly labelled.
5. Add direct-ID local playback and YouTube radio/continuation with bounded queue, duplicate handling, cancellation, autoplay/repeat and recommendations attribution. Preserve Spotify audio/mirror and extensions.
6. Test real devices and accounts: like here -> confirm in official app; like there -> refresh here; unlike; duplicate entries; multi-channel account; sign-out/in; network failures; 401/403/429 and HTTP-200 errors; playback/queue in both looks. Record actual end-to-end outcomes before declaring it working.
