package com.smartisan.music.ui.shell

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.smartisan.music.R

internal class LegacyAlbumTileImageView(context: Context) : ImageView(context) {
    private var coverMask: Drawable? =
        context.getDrawable(R.drawable.mask_albumcover_tile_selector)?.mutate()

    init {
        val padding = resources.getDimensionPixelSize(R.dimen.gridview_padding)
        setPadding(padding, padding, padding, padding)
        scaleType = ScaleType.CENTER_CROP
        cropToPadding = true
        isDuplicateParentStateEnabled = true
    }

    fun setMaskEnabled(enabled: Boolean) {
        coverMask = context.getDrawable(
            if (enabled) R.drawable.mask_albumcover_tile_selector else R.drawable.no_mask_albumcover_tile_selector,
        )?.mutate()
        coverMask?.state = drawableState
        invalidate()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        coverMask?.state = drawableState
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        coverMask?.let { mask ->
            mask.setBounds(0, 0, width, height)
            mask.draw(canvas)
        }
    }
}
