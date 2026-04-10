package com.niyarin.simplemozckeyboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow

class LongPressKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : KeyboardView(context, attrs) {

    interface LongPressPopupListener {
        fun onPopupTextSelected(text: String)
    }

    var longPressPopupListener: LongPressPopupListener? = null
    var longPressNumbersEnabled: Boolean = false

    private val longPressNumberMap = mapOf(
        113 to "1",
        119 to "2",
        101 to "3",
        114 to "4",
        116 to "5",
        121 to "6",
        117 to "7",
        105 to "8",
        111 to "9",
        112 to "0",
    )

    private var popupWindow: PopupWindow? = null

    override fun onLongPress(key: Keyboard.Key?): Boolean {
        if (!longPressNumbersEnabled || key == null) {
            return super.onLongPress(key)
        }

        val code = key.codes.firstOrNull() ?: return super.onLongPress(key)
        val popupText = longPressNumberMap[code] ?: return super.onLongPress(key)

        showNumberPopup(key, popupText)
        return true
    }

    override fun closing() {
        dismissPopup()
        super.closing()
    }

    private fun showNumberPopup(key: Keyboard.Key, popupText: String) {
        dismissPopup()

        val popupButton = Button(context).apply {
            text = popupText
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = resources.displayMetrics.density * 12f
                setColor(0xFF3A3A3A.toInt())
                setStroke((resources.displayMetrics.density * 1.5f).toInt(), 0xFF707070.toInt())
            }
            setPadding(32, 20, 32, 20)
            setOnClickListener {
                longPressPopupListener?.onPopupTextSelected(popupText)
                dismissPopup()
            }
        }

        popupButton.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        val popupWidth = popupButton.measuredWidth
        val popupHeight = popupButton.measuredHeight

        popupWindow = PopupWindow(
            popupButton,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = resources.displayMetrics.density * 8f
            showAtLocation(
                this@LongPressKeyboardView,
                Gravity.NO_GRAVITY,
                key.x + (key.width - popupWidth) / 2,
                (key.y - popupHeight - (resources.displayMetrics.density * 8f).toInt()).coerceAtLeast(0)
            )
        }
    }

    private fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
    }
}
