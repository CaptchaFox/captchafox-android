package com.captchafox.android

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.annotation.ColorInt
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shows the CaptchaFox widget over the current screen. Create it with an activity and call [show]; it shows a loading
 * indicator until the widget is loaded and dismisses itself on verify, error and close, and when its activity is
 * destroyed. In hidden mode it starts the verification once the widget is loaded and shows the widget only when a
 * challenge opens. A dismissed dialog has destroyed its widget: create a new one to show the widget again.
 *
 * Set [backdropColor], [headerView], [footerView], [loadingView] and [dismissDelayAfterVerifyMillis] before showing it.
 */
public class CaptchaFoxDialog internal constructor(
    context: Context,
    mode: CaptchaFoxConfiguration.Mode?,
    private val makeWidget: (Context) -> CaptchaFoxView,
) : Dialog(context) {
    public constructor(context: Context, configuration: CaptchaFoxConfiguration) :
        this(context, configuration.mode, { CaptchaFoxView(it, configuration) })

    public var listener: CaptchaFoxListener? = null

    /** The colour over the current screen; by default black at 60 % opacity. */
    @ColorInt
    public var backdropColor: Int = DEFAULT_BACKDROP_COLOR
        set(value) {
            field = value
            content?.setBackgroundColor(value)
        }

    /** A view above the widget, such as a title. */
    public var headerView: View? = null

    /** A view below the widget. */
    public var footerView: View? = null

    /** Shown in place of the progress indicator while the widget loads, and in hidden mode until the token arrives. */
    public var loadingView: View? = null

    /**
     * How many milliseconds the dialog stays after verify, so that the visitor sees the widget's success state; 500 by
     * default, 0 dismisses it at once. The listener's `onVerify` is called at once either way.
     */
    public var dismissDelayAfterVerifyMillis: Long = 500

    /** Called once when the dialog dismisses itself. */
    internal var onFinish: (() -> Unit)? = null

    /** Created with the dialog's views, when it is first shown, so that nothing runs before. */
    internal var widget: CaptchaFoxView? = null
        private set
    internal val loadingIndicator = ProgressBar(context)
    internal var content: FrameLayout? = null
        private set

    private val isHiddenMode = mode == CaptchaFoxConfiguration.Mode.HIDDEN

    /** The activity whose window shows the dialog; a dialog has no owner activity unless an app sets one. */
    private val activity = context.activity()
    private val mainHandler = Handler(Looper.getMainLooper())

    // The execution continues in a later turn of the main thread, after the widget's callback that settled it.
    private val scope = CoroutineScope(
        object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                mainHandler.post(block)
            }
        },
    )
    private var execution: Job? = null
    private var isFinished = false
    private val dismissFinished = Runnable {
        onFinish?.invoke()
        dismiss()
    }

    /** Dismisses the dialog while its activity's window still exists, as a configuration change destroys the activity. */
    private val ownerDestroyed = OnActivityDestroyed(activity) { dismiss() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ownerDestroyed.register()

        val widget = makeWidget(context)
        this.widget = widget
        // The dialog's own loading view stands in for the widget's.
        widget.showsLoadingIndicator = false
        widget.alpha = 0f
        widget.listener = WidgetListener()
        // The dialog fills the screen already.
        widget.movesChallengeToOverlay = false

        val loading = loadingView ?: loadingIndicator
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        column.fitsSystemWindows = true
        headerView?.let { column.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }
        column.addView(widget, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        footerView?.let { column.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

        // Below the transparent web view: in hidden mode the challenge covers the loading view, which shows again when
        // the widget closes the challenge and checks the answer before it reports the token.
        val content = FrameLayout(context)
        this.content = content
        content.setBackgroundColor(backdropColor)
        content.addView(loading, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        content.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(content)
        window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setLayout(MATCH_PARENT, MATCH_PARENT)
        }
    }

    /** Called when the dialog is dismissed: nothing of the widget outlives it. */
    override fun onStop() {
        super.onStop()

        ownerDestroyed.unregister()
        isFinished = true
        mainHandler.removeCallbacks(dismissFinished)
        execution?.cancel()
        widget?.destroy()
    }

    private inner class WidgetListener : CaptchaFoxListener {
        override fun onLoad() {
            listener?.onLoad()
            widgetLoaded()
        }

        override fun onVerify(token: String) {
            listener?.onVerify(token)
            finish(dismissDelayAfterVerifyMillis)
        }

        override fun onError(message: String) {
            listener?.onError(message)
            finish()
        }

        override fun onFail() {
            listener?.onFail()
        }

        override fun onExpire() {
            listener?.onExpire()
        }

        override fun onClose() {
            listener?.onClose()
            finish()
        }

        override fun onChallengeOpen() {
            listener?.onChallengeOpen()
            if (isHiddenMode) {
                showWidget()
            }
        }
    }

    private fun widgetLoaded() {
        val widget = widget ?: return
        if (!isHiddenMode) {
            showWidget()
            return
        }

        execution = scope.launch {
            try {
                // The widget's callbacks report a token and most errors.
                widget.execute()
            } catch (cancellation: CancellationException) {
                // The dialog cancelled it when it finished or was dismissed.
            } catch (error: Exception) {
                executionFailed(error)
            }
        }
    }

    /**
     * The widget's `execute()` may fail without calling its error callback; once the dialog has finished, the callback
     * has reported the failure already.
     */
    private fun executionFailed(error: Exception) {
        if (isFinished) {
            return
        }

        val captchaFoxError = error as? CaptchaFoxException
        listener?.onError(captchaFoxError?.message ?: captchaFoxError?.code?.value ?: error.toString())
        finish()
    }

    private fun showWidget() {
        if (!isHiddenMode) {
            loadingIndicator.visibility = View.GONE
            loadingView?.visibility = View.GONE
        }
        widget?.alpha = 1f
    }

    /** Finishes at once and dismisses the dialog after [delayMillis]. */
    private fun finish(delayMillis: Long = 0) {
        if (isFinished) {
            return
        }

        isFinished = true
        execution?.cancel()
        if (delayMillis <= 0) {
            dismissFinished.run()
            return
        }

        mainHandler.postDelayed(dismissFinished, delayMillis)
    }
}

/** Black at 60 % opacity. */
internal val DEFAULT_BACKDROP_COLOR: Int = Color.argb(153, 0, 0, 0)
