package com.captchafox.android

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * A window over the whole screen that shows a [CaptchaFoxView]'s web view while a challenge is open, which needs more
 * room than the view usually has. [onBack] answers the back button and the Escape key, which only the widget may act on;
 * [onOwnerDestroyed] is called while the activity's window still exists, before the overlay is dismissed with it.
 */
internal class ChallengeOverlay(
    activity: Activity,
    private val onBack: () -> Unit,
    onOwnerDestroyed: () -> Unit,
) : Dialog(activity) {
    val content = FrameLayout(activity)
    private val ownerDestroyed = OnActivityDestroyed(activity) { onOwnerDestroyed() }

    init {
        content.setBackgroundColor(DEFAULT_BACKDROP_COLOR)
        content.fitsSystemWindows = true
        // Only the widget ends its challenge; a cancelled overlay would hide the web view from the view.
        setCancelable(false)
    }

    fun show(webView: WebView) {
        content.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ownerDestroyed.register()
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(content)
        window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setLayout(MATCH_PARENT, MATCH_PARENT)
        }
    }

    override fun onStop() {
        super.onStop()

        ownerDestroyed.unregister()
    }

    @Deprecated("Dialog calls it for the back button, also with predictive back.")
    override fun onBackPressed() {
        onBack()
    }

    // Before the web view and the dialog's own key handling, which would dismiss the overlay on Escape.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) {
            return super.dispatchKeyEvent(event)
        }

        if (event.action == KeyEvent.ACTION_UP) {
            onBack()
        }
        return true
    }
}
