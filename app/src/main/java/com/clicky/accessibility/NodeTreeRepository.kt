package com.clicky.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NodeTreeRepository {

    private val _nodeTree = MutableStateFlow<List<AccessibilityNodeSnapshot>>(emptyList())
    val nodeTree: StateFlow<List<AccessibilityNodeSnapshot>> = _nodeTree.asStateFlow()

    fun updateTree(snapshots: List<AccessibilityNodeSnapshot>) {
        _nodeTree.value = snapshots
    }

    fun getVisibleNodes(): List<AccessibilityNodeSnapshot> {
        return _nodeTree.value.filter { it.isVisibleToUser && it.hasMeaningfulContent() }
    }

    fun findNodeByText(text: String): AccessibilityNodeSnapshot? {
        return _nodeTree.value.firstOrNull {
            it.text?.contains(text, ignoreCase = true) == true ||
                    it.contentDescription?.contains(text, ignoreCase = true) == true
        }
    }
}
