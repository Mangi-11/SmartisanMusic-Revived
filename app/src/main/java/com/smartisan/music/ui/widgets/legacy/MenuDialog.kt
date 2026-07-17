package com.smartisan.music.ui.widgets.legacy

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.LinearLayout
import com.smartisan.music.R

class MenuDialog @JvmOverloads constructor(
    context: Context,
    private val location: Int = LOCATION_APP_BOTTOM,
) : Dialog(context, R.style.MenuDialogTheme) {

    private val titleBar: MenuDialogTitleBar
    private val okButton: ShadowButton
    private val listView: ListView
    private val marginView: Int
    private val marginEdge: Int

    init {
        setContentView(R.layout.menu_dialog)
        titleBar = findViewById(R.id.menu_dialog_title_bar)
        okButton = findViewById(R.id.btn_ok)
        listView = findViewById(R.id.content_list)
        marginView = context.resources.getDimensionPixelOffset(R.dimen.menu_dialog_btn_margin_view)
        marginEdge = context.resources.getDimensionPixelOffset(R.dimen.menu_dialog_btn_margin_edge)

        titleBar.forceRequestAccessibilityFocusWhenAttached(false)
        titleBar.setOnRightButtonClickListener {
            cancel()
        }
        titleBar.setOnLeftButtonClickListener {
            cancel()
        }
        titleBar.setShadowVisible(false)
        titleBar.setLeftButtonVisibility(View.INVISIBLE)
        titleBar.setRightButtonVisibility(View.VISIBLE)
        listView.isFocusable = false
        locateDialog(location)
        setCanceledOnTouchOutside(true)
    }

    override fun show() {
        super.show()
        locateDialog(location)
    }

    override fun setTitle(titleId: Int) {
        setTitle(context.getText(titleId))
    }

    override fun setTitle(title: CharSequence?) {
        titleBar.setTitle(title)
    }

    fun setTitleSinleLine(singleLine: Boolean) {
        titleBar.setTitleSingleLine(singleLine)
    }

    fun setNegativeButton(listener: View.OnClickListener?) {
        titleBar.setOnRightButtonClickListener(listener)
        titleBar.setOnLeftButtonClickListener(listener)
    }

    fun setNegativeButton(textId: Int, listener: View.OnClickListener?) {
        setNegativeButton(listener)
    }

    fun setNegativeButton(text: CharSequence?, listener: View.OnClickListener?) {
        setNegativeButton(listener)
    }

    fun setNegativeImage(resId: Int, listener: View.OnClickListener?) {
        titleBar.setRightImageRes(resId)
        titleBar.setLeftImageViewRes(resId)
        setNegativeButton(listener)
    }

    fun setPositiveButton(textId: Int, listener: View.OnClickListener?) {
        setPositiveButton(context.getText(textId), listener)
    }

    fun setPositiveButton(text: CharSequence?, listener: View.OnClickListener?) {
        okButton.visibility = View.VISIBLE
        adjustLayoutParams()
        okButton.text = text
        okButton.setOnClickListener { view ->
            dismiss()
            listener?.onClick(view)
        }
    }

    fun setPositiveButtonGone() {
        okButton.text = null
        okButton.setOnClickListener(null)
        okButton.visibility = View.GONE
        adjustLayoutParams()
    }

    fun setPositiveBgStyle(style: ShadowButton.LongButtonStyle) {
        if (style == ShadowButton.LongButtonStyle.RED) {
            okButton.setBackgroundResource(R.drawable.shrink_long_btn_red_selector)
        }
    }

    fun setPositiveRedBg(red: Boolean) {
        if (red) {
            setPositiveBgStyle(ShadowButton.LongButtonStyle.RED)
        }
    }

    fun setAdapter(adapter: ListAdapter) {
        listView.visibility = View.VISIBLE
        adjustLayoutParams()
        listView.adapter = adapter
    }

    fun setAdaper(adapter: ListAdapter, listener: AdapterView.OnItemClickListener?) {
        setAdapter(adapter)
        listView.onItemClickListener = listener
    }

    fun getListView(): ListView = listView

    override fun setOnCancelListener(listener: DialogInterface.OnCancelListener?) {
        super.setOnCancelListener(listener)
    }

    private fun locateDialog(dialogLocation: Int) {
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val gravity = if (dialogLocation == LOCATION_APP_BOTTOM) {
                Gravity.BOTTOM
            } else {
                Gravity.CENTER
            }
            setGravity(gravity)
            if (gravity == Gravity.BOTTOM) {
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                )
            } else {
                setLayout(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                )
            }
        }
    }

    private fun adjustLayoutParams() {
        val hasListAndButton = listView.visibility == View.VISIBLE && okButton.visibility == View.VISIBLE
        (okButton.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.topMargin = if (hasListAndButton) 0 else marginView
            okButton.layoutParams = params
        }
        val bottomPadding = if (hasListAndButton) marginView else marginEdge
        listView.setPadding(
            listView.paddingLeft,
            listView.paddingTop,
            listView.paddingRight,
            bottomPadding,
        )
    }

    companion object {
        const val LOCATION_APP_BOTTOM = 0
        const val LOCATION_APP_CENTER = 1
        const val LOCATION_DISPLAY_CENTER = 2
    }
}
