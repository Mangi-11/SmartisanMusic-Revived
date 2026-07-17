package com.smartisan.music.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.smartisan.music.R
import com.smartisan.music.ui.widgets.legacy.MenuDialogTitleBar

internal data class LegacyTrackActionItem(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:DrawableRes val pressedIconRes: Int = iconRes,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
internal fun LegacyTrackActionsOverlay(
    visible: Boolean,
    actions: List<LegacyTrackActionItem>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renderOverlay by remember { mutableStateOf(visible) }
    var renderedActions by remember { mutableStateOf(actions) }
    LaunchedEffect(visible) {
        if (visible) {
            renderOverlay = true
        }
    }
    LaunchedEffect(visible, actions) {
        if (visible) {
            renderedActions = actions
        }
    }
    if (!renderOverlay) {
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LegacyTrackActionsView(context).apply {
                onHidden = { renderOverlay = false }
            }
        },
        update = { view ->
            view.onHidden = { renderOverlay = false }
            view.bind(
                actions = if (visible) actions else renderedActions,
                onDismissRequest = onDismissRequest,
            )
            if (visible) {
                view.showPanel()
            } else {
                view.dismissPanel()
            }
        },
    )
}

private class LegacyTrackActionsView(
    context: Context,
) : FrameLayout(context) {
    var onHidden: () -> Unit = {}

    private val interpolator = DecelerateInterpolator()
    private val itemAdapter = TrackActionAdapter(context)
    private val parentPanel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
    }
    private val titleBar = MenuDialogTitleBar(context).apply {
        id = R.id.menu_dialog_title_bar
        forceRequestAccessibilityFocusWhenAttached(false)
        setTitle(R.string.select_action)
        setLeftButtonVisibility(View.INVISIBLE)
        setRightButtonVisibility(View.VISIBLE)
    }
    private val gridView = GridView(context).apply {
        id = R.id.gridview
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        selector = ColorDrawable(Color.TRANSPARENT)
        cacheColorHint = Color.TRANSPARENT
        isVerticalScrollBarEnabled = false
        horizontalSpacing = 0
        verticalSpacing = 0
        adapter = itemAdapter
    }
    private val bottomLine = View(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.bottom_line))
    }
    private var shown = false
    private var dismissing = false
    private var dismissRequest: () -> Unit = {}

    init {
        id = R.id.container
        setBackgroundResource(R.color.transparent_black)
        alpha = 0f
        visibility = GONE
        isClickable = true
        setOnClickListener {
            dismissRequest()
        }
        titleBar.setOnRightButtonClickListener {
            dismissRequest()
        }
        gridView.setOnItemClickListener { _, _, position, _ ->
            val item = itemAdapter.getItem(position)
            if (item.enabled) {
                item.onClick()
            }
        }
        addView(
            parentPanel,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        parentPanel.addView(
            titleBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.titlebar_height),
            ),
        )
        parentPanel.addView(
            gridView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.dpInt(TrackActionItemHeightDp),
            ),
        )
        parentPanel.addView(
            bottomLine,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1,
            ),
        )
    }

    fun bind(
        actions: List<LegacyTrackActionItem>,
        onDismissRequest: () -> Unit,
    ) {
        dismissRequest = onDismissRequest
        itemAdapter.setItems(actions)
        val columnCount = actions.size.coerceIn(1, TrackActionColumnCount)
        gridView.numColumns = columnCount
        val rows = (actions.size + columnCount - 1) / columnCount
        gridView.layoutParams = gridView.layoutParams.apply {
            height = context.dpInt(TrackActionItemHeightDp * rows)
        }
    }

    fun showPanel() {
        if (shown && !dismissing) {
            return
        }
        cancelAnimations()
        shown = true
        dismissing = false
        visibility = VISIBLE
        isClickable = true
        post {
            val panelHeight = parentPanel.measuredPanelHeight()
            parentPanel.translationY = panelHeight.toFloat()
            parentPanel.alpha = PanelHiddenAlpha
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .start()
            parentPanel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .start()
        }
    }

    fun dismissPanel() {
        if (!shown && !dismissing) {
            visibility = GONE
            onHidden()
            return
        }
        if (dismissing) {
            return
        }
        dismissing = true
        cancelAnimations()
        post {
            val panelHeight = parentPanel.measuredPanelHeight()
            animate()
                .alpha(0f)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .start()
            parentPanel.animate()
                .translationY(panelHeight.toFloat())
                .alpha(PanelHiddenAlpha)
                .setDuration(PopupAnimationDurationMs)
                .setInterpolator(interpolator)
                .withEndAction {
                    shown = false
                    dismissing = false
                    visibility = GONE
                    onHidden()
                }
                .start()
        }
    }

    private fun cancelAnimations() {
        animate().withEndAction(null).cancel()
        parentPanel.animate().withEndAction(null).cancel()
    }
}

private class TrackActionAdapter(
    private val context: Context,
) : BaseAdapter() {
    private var items: List<LegacyTrackActionItem> = emptyList()

    fun setItems(items: List<LegacyTrackActionItem>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): LegacyTrackActionItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun isEnabled(position: Int): Boolean = items[position].enabled

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val holder: TrackActionViewHolder
        val root = if (convertView == null) {
            val createdRoot = RelativeLayout(context).apply {
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.menu_item_selector)
                layoutParams = AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    context.dpInt(TrackActionItemHeightDp),
                )
            }
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val icon = ImageView(context).apply {
                id = R.id.menu_icon
                isDuplicateParentStateEnabled = true
                contentDescription = null
            }
            val text = TextView(context).apply {
                id = R.id.menu_text
                gravity = Gravity.CENTER_VERTICAL
                setSingleLine(true)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(context, R.color.add_nav_text_color))
                setPadding(context.dpInt(1f), 0, context.dpInt(1f), 0)
            }
            content.addView(
                icon,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                },
            )
            content.addView(
                text,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = context.dpInt(1f)
                    leftMargin = context.dpInt(6f)
                    rightMargin = context.dpInt(6f)
                },
            )
            createdRoot.addView(
                content,
                RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    addRule(RelativeLayout.CENTER_IN_PARENT)
                },
            )
            holder = TrackActionViewHolder(icon, text)
            createdRoot.tag = holder
            createdRoot
        } else {
            holder = convertView.tag as TrackActionViewHolder
            convertView as RelativeLayout
        }
        val item = items[position]
        root.isEnabled = item.enabled
        root.alpha = if (item.enabled) 1f else DisabledAlpha
        holder.icon.isEnabled = item.enabled
        holder.text.isEnabled = item.enabled
        holder.icon.setImageDrawable(trackActionIconDrawable(context, item.iconRes, item.pressedIconRes))
        holder.text.setText(item.labelRes)
        holder.text.setTextColor(
            ContextCompat.getColor(
                context,
                if (item.selected) R.color.btn_text_color_blue else R.color.add_nav_text_color,
            ),
        )
        root.contentDescription = context.getString(item.labelRes)
        root.setOnClickListener {
            if (item.enabled) {
                item.onClick()
            }
        }
        return root
    }
}

private data class TrackActionViewHolder(
    val icon: ImageView,
    val text: TextView,
)

private fun trackActionIconDrawable(
    context: Context,
    @DrawableRes normalRes: Int,
    @DrawableRes pressedRes: Int,
): Drawable? {
    val normal = ContextCompat.getDrawable(context, normalRes) ?: return null
    if (normalRes == pressedRes) {
        return normal
    }
    return StateListDrawable().apply {
        ContextCompat.getDrawable(context, pressedRes)?.let { pressed ->
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_selected), pressed)
        }
        addState(intArrayOf(), normal)
    }
}

private fun View.measuredPanelHeight(): Int {
    return height.takeIf { it > 0 } ?: measuredHeight.takeIf { it > 0 } ?: 0
}

private fun Context.dpInt(value: Float): Int {
    return (value * resources.displayMetrics.density + 0.5f).toInt()
}

private const val TrackActionColumnCount = 4
private const val TrackActionItemHeightDp = 72f
private const val PopupAnimationDurationMs = 300L
private const val PanelHiddenAlpha = 0.92f
private const val DisabledAlpha = 0.35f
