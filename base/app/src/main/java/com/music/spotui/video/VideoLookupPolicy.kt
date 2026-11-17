package com.music.spotui.video

/** Saver skips automatic app-resume lookup; a visible player or video intent still requests it. */
internal fun videoLookupOnResumeAllowed(
    allowAutomatic: Boolean,
    wantVideo: Boolean,
    videoOn: Boolean,
    playerVisible: Boolean,
): Boolean = allowAutomatic || wantVideo || videoOn || playerVisible

/** The start of the song looked for in a clip (was 45 s: decoding is most of the wait). */
internal const val REFERENCE_SECONDS = 30

/**
 * How much of a clip's start is decoded: the song's start plus the clip's extra length (an
 * intro) and a margin, 45 s to 100 s. A clip as long as the song needs 45 s, not 100.
 */
internal fun clipWindowSeconds(clipSec: Long, songMs: Long): Int {
    if (clipSec <= 0 || songMs <= 0) return 100
    val extra = (clipSec - songMs / 1000).coerceAtLeast(0)
    return (REFERENCE_SECONDS + extra + 15).toInt().coerceIn(45, 100)
}
