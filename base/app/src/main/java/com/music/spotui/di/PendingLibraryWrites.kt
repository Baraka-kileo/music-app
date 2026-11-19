package com.music.spotui.di

/** Tracks the latest intent and last confirmed value separately for each library item. */
internal class PendingLibraryWrites {
    private data class Entry(var latest: Long, var confirmed: Boolean, val outstanding: MutableSet<Long>)
    private val entries = mutableMapOf<String, Entry>()
    private val revisions = mutableMapOf<String, Long>()
    private var sequence = 0L

    fun begin(uri: String, previous: Boolean): Long {
        val revision = ++sequence
        revisions[uri] = revision
        val entry = entries.getOrPut(uri) { Entry(revision, previous, mutableSetOf()) }
        entry.latest = revision
        entry.outstanding.add(revision)
        return revision
    }

    /** Returns the confirmed value to restore only when the newest write failed. */
    fun finish(uri: String, revision: Long, value: Boolean, succeeded: Boolean): Boolean? {
        val entry = entries[uri] ?: return null
        if (!entry.outstanding.remove(revision)) return null
        if (succeeded) entry.confirmed = value
        val rollback = if (!succeeded && entry.latest == revision) entry.confirmed else null
        if (entry.outstanding.isEmpty()) entries.remove(uri)
        return rollback
    }

    fun isPending(uri: String): Boolean = entries.containsKey(uri)
    fun revision(uri: String): Long = revisions[uri] ?: 0L
    fun clear() { entries.clear(); revisions.clear() }
}
