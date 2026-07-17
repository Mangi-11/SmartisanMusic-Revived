package com.smartisan.music.ui.shell

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.smartisan.music.R
import com.smartisan.music.ui.album.AlbumSummary
import kotlin.math.min
import kotlin.math.roundToInt

internal data class LegacyAlbumArtworkBrowserState(
    val album: AlbumSummary,
    val sourceView: View?,
)

@Composable
internal fun LegacyAlbumArtworkBrowserOverlay(
    state: LegacyAlbumArtworkBrowserState?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renderedState by remember { mutableStateOf<LegacyAlbumArtworkBrowserState?>(null) }
    LaunchedEffect(state) {
        if (state != null) renderedState = state
    }

    val activeState = state ?: renderedState ?: return
    BackHandler { onDismissRequest() }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        DisableDialogDim()
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context -> ArtworkBrowserView(context) },
            update = { view ->
                view.onHidden = { renderedState = null }
                view.bind(activeState, onDismissRequest)
                if (state == null) view.dismiss() else view.show()
            },
        )
    }
}

@Composable
private fun DisableDialogDim() {
    val view = LocalView.current
    SideEffect {
        val window = (view as? DialogWindowProvider)?.window
            ?: (view.parent as? DialogWindowProvider)?.window
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(0f)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}

private class ArtworkBrowserView(context: Context) : FrameLayout(context) {
    var onHidden: () -> Unit = {}

    private val loader = LegacyAlbumArtworkLoader(context)
    private val interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    private val scrim = View(context).apply {
        setBackgroundColor(Color.BLACK)
        alpha = 0f
        isClickable = true
        setOnClickListener { dismissRequest() }
    }
    private val artworkFrame = FrameLayout(context).apply {
        isClickable = true
        clipChildren = false
        clipToPadding = false
    }
    private val artworkImage = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = false
        setImageResource(R.drawable.noalbumcover_220)
    }
    private val startBounds = RectF()
    private val endBounds = RectF()
    private val sourceRect = Rect()
    private val rootLocation = IntArray(2)

    private var state: LegacyAlbumArtworkBrowserState? = null
    private var dismissRequest: () -> Unit = {}
    private var animator: ValueAnimator? = null
    private var startScale = 1f
    private var progress = 0f
    private var artworkKey: String? = null
    private var hiddenSource: View? = null
    private var hiddenSourceAlpha = 1f
    private var runToken = 0
    private var shown = false
    private var dismissing = false

    init {
        visibility = GONE
        clipChildren = false
        clipToPadding = false
        isClickable = true
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(artworkFrame, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        artworkFrame.addView(
            artworkImage,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun bind(nextState: LegacyAlbumArtworkBrowserState, onDismissRequest: () -> Unit) {
        if (state?.album?.id != nextState.album.id) artworkKey = null
        state = nextState
        dismissRequest = onDismissRequest
        bindArtwork()
    }

    fun show() {
        if (shown && !dismissing) return
        cancelAnimation()
        shown = true
        dismissing = false
        progress = 0f
        visibility = VISIBLE
        scrim.alpha = 0f
        artworkFrame.alpha = 1f
        artworkFrame.visibility = INVISIBLE
        val token = nextRunToken()
        post {
            if (token != runToken) return@post
            bindArtwork()
            updateBounds()
            hideSource()
            animate(0f, 1f, BrowserOpenDurationMs) { animator = null }
        }
    }

    fun dismiss() {
        if (!shown && !dismissing) {
            hideNow()
            return
        }
        if (dismissing) return
        cancelAnimation()
        dismissing = true
        val token = nextRunToken()
        post {
            if (token != runToken) return@post
            val from = progress.coerceIn(0f, 1f)
            if (from <= 0f) hideNow() else animate(from, 0f, BrowserCloseDurationMs) { hideNow() }
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        bindArtwork()
        if (shown && width > 0 && height > 0) {
            updateBounds()
            applyProgress(progress)
        }
    }

    override fun onDetachedFromWindow() {
        cancelAnimation()
        restoreSource()
        loader.clear()
        super.onDetachedFromWindow()
    }

    private fun animate(from: Float, to: Float, durationMs: Long, onEnd: () -> Unit) {
        artworkFrame.visibility = VISIBLE
        artworkFrame.pivotX = 0f
        artworkFrame.pivotY = 0f
        applyProgress(from)

        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = durationMs
            interpolator = this@ArtworkBrowserView.interpolator
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                    onEnd()
                }

                override fun onAnimationCancel(animation: Animator) {
                    animator = null
                }
            })
            start()
        }
    }

    private fun applyProgress(value: Float) {
        progress = value
        val center = curvedCenter(value)
        val scale = lerp(startScale, 1f, value)
        scrim.alpha = value
        artworkFrame.alpha = 1f
        artworkFrame.x = center.x - endBounds.width() * scale / 2f
        artworkFrame.y = center.y - endBounds.height() * scale / 2f
        artworkFrame.scaleX = scale
        artworkFrame.scaleY = scale
    }

    private fun updateBounds() {
        val size = min(width, height)
        if (size <= 0) return

        val left = ((width - size) / 2f).roundToInt()
        val top = ((height - size) / 2f).roundToInt()
        val exactSize = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        artworkFrame.measure(exactSize, exactSize)
        artworkFrame.layout(left, top, left + size, top + size)
        artworkFrame.layoutParams = (artworkFrame.layoutParams as LayoutParams).apply {
            width = size
            height = size
            gravity = Gravity.CENTER
        }

        endBounds.set(left.toFloat(), top.toFloat(), (left + size).toFloat(), (top + size).toFloat())
        if (!readSourceBounds()) {
            startBounds.set(endBounds)
            startBounds.inset(endBounds.width() * BrowserFallbackInsetRatio, endBounds.height() * BrowserFallbackInsetRatio)
        }
        startScale = alignStartToEndAspectRatio()
    }

    private fun readSourceBounds(): Boolean {
        val source = state?.sourceView ?: return false
        if (!source.getGlobalVisibleRect(sourceRect) || sourceRect.width() <= 0 || sourceRect.height() <= 0) {
            return false
        }
        getLocationOnScreen(rootLocation)
        startBounds.set(sourceRect)
        startBounds.offset(-rootLocation[0].toFloat(), -rootLocation[1].toFloat())
        return true
    }

    private fun alignStartToEndAspectRatio(): Float {
        if (startBounds.width() <= 0f || startBounds.height() <= 0f) return 1f
        return if (endBounds.width() / endBounds.height() > startBounds.width() / startBounds.height()) {
            val scale = startBounds.height() / endBounds.height()
            val delta = (scale * endBounds.width() - startBounds.width()) / 2f
            startBounds.left -= delta
            startBounds.right += delta
            scale
        } else {
            val scale = startBounds.width() / endBounds.width()
            val delta = (scale * endBounds.height() - startBounds.height()) / 2f
            startBounds.top -= delta
            startBounds.bottom += delta
            scale
        }
    }

    private fun curvedCenter(t: Float): PointF {
        val startX = startBounds.centerX()
        val startY = startBounds.centerY()
        val endX = endBounds.centerX()
        val endY = endBounds.centerY()
        val controlX = (startX + endX) / 2f - (endY - startY) * BrowserArcBendRatio
        val controlY = (startY + endY) / 2f + (endX - startX) * BrowserArcBendRatio
        val r = 1f - t
        return PointF(
            r * r * startX + 2f * r * t * controlX + t * t * endX,
            r * r * startY + 2f * r * t * controlY + t * t * endY,
        )
    }

    private fun bindArtwork() {
        val currentState = state ?: return
        val size = min(width, height).coerceAtLeast(0)
        if (size <= 0) return

        val key = "${currentState.album.id}@$size"
        if (artworkKey == key) return
        artworkKey = key
        loader.bind(
            imageView = artworkImage,
            album = currentState.album,
            fallbackRes = R.drawable.noalbumcover_220,
            sizePx = size,
        )
    }

    private fun hideSource() {
        val source = state?.sourceView ?: return restoreSource()
        if (hiddenSource != source) {
            restoreSource()
            hiddenSourceAlpha = source.alpha
        }
        hiddenSource = source
        source.alpha = 0f
    }

    private fun restoreSource() {
        hiddenSource?.alpha = hiddenSourceAlpha
        hiddenSource = null
        hiddenSourceAlpha = 1f
    }

    private fun hideNow() {
        shown = false
        dismissing = false
        applyProgress(0f)
        restoreSource()
        visibility = GONE
        onHidden()
    }

    private fun cancelAnimation() {
        runToken++
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
    }

    private fun nextRunToken(): Int {
        runToken++
        return runToken
    }
}

private fun lerp(start: Float, end: Float, progress: Float): Float {
    return start + (end - start) * progress
}

private const val BrowserOpenDurationMs = 420L
private const val BrowserCloseDurationMs = 300L
private const val BrowserFallbackInsetRatio = 0.03f
private const val BrowserArcBendRatio = 0.18f
