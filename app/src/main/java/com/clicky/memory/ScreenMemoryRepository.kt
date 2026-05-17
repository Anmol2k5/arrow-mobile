package com.clicky.memory

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenMemoryEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val nodeCount: Int,
    val userIntent: String? = null
)

class ScreenMemoryRepository {

    private val maxEntries = 50
    private val _history = MutableStateFlow<List<ScreenMemoryEntry>>(emptyList())
    val history: StateFlow<List<ScreenMemoryEntry>> = _history.asStateFlow()

    fun addEntry(entry: ScreenMemoryEntry) {
        val current = _history.value.toMutableList()
        current.add(0, entry)
        if (current.size > maxEntries) {
            current.removeAt(current.lastIndex)
        }
        _history.value = current
    }

    fun getRecentEntries(count: Int = 10): List<ScreenMemoryEntry> {
        return _history.value.take(count)
    }

    fun clear() {
        _history.value = emptyList()
    }
}
