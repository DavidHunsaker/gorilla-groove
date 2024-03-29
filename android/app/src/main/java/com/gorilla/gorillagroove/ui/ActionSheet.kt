package com.gorilla.gorillagroove.ui

import android.app.ActionBar
import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gorilla.gorillagroove.R
import com.gorilla.gorillagroove.service.GGLog.logInfo
import com.gorilla.gorillagroove.util.getPixelsFromDp
import kotlinx.android.synthetic.main.dialog_bottom_notification_action.view.*

class ActionSheet(activity: Activity, items: List<ActionSheetItem>) : BottomSheetDialog(activity) {
    init {
        logInfo("Opening action sheet")
        val sheetView = View.inflate(activity, R.layout.dialog_bottom_notification_action, null)
        setContentView(sheetView)
        show()

        // Remove default white color background. Doesn't seem possible to do this in XML
        findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)!!.background = null

        sheetView.closeButton.setOnClickListener {
            logInfo("Action sheet canceled with 'Cancel' button")
            this.dismiss()
        }

        val container = sheetView.mainActionsContainer

        items.forEachIndexed { index, item ->
            val textView = TextView(ContextThemeWrapper(activity, R.style.ActionSheetItem))
            textView.text = item.text

            if (item.type == ActionSheetType.DESTRUCTIVE) {
                textView.setTextColor(activity.getColor(R.color.dangerRed))
            }

            textView.setOnClickListener {
                logInfo("Action sheet item selected: '${item.text}'")
                this.dismiss()
                item.onClick()
            }

            container.addView(textView)

            if (index != items.size - 1) {
                val spacer = View(activity)
                spacer.setBackgroundColor(ContextCompat.getColor(context, R.color.grey1))
                val height = getPixelsFromDp(1f)
                spacer.layoutParams = LinearLayout.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, height)

                container.addView(spacer)
            }
        }
    }

    override fun cancel() {
        super.cancel()

        logInfo("Action sheet canceled")
    }
}

data class ActionSheetItem(
    val text: String,
    val type: ActionSheetType = ActionSheetType.NORMAL,
    val onClick: () -> Unit,
)

enum class ActionSheetType {
    NORMAL, DESTRUCTIVE
}
