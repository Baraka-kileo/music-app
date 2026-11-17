package com.music.spotui.data.offline

import org.junit.Assert.*
import org.junit.Test

class CollectionSyncRulesTest {
    @Test fun waitsUntilEveryConfirmedAdditionAndRemovalIsVisible() {
        val pending = mapOf("added" to true, "removed" to false)
        assertFalse(CollectionSyncRules.reflectsEdits(setOf("old"), pending))
        assertFalse(CollectionSyncRules.reflectsEdits(setOf("added", "removed"), pending))
        assertTrue(CollectionSyncRules.reflectsEdits(setOf("added", "old"), pending))
    }
    @Test fun noExpectedEditsAllowsCompleteBackgroundSnapshot() {
        assertTrue(CollectionSyncRules.reflectsEdits(emptySet(), emptyMap()))
    }
    @Test fun unfinishedAndFailedPagesCannotRemoveDownloadedTracks() {
        assertFalse(CollectionSyncRules.canReconcile(false, true, emptySet(), emptyMap()))
        assertFalse(CollectionSyncRules.canReconcile(true, false, emptySet(), emptyMap()))
        assertTrue(CollectionSyncRules.canReconcile(true, true, emptySet(), emptyMap()))
    }
    @Test fun latestRapidLikeOrUnlikeWins() {
        val edits = mutableMapOf("track" to true)
        edits["track"] = false
        assertFalse(CollectionSyncRules.reflectsEdits(setOf("track"), edits))
        assertTrue(CollectionSyncRules.reflectsEdits(emptySet(), edits))
    }
    @Test fun cachedOrOptimisticScreensCannotDropExistingDownloads() {
        assertEquals(setOf("old", "new"), CollectionSyncRules.retained(setOf("old"), setOf("new"), false))
        assertEquals(setOf("new"), CollectionSyncRules.retained(setOf("old"), setOf("new"), true))
    }
}
