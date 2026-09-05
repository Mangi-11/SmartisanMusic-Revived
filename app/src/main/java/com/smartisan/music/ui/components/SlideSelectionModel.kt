package com.smartisan.music.ui.components

internal data class SlideSelectionChange(
    val key: String,
    val selected: Boolean,
)

internal class SlideSelectionModel {
    private var anchorPosition = -1
    private var targetSelected = false
    private var expandedFromAnchor = false
    private val activeKeys = linkedSetOf<String>()
    private val baselineSelected = mutableMapOf<String, Boolean>()
    private val currentSelected = mutableMapOf<String, Boolean>()

    val active: Boolean
        get() = anchorPosition != -1

    fun begin(
        position: Int,
        key: String,
        selected: Boolean,
    ) {
        reset()
        anchorPosition = position
        targetSelected = !selected
        baselineSelected[key] = selected
        currentSelected[key] = selected
    }

    fun changesThrough(
        position: Int,
        keyAtPosition: (Int) -> String?,
        isSelected: (String) -> Boolean,
    ): List<SlideSelectionChange> {
        if (!active || position == -1) {
            return emptyList()
        }
        if (position != anchorPosition) {
            expandedFromAnchor = true
        }
        val range =
            if (position == anchorPosition && expandedFromAnchor) {
                emptyList()
            } else if (position >= anchorPosition) {
                anchorPosition..position
            } else {
                anchorPosition downTo position
            }
        val nextKeys = linkedSetOf<String>()
        val changes = mutableListOf<SlideSelectionChange>()
        range.forEach { nextPosition ->
            val key = keyAtPosition(nextPosition) ?: return@forEach
            nextKeys += key
            baselineSelected.getOrPut(key) { isSelected(key) }
        }
        activeKeys
            .filter { key -> key !in nextKeys }
            .asReversed()
            .forEach { key ->
                val baseline = baselineSelected.getValue(key)
                if (currentSelectionOf(key) != baseline) {
                    changes += SlideSelectionChange(key, baseline)
                }
                currentSelected[key] = baseline
            }
        nextKeys
            .filter { key -> key !in activeKeys }
            .forEach { key ->
                if (currentSelectionOf(key) != targetSelected) {
                    changes += SlideSelectionChange(key, targetSelected)
                }
                currentSelected[key] = targetSelected
            }
        activeKeys.clear()
        activeKeys += nextKeys
        return changes
    }

    fun reset() {
        anchorPosition = -1
        targetSelected = false
        expandedFromAnchor = false
        activeKeys.clear()
        baselineSelected.clear()
        currentSelected.clear()
    }

    private fun currentSelectionOf(key: String): Boolean {
        return currentSelected[key] ?: baselineSelected.getValue(key)
    }
}

internal fun <T> Set<T>.withSelection(
    value: T,
    selected: Boolean,
): Set<T> = if (selected) this + value else this - value
