package com.music.spotui.data.offline

/** Never reconcile an older Spotify page while a confirmed in-app edit is missing from it. */
internal object CollectionSyncRules {
    fun reflectsEdits(trackIds: Set<String>, expected: Map<String, Boolean>): Boolean =
        expected.all { (id, present) -> (id in trackIds) == present }

    fun canReconcile(complete: Boolean, completeForSync: Boolean, trackIds: Set<String>, expected: Map<String, Boolean>): Boolean =
        complete && completeForSync && reflectsEdits(trackIds, expected)

    fun retained(previous: Set<String>, current: Set<String>, allowRemoval: Boolean): Set<String> =
        if (allowRemoval) current else previous + current
}
