package com.clicky.memory

data class GuidanceAuditEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String,
    val parameters: Map<String, String>,
    val result: String
)

class GuidanceAuditRepository {

    private val maxEntries = 100
    private val _audits = mutableListOf<GuidanceAuditEntry>()

    @Synchronized
    fun addEntry(entry: GuidanceAuditEntry) {
        _audits.add(0, entry)
        if (_audits.size > maxEntries) {
            _audits.removeAt(_audits.lastIndex)
        }
    }

    @Synchronized
    fun getAudits(): List<GuidanceAuditEntry> {
        return _audits.toList()
    }

    @Synchronized
    fun clear() {
        _audits.clear()
    }
}
