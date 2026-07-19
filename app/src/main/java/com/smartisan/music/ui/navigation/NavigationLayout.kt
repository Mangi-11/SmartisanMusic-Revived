package com.smartisan.music.ui.navigation

/**
 * 用户可定制的主导航布局。
 *
 * [orderedDestinations] 是七个可移动内容目的地的完整排列；[bottomCount] 是排列中固定到
 * 底栏的前缀长度。`More` 不进入排列，始终作为底栏最后一项。这个表示法既没有重复状态，
 * 也能直接映射原版“下方底栏 / 上方更多”两个交换区域。
 */
data class NavigationLayout(
    val orderedDestinations: List<MusicDestination> = DefaultDestinationOrder,
    val bottomCount: Int = DefaultBottomDestinationCount,
) {
    init {
        require(orderedDestinations.size == MusicDestination.movableEntries.size)
        require(orderedDestinations.toSet() == MusicDestination.movableEntries.toSet())
        require(bottomCount in MinBottomDestinationCount..MaxBottomDestinationCount)
    }

    val bottomDestinations: List<MusicDestination>
        get() = orderedDestinations.take(bottomCount) + MusicDestination.More

    val overflowDestinations: List<MusicDestination>
        get() = orderedDestinations.drop(bottomCount)

    fun isPinned(destination: MusicDestination): Boolean {
        return destination == MusicDestination.More || destination in orderedDestinations.take(bottomCount)
    }

    /** 在不修改持久化布局的前提下，临时确保某目的地出现在底栏。 */
    fun bottomDestinationsEnsuring(destination: MusicDestination?): List<MusicDestination> {
        if (destination == null || destination == MusicDestination.More || isPinned(destination)) {
            return bottomDestinations
        }
        val pinned = orderedDestinations.take(bottomCount).toMutableList()
        pinned[pinned.lastIndex] = destination
        return pinned + MusicDestination.More
    }

    fun swap(first: MusicDestination, second: MusicDestination): NavigationLayout {
        if (first == second || !first.movable || !second.movable) {
            return this
        }
        val firstIndex = orderedDestinations.indexOf(first)
        val secondIndex = orderedDestinations.indexOf(second)
        if (firstIndex < 0 || secondIndex < 0) {
            return this
        }
        val reordered = orderedDestinations.toMutableList()
        reordered[firstIndex] = second
        reordered[secondIndex] = first
        return copy(orderedDestinations = reordered)
    }

    fun promote(destination: MusicDestination): NavigationLayout {
        if (!destination.movable || isPinned(destination) || bottomCount >= MaxBottomDestinationCount) {
            return this
        }
        val reordered = orderedDestinations.toMutableList().apply {
            remove(destination)
            add(bottomCount, destination)
        }
        return copy(
            orderedDestinations = reordered,
            bottomCount = bottomCount + 1,
        )
    }

    fun demote(destination: MusicDestination): NavigationLayout {
        if (!destination.movable || !isPinned(destination) || bottomCount <= MinBottomDestinationCount) {
            return this
        }
        val nextBottomCount = bottomCount - 1
        val reordered = orderedDestinations.toMutableList().apply {
            remove(destination)
            add(nextBottomCount, destination)
        }
        return copy(
            orderedDestinations = reordered,
            bottomCount = nextBottomCount,
        )
    }

    fun move(destination: MusicDestination, offset: Int): NavigationLayout {
        if (!destination.movable || offset == 0) {
            return this
        }
        val sourceIndex = orderedDestinations.indexOf(destination)
        if (sourceIndex < 0) {
            return this
        }
        val zone = if (sourceIndex < bottomCount) 0 until bottomCount else bottomCount until orderedDestinations.size
        val targetIndex = (sourceIndex + offset).coerceIn(zone.first, zone.last)
        if (sourceIndex == targetIndex) {
            return this
        }
        val reordered = orderedDestinations.toMutableList().apply {
            removeAt(sourceIndex)
            add(targetIndex, destination)
        }
        return copy(orderedDestinations = reordered)
    }
}

internal val DefaultDestinationOrder = listOf(
    MusicDestination.Playlist,
    MusicDestination.Artist,
    MusicDestination.Album,
    MusicDestination.Songs,
    MusicDestination.Genre,
    MusicDestination.LovedSongs,
    MusicDestination.Folder,
)

internal const val MinBottomDestinationCount = 2
internal const val MaxBottomDestinationCount = 4
internal const val DefaultBottomDestinationCount = 4

internal fun normalizedNavigationLayout(
    routes: Iterable<String>,
    bottomCount: Int,
): NavigationLayout {
    val ordered = buildList {
        routes.forEach { route ->
            val destination = MusicDestination.fromRoute(route)
            if (destination?.movable == true && destination !in this) {
                add(destination)
            }
        }
        DefaultDestinationOrder.forEach { destination ->
            if (destination !in this) {
                add(destination)
            }
        }
    }
    return NavigationLayout(
        orderedDestinations = ordered,
        bottomCount = bottomCount.coerceIn(MinBottomDestinationCount, MaxBottomDestinationCount),
    )
}

/** 将旧版“隐藏底栏项”设置迁移为“底栏前缀 + 更多后缀”。 */
internal fun navigationLayoutFromLegacyHiddenTabs(hiddenRoutes: Set<String>): NavigationLayout {
    val defaultPinned = DefaultDestinationOrder.take(DefaultBottomDestinationCount)
    val requestedPinned = defaultPinned.filter { it.route !in hiddenRoutes }.toMutableList()
    defaultPinned.forEach { destination ->
        if (requestedPinned.size < MinBottomDestinationCount && destination !in requestedPinned) {
            requestedPinned += destination
        }
    }
    val overflow = DefaultDestinationOrder.filter { it !in requestedPinned }
    return NavigationLayout(
        orderedDestinations = requestedPinned + overflow,
        bottomCount = requestedPinned.size,
    )
}
