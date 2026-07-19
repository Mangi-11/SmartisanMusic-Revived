package com.smartisan.music.ui.shell.tabs

import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.AttributeSet
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import com.smartisan.music.R
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.navigation.NavigationLayout
import com.smartisan.music.ui.widgets.legacy.BottomTabItemView
import com.smartisan.music.ui.widgets.legacy.MenuDialogTitleBar

@Composable
internal fun LegacyNavigationEditorOverlay(
    visible: Boolean,
    layout: NavigationLayout,
    selectedDestination: MusicDestination,
    onDismissRequest: () -> Unit,
    onCommit: (NavigationLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renderOverlay by remember { mutableStateOf(visible) }
    val latestDismissRequest by rememberUpdatedState(onDismissRequest)
    val latestCommit by rememberUpdatedState(onCommit)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
    val navigationBarStart = WindowInsets.navigationBars.getLeft(density, layoutDirection)
    val navigationBarEnd = WindowInsets.navigationBars.getRight(density, layoutDirection)

    LaunchedEffect(visible) {
        if (visible) {
            renderOverlay = true
        }
    }
    if (!renderOverlay) {
        return
    }

    BackHandler(enabled = visible) {
        latestDismissRequest()
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            LegacyNavigationEditorView(context).apply {
                onHidden = { renderOverlay = false }
            }
        },
        update = { view ->
            view.onHidden = { renderOverlay = false }
            view.setNavigationBarInsets(
                start = navigationBarStart,
                end = navigationBarEnd,
                bottom = navigationBarBottom,
            )
            view.bindCallbacks(
                onCancel = latestDismissRequest,
                onCommit = latestCommit,
            )
            if (visible) {
                view.show(layout, selectedDestination)
            } else {
                view.dismiss()
            }
        },
    )
}

private class LegacyNavigationEditorView(context: Context) : FrameLayout(context) {
    var onHidden: () -> Unit = {}

    private val titleBar = MenuDialogTitleBar(context).apply {
        setTitle(R.string.navigation_editor_title)
        setLeftButtonVisibility(View.VISIBLE)
        setRightButtonVisibility(View.VISIBLE)
        setLeftImageViewRes(R.drawable.standard_icon_cancel_selector)
        setRightImageRes(R.drawable.standard_icon_complete_selector)
        getLeftImageView().contentDescription = context.getString(R.string.cancel)
        getRightImageView().contentDescription = context.getString(R.string.done)
    }
    private val overflowZone = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.START
        weightSum = EditorSlotCount.toFloat()
        setBackgroundColor(context.getColor(R.color.tab_bar_top_background))
        foreground = context.getDrawable(R.drawable.title_bar_shadow)
        foregroundGravity = Gravity.TOP or Gravity.FILL_HORIZONTAL
    }
    private val divider = View(context).apply {
        setBackgroundColor(context.getColor(R.color.nav_list_line))
    }
    private val bottomZone = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.sb_repeat_tabbar_bg)
    }
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        addView(
            titleBar,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        addView(
            overflowZone,
            LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.navigation_editor_overflow_height),
            ),
        )
        addView(
            divider,
            LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.nav_divider_height),
            ),
        )
        addView(
            bottomZone,
            LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.smartisan_tabswitch_tabbar_height),
            ),
        )
    }
    private val accessibilityAnnouncement = TextView(context).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        isFocusable = false
        setTextColor(Color.TRANSPARENT)
    }

    private var draftLayout = NavigationLayout()
    private var selectedDestination = MusicDestination.Playlist
    private var shown = false
    private var dismissing = false
    private var cancelRequest: () -> Unit = {}
    private var commitRequest: (NavigationLayout) -> Unit = {}
    private var startInset = 0
    private var endInset = 0
    private var bottomInset = 0
    private val itemViews = mutableMapOf<MusicDestination, NavigationEditorItemView>()

    init {
        setBackgroundColor(context.getColor(R.color.transparent_black))
        alpha = 0f
        visibility = View.GONE
        isClickable = true
        setOnClickListener { cancelRequest() }
        titleBar.setOnLeftButtonClickListener { cancelRequest() }
        titleBar.setOnRightButtonClickListener { commitRequest(draftLayout) }
        addView(
            panel,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
        )
        addView(accessibilityAnnouncement, LayoutParams(1, 1, Gravity.TOP or Gravity.START))
    }

    fun bindCallbacks(
        onCancel: () -> Unit,
        onCommit: (NavigationLayout) -> Unit,
    ) {
        cancelRequest = onCancel
        commitRequest = onCommit
    }

    fun setNavigationBarInsets(start: Int, end: Int, bottom: Int) {
        if (startInset == start && endInset == end && bottomInset == bottom) {
            return
        }
        startInset = start
        endInset = end
        bottomInset = bottom
        overflowZone.setPadding(start, 0, end, 0)
        bottomZone.setPadding(start, 0, end, bottom)
        bottomZone.layoutParams = (bottomZone.layoutParams as LinearLayout.LayoutParams).apply {
            height = resources.getDimensionPixelSize(R.dimen.smartisan_tabswitch_tabbar_height) + bottom
        }
    }

    fun show(layout: NavigationLayout, selectedDestination: MusicDestination) {
        if (shown || dismissing) {
            return
        }
        shown = true
        this.draftLayout = layout
        this.selectedDestination = selectedDestination
        renderZones()
        visibility = View.VISIBLE
        alpha = 0f
        panel.post {
            panel.translationY = panel.height.toFloat()
            animate().alpha(1f).setDuration(EditorShowDurationMs).start()
            panel.animate()
                .translationY(0f)
                .setDuration(EditorShowDurationMs)
                .start()
        }
    }

    fun dismiss() {
        if (!shown || dismissing) {
            return
        }
        shown = false
        dismissing = true
        panel.animate()
            .translationY(panel.height.toFloat())
            .setDuration(EditorHideDurationMs)
            .start()
        animate()
            .alpha(0f)
            .setDuration(EditorHideDurationMs)
            .withEndAction {
                visibility = View.GONE
                dismissing = false
                onHidden()
            }
            .start()
    }

    private fun renderZones() {
        overflowZone.removeAllViews()
        bottomZone.removeAllViews()

        draftLayout.overflowDestinations.forEach { destination ->
            overflowZone.addView(
                createItem(destination, inOverflow = true),
                LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f),
            )
        }

        val bottomDestinations = draftLayout.bottomDestinations
        bottomZone.weightSum = bottomDestinations.size.toFloat()
        bottomDestinations.forEach { destination ->
            bottomZone.addView(
                createItem(destination, inOverflow = false),
                LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f),
            )
        }
    }

    private fun createItem(
        destination: MusicDestination,
        inOverflow: Boolean,
    ): NavigationEditorItemView {
        val orderedIndex = draftLayout.orderedDestinations.indexOf(destination)
        val zoneStart = if (inOverflow) draftLayout.bottomCount else 0
        val zoneEnd = if (inOverflow) {
            draftLayout.orderedDestinations.lastIndex
        } else {
            draftLayout.bottomCount - 1
        }
        val checkedDestination = selectedDestination.takeIf(draftLayout::isPinned)
            ?: MusicDestination.More
        return itemViews.getOrPut(destination) {
            NavigationEditorItemView(context)
        }.also { item ->
            item.bind(
                destination = destination,
                inOverflow = inOverflow,
                checked = !inOverflow && destination == checkedDestination,
                canMoveEarlier = destination.movable && orderedIndex > zoneStart,
                canMoveLater = destination.movable && orderedIndex in 0 until zoneEnd,
                onStartDrag = ::startDrag,
                onMove = { offset -> moveForAccessibility(destination, offset) },
                onSwapZone = { swapZoneForAccessibility(destination) },
            )
            item.setOnDragListener(null)
            if (destination.movable) {
                item.setOnDragListener { _, event -> handleDrag(destination, event) }
            }
        }
    }

    private fun startDrag(view: View, destination: MusicDestination): Boolean {
        if (!destination.movable) {
            return false
        }
        return view.startDragAndDrop(
            ClipData.newPlainText(NavigationDragMimeLabel, destination.route),
            DragShadowBuilder(view),
            destination,
            0,
        )
    }

    private fun handleDrag(target: MusicDestination, event: DragEvent): Boolean {
        val source = event.localState as? MusicDestination ?: return false
        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> source.movable
            DragEvent.ACTION_DROP -> {
                if (source != target) {
                    draftLayout = draftLayout.swap(source, target)
                    announceSwap(source, target)
                    post(::renderZones)
                }
                true
            }
            else -> true
        }
    }

    private fun moveForAccessibility(destination: MusicDestination, offset: Int) {
        val nextLayout = draftLayout.move(destination, offset)
        if (nextLayout == draftLayout) {
            return
        }
        draftLayout = nextLayout
        sendAccessibilityAnnouncement(
            context.getString(R.string.navigation_editor_item_moved, context.getString(destination.labelRes)),
        )
        renderZones()
    }

    private fun swapZoneForAccessibility(destination: MusicDestination) {
        val target = if (draftLayout.isPinned(destination)) {
            draftLayout.overflowDestinations.firstOrNull()
        } else {
            draftLayout.bottomDestinations.dropLast(1).lastOrNull()
        } ?: return
        draftLayout = draftLayout.swap(destination, target)
        announceSwap(destination, target)
        renderZones()
    }

    private fun announceSwap(first: MusicDestination, second: MusicDestination) {
        sendAccessibilityAnnouncement(
            context.getString(
                R.string.navigation_editor_items_swapped,
                context.getString(first.labelRes),
                context.getString(second.labelRes),
            ),
        )
    }

    private fun sendAccessibilityAnnouncement(message: CharSequence) {
        accessibilityAnnouncement.text = ""
        accessibilityAnnouncement.post {
            accessibilityAnnouncement.text = message
        }
    }
}

private class NavigationEditorItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val tabItem = BottomTabItemView(context).apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        setDuplicateParentStateEnabled(true)
    }
    private lateinit var destination: MusicDestination
    private var inOverflow = false
    private var canMoveEarlier = false
    private var canMoveLater = false
    private var startDrag: (View, MusicDestination) -> Boolean = { _, _ -> false }
    private var move: (Int) -> Unit = {}
    private var swapZone: () -> Unit = {}

    init {
        isFocusable = true
        addView(tabItem, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(
        destination: MusicDestination,
        inOverflow: Boolean,
        checked: Boolean,
        canMoveEarlier: Boolean,
        canMoveLater: Boolean,
        onStartDrag: (View, MusicDestination) -> Boolean,
        onMove: (Int) -> Unit,
        onSwapZone: () -> Unit,
    ) {
        this.destination = destination
        this.inOverflow = inOverflow
        this.canMoveEarlier = canMoveEarlier
        this.canMoveLater = canMoveLater
        startDrag = onStartDrag
        move = onMove
        swapZone = onSwapZone
        val label = context.getString(destination.labelRes)
        tabItem.text = label
        tabItem.setDrawableResource(
            if (inOverflow) destination.overflowIconRes else destination.bottomIconRes,
        )
        tabItem.setTextColor(context.getColorStateList(R.color.tab_bar_text_color))
        tabItem.setChecked(checked, animate = false)
        tabItem.setPadding(
            0,
            if (inOverflow) {
                resources.getDimensionPixelSize(R.dimen.smartisan_switch_bar_top_rg_padding)
            } else {
                0
            },
            0,
            0,
        )
        tabItem.background = null
        contentDescription = context.getString(
            if (inOverflow) R.string.navigation_editor_overflow_item_description
            else R.string.navigation_editor_bottom_item_description,
            label,
        )
        setOnLongClickListener {
            startDrag(this, destination)
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isLongClickable = false
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK)
        if (canMoveEarlier) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    R.id.navigation_action_move_earlier,
                    context.getString(R.string.navigation_editor_move_earlier),
                ),
            )
        }
        if (canMoveLater) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    R.id.navigation_action_move_later,
                    context.getString(R.string.navigation_editor_move_later),
                ),
            )
        }
        if (destination.movable) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    R.id.navigation_action_swap_zone,
                    context.getString(
                        if (inOverflow) R.string.navigation_editor_pin_replacing
                        else R.string.navigation_editor_unpin_replacing,
                    ),
                ),
            )
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        return when (action) {
            R.id.navigation_action_move_earlier -> {
                move(-1)
                true
            }
            R.id.navigation_action_move_later -> {
                move(1)
                true
            }
            R.id.navigation_action_swap_zone -> {
                swapZone()
                true
            }
            else -> super.performAccessibilityAction(action, arguments)
        }
    }
}

private const val EditorSlotCount = 5
private const val EditorShowDurationMs = 220L
private const val EditorHideDurationMs = 180L
private const val NavigationDragMimeLabel = "smartisan-navigation-destination"
