package com.music.spotui.di

/** New entries from a fully loaded list, retaining repeated songs in their original order. */
internal object QueueOccurrences {
    fun <T> additions(existing: List<T>, loaded: List<T>, identity: (T) -> String): List<T> {
        val remaining = existing.map(identity).filter(String::isNotBlank).groupingBy { it }.eachCount().toMutableMap()
        return loaded.filter { item ->
            val id = identity(item)
            if (id.isBlank()) false else {
                val count = remaining[id] ?: 0
                if (count > 0) { remaining[id] = count - 1; false } else true
            }
        }
    }
}
