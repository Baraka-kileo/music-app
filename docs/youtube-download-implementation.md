# YouTube audio and downloads: implementation report

Based on Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0; Melo-derived
code and upstream notices are documented in THIRD_PARTY_NOTICES.md.

## Checkpoints

- `a170a0da`: Melo-based YouTube audio player on `codex/melo-from-clip`.
- `8c01dc96`: version-aware matching checkpoint.

## Download path

YouTube is available in the download-source order with existing extensions.
The downloader tries extension sources above it, then built-in YouTube, then
extensions below it. Existing playlist collections, shared-track retention,
queue persistence, Wi-Fi-only automatic playlist refresh, offline artwork and
optional offline lyrics are reused. The audio quality is whatever the chosen
YouTube stream supplies, not a promise of CD or hi-res audio.

The built-in path asks the playback matcher and stream resolver, requires a
high-confidence match, reads a bounded stream into a temporary file, checks
the complete byte count and Android audio metadata/duration, encrypts the file
and only then adds it to the offline index. Errors and partial files are not
accepted as complete downloads. A match not meeting the confidence threshold
shows a retryable no-match status. Scores are heuristics, not verification of
recording identity. A user-facing manual candidate chooser is not implemented.

Version tags (sped up, slowed, nightcore, live, remix, acoustic, instrumental,
karaoke, cover) must agree between the Spotify request and the candidate.
The saved match namespace was changed to avoid replaying older unchecked
matches. Downloads also refuse older cached matches without confidence data.
These checks can reject valid uploads with missing or different labels.

## What is not yet established

- No live Android phone was attached during development. Audio downloading,
  actual playback after an app restart with airplane mode, lyrics alignment,
  and playlist synchronization with the real services require device testing.
- The app's existing lyrics lookup is best-effort; matched audio can still
  differ in timing, especially for alternate versions.
- Recent audio caching and configurable storage controls are existing features.
  A verified 'recent songs offline' view is not implemented; cached audio is
  not represented as an explicit download.
- Explicit mobile downloads retain the existing warning and behavior; automatic
  playlist updates wait for unmetered Wi-Fi. A separate new mobile-data toggle
  is not implemented.
- Failed/no-match download rows use the current in-memory status system and
  can be retried while present. A persistent manual review workflow is not yet
  implemented. Unfinished pending requests use the existing restart queue.
- Stream URLs and upstream YouTube interfaces may expire or change. The source
  and account terms, and rights to retain particular recordings, require review.

## Phone test

Select YouTube in Settings > Sources > download source order; move it above
extensions if desired. Download one Spotify song, check its source/quality and
lyrics, restart the app and verify playback in airplane mode. Repeat with a
playlist, shared tracks, cancelled downloads, a changed playlist, low storage,
a version-tagged track, and both interfaces. Test extension downloads as well.
Do not remove existing downloads while validating the new path.
