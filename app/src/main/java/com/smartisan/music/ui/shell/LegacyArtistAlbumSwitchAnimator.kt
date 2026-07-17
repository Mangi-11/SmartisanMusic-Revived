package com.smartisan.music.ui.shell

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.LayoutAnimationController
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ListView
import com.smartisan.music.R
import com.smartisan.music.ui.album.AlbumViewMode

internal class LegacyArtistAlbumViewSwitchAnimator {
    private var animator: Animator? = null
    private val interpolator = DecelerateInterpolator()
    private var generation: Int = 0

    fun animate(
        root: LegacyArtistAlbumsRoot,
        from: AlbumViewMode?,
        to: AlbumViewMode,
    ) {
        generation += 1
        val currentGen = generation
        animator?.cancel()
        animator = null
        if (from == AlbumViewMode.List && to == AlbumViewMode.Tile) {
            animateListToGrid(root, currentGen)
        } else if (from == AlbumViewMode.Tile && to == AlbumViewMode.List) {
            animateGridToList(root, currentGen)
        } else {
            root.showModeImmediately(to)
        }
    }

    private fun animateListToGrid(root: LegacyArtistAlbumsRoot, gen: Int) {
        val listHost = root.listHost
        val listView = root.listView
        val gridView = root.gridView
        val firstVisiblePosition = listView.firstVisiblePosition
        listHost.animate().cancel()
        listHost.visibility = View.GONE
        listHost.alpha = 1f
        gridView.alpha = 1f
        gridView.layoutAnimation = legacyAlbumGridFadeLayoutAnimation()
        gridView.setSelection(firstVisiblePosition)
        gridView.visibility = View.VISIBLE
        gridView.post {
            if (gen != generation) return@post
            resetGridChildren(gridView)
            gridView.scheduleLayoutAnimation()
        }
    }

    private fun legacyAlbumGridFadeLayoutAnimation(): LayoutAnimationController {
        val animation = AnimationSet(true).apply {
            addAnimation(
                AlphaAnimation(0f, 1f).apply {
                    duration = 300L
                },
            )
        }
        return LayoutAnimationController(animation, 0.133f).apply {
            order = LayoutAnimationController.ORDER_NORMAL
        }
    }

    private fun animateGridToList(root: LegacyArtistAlbumsRoot, gen: Int) {
        val listHost = root.listHost
        val listView = root.listView
        val gridView = root.gridView
        val firstVisiblePosition = gridView.firstVisiblePosition
        listHost.animate().cancel()
        listHost.alpha = 0f
        listHost.visibility = View.VISIBLE
        listView.alpha = 1f
        listView.visibility = View.VISIBLE
        afterNextLayout(listView, gen) {
            if (gen != generation) return@afterNextLayout
            val animators = mutableListOf<Animator>()
            val hiddenListTargets = mutableSetOf<View>()
            val gridFirstVisiblePosition = gridView.firstVisiblePosition
            val listFirstPosition = listView.firstVisiblePosition
            val listLastPosition = listView.lastVisiblePosition
            for (index in 0 until gridView.childCount) {
                val gridChild = gridView.getChildAt(index) ?: continue
                val gridCover = gridChild.findViewById<View>(R.id.gridview_image) ?: continue
                val position = gridCover.getTag(R.string.add_track) as? Int ?: continue
                val animationOrder = artistGridAnimationOrder(
                    position = position,
                    firstVisiblePosition = gridFirstVisiblePosition,
                    fallbackIndex = index,
                )
                val listCover = listView.findArtistCoverByPosition(position)
                gridChild.prepareForArtistAlbumSwitch()
                val target = gridChild.artistGridToListTarget(
                    listView = listView,
                    listCover = listCover,
                    gridView = gridView,
                    gridCover = gridCover,
                    targetPosition = position,
                    listFirstPosition = listFirstPosition,
                    listLastPosition = listLastPosition,
                )
                animators += ObjectAnimator.ofPropertyValuesHolder(
                    gridChild,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, target.translationX),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, target.translationY),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, target.scaleX),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, target.scaleY),
                ).apply {
                    duration = ArtistAlbumSwitchBaseDurationMillis
                    startDelay = animationOrder * ArtistAlbumSwitchStaggerMillis
                    interpolator = this@LegacyArtistAlbumViewSwitchAnimator.interpolator
                    addListener(
                        object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                listCover?.artistParentView()?.let { listTarget ->
                                    listTarget.visibility = View.INVISIBLE
                                    hiddenListTargets += listTarget
                                }
                            }

                            override fun onAnimationEnd(animation: Animator) {
                                listCover?.artistParentView()?.visibility = View.VISIBLE
                            }

                            override fun onAnimationCancel(animation: Animator) {
                                listCover?.artistParentView()?.visibility = View.VISIBLE
                            }
                        },
                    )
                }
            }
            if (animators.isEmpty()) {
                listHost.alpha = 1f
                gridView.visibility = View.GONE
                resetGridChildren(gridView)
                return@afterNextLayout
            }
            val nextAnimator = AnimatorSet().apply {
                playTogether(animators)
                addListener(
                    object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            finishGridToList(listHost, gridView, hiddenListTargets)
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            finishGridToList(listHost, gridView, hiddenListTargets)
                        }
                    },
                )
            }
            animator = nextAnimator
            nextAnimator.start()
        }
        listView.setSelectionFromTop(firstVisiblePosition, 0)
        listView.requestLayout()
    }

    private fun afterNextLayout(
        view: View,
        gen: Int,
        block: () -> Unit,
    ) {
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (view.viewTreeObserver.isAlive) {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
                if (gen == generation) {
                    block()
                }
            }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun finishGridToList(
        listHost: View,
        gridView: GridView,
        hiddenListTargets: Set<View>,
    ) {
        hiddenListTargets.forEach { target ->
            target.visibility = View.VISIBLE
        }
        listHost.animate().cancel()
        listHost.alpha = 1f
        gridView.visibility = View.GONE
        resetGridChildren(gridView)
    }

    private fun resetGridChildren(gridView: GridView) {
        for (index in 0 until gridView.childCount) {
            gridView.getChildAt(index)?.apply {
                translationX = 0f
                translationY = 0f
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
                setLayerType(View.LAYER_TYPE_NONE, null)
                findViewById<View>(R.id.tv_album_name)?.visibility = View.VISIBLE
            }
        }
    }
}

private data class ArtistAlbumSwitchTarget(
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float,
)

private fun artistGridAnimationOrder(
    position: Int,
    firstVisiblePosition: Int,
    fallbackIndex: Int,
): Long {
    val order = position - firstVisiblePosition
    return if (order >= 0) order.toLong() else fallbackIndex.toLong()
}

private fun ListView.findArtistCoverByPosition(position: Int): View? {
    for (index in 0 until childCount) {
        val child = getChildAt(index) ?: continue
        val cover = child.findViewById<View>(R.id.listview_item_image) ?: continue
        if (cover.getTag(R.string.add_track) == position) {
            return cover
        }
    }
    return null
}

private fun View.prepareForArtistAlbumSwitch() {
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
    pivotX = 0f
    pivotY = 0f
    findViewById<View>(R.id.tv_album_name)?.visibility = View.INVISIBLE
}

private fun View.artistGridToListTarget(
    listView: ListView,
    listCover: View?,
    gridView: GridView,
    gridCover: View,
    targetPosition: Int,
    listFirstPosition: Int,
    listLastPosition: Int,
): ArtistAlbumSwitchTarget {
    val gridInnerWidth = (gridCover.width - gridCover.paddingTop * 2).coerceAtLeast(1)
    val gridInnerHeight = (gridCover.height - gridCover.paddingTop * 2).coerceAtLeast(1)
    val sampleListCover = listCover ?: listView.getChildAt(0)?.findViewById<View>(R.id.listview_item_image)
    val scaleX = (sampleListCover?.width ?: gridInnerWidth).toFloat() / gridInnerWidth
    val scaleY = (sampleListCover?.height ?: gridInnerHeight).toFloat() / gridInnerHeight
    if (listCover != null && targetPosition in listFirstPosition..listLastPosition) {
        val gridChildX = leftRelativeToArtist(gridView)
        val gridChildY = topRelativeToArtist(gridView)
        val gridCoverX = gridCover.leftRelativeToArtist(gridView) + gridCover.paddingTop
        val gridCoverY = gridCover.topRelativeToArtist(gridView) + gridCover.paddingTop
        val listCoverX = listCover.leftRelativeToArtist(listView)
        val listCoverY = listCover.topRelativeToArtist(listView)
        return ArtistAlbumSwitchTarget(
            translationX = listCoverX - (gridChildX + (gridCoverX - gridChildX) * scaleX),
            translationY = listCoverY - (gridChildY + (gridCoverY - gridChildY) * scaleY),
            scaleX = scaleX,
            scaleY = scaleY,
        )
    }
    return ArtistAlbumSwitchTarget(
        translationX = -left.toFloat(),
        translationY = when {
            targetPosition > listLastPosition -> listView.lastArtistChildTop().toFloat()
            targetPosition < listFirstPosition -> -listView.lastArtistChildTop() / 2f
            else -> -top.toFloat()
        },
        scaleX = scaleX,
        scaleY = scaleY,
    )
}

private fun ListView.lastArtistChildTop(): Int {
    return getChildAt(childCount - 1)?.top ?: 0
}

private fun View.artistParentView(): View? = parent as? View

private fun View.leftRelativeToArtist(ancestor: View): Float {
    var result = left.toFloat()
    var current = parent as? View
    while (current != null && current !== ancestor) {
        result += current.left - current.scrollX
        current = current.parent as? View
    }
    return result
}

private fun View.topRelativeToArtist(ancestor: View): Float {
    var result = top.toFloat()
    var current = parent as? View
    while (current != null && current !== ancestor) {
        result += current.top - current.scrollY
        current = current.parent as? View
    }
    return result
}
