package com.captchafox.android

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.annotation.MainThread

/**
 * The CaptchaFox widget as a view. It loads the widget when it is created; [listener] is called on the main thread.
 * Create it in code; use it on the main thread.
 */
@SuppressLint("ViewConstructor")
public class CaptchaFoxView internal constructor(
    context: Context,
    private val bridge: WidgetBridge,
) : FrameLayout(context) {
    public constructor(context: Context, configuration: CaptchaFoxConfiguration) :
        this(context, WidgetBridge(context, configuration))

    public var listener: CaptchaFoxListener? = null

    /** Whether a progress indicator shows until the widget is loaded or reports an error; `true` by default. */
    public var showsLoadingIndicator: Boolean = true
        set(value) {
            field = value
            updateLoadingIndicator()
        }

    internal val loadingIndicator = ProgressBar(context)
    private var isLoading = true

    /** Whether an open challenge shows over the whole screen; not inside [CaptchaFoxDialog], which fills it already. */
    internal var movesChallengeToOverlay = true

    /** The window that shows the web view while a challenge is open. */
    internal var overlay: ChallengeOverlay? = null
        private set

    /** Whether the widget shows a challenge, which the overlay shows while the view is in a window. */
    private var isChallengeOpen = false
    private var ownMinimumWidth = 0
    private var ownMinimumHeight = 0

    // The widget shows its success in the challenge for a second after verify before it leaves it.
    private val closeOverlayAfterVerify = Runnable { endChallenge() }
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        addView(bridge.webView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(loadingIndicator, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        updateLoadingIndicator()
        bridge.onEvent = ::deliver
        bridge.onWebViewReplaced = { old, new ->
            mainHandler.removeCallbacks(closeOverlayAfterVerify)
            isChallengeOpen = false
            endOverlay()
            removeView(old)
            addView(new, 0, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        bridge.onPageReloaded = ::endChallenge
    }

    /**
     * Starts the verification, as hidden mode needs it, and returns the token once the widget has one. It throws
     * [CaptchaFoxException] before the widget is loaded, when the widget reports an error, when the visitor closes the
     * challenge, when `execute()` is called again and when [reset] is called, and `CancellationException` when the
     * calling coroutine is cancelled. Call it on the main thread.
     */
    @MainThread
    public suspend fun execute(): String = bridge.execute()

    /** Resets the widget, so that the visitor verifies again. */
    @MainThread
    public fun reset() {
        endChallenge()
        bridge.reset()
    }

    /**
     * Removes and destroys the view's web view, for when the app no longer shows the widget, such as in its activity's
     * `onDestroy`. A pending [execute] throws `CancellationException`; the view shows nothing afterwards.
     */
    @MainThread
    public fun destroy() {
        endChallenge()
        removeView(bridge.webView)
        bridge.destroy()
    }

    private fun updateLoadingIndicator() {
        loadingIndicator.visibility = if (showsLoadingIndicator && isLoading) VISIBLE else GONE
    }

    private fun deliver(event: WidgetEvent) {
        if (event == WidgetEvent.Load || event is WidgetEvent.Error) {
            isLoading = false
            updateLoadingIndicator()
        }

        // The widget shows its challenge from its challenge-open callback until it verifies, fails, reports an error,
        // is closed or expires.
        when (event) {
            WidgetEvent.ChallengeOpen -> {
                mainHandler.removeCallbacks(closeOverlayAfterVerify)
                isChallengeOpen = true
                openOverlay()
            }
            is WidgetEvent.Verify -> mainHandler.postDelayed(closeOverlayAfterVerify, 1_000)
            is WidgetEvent.Error, WidgetEvent.Fail, WidgetEvent.Close, WidgetEvent.Expire -> endChallenge()
            WidgetEvent.Load -> Unit
        }

        when (event) {
            WidgetEvent.Load -> listener?.onLoad()
            is WidgetEvent.Verify -> listener?.onVerify(event.token)
            is WidgetEvent.Error -> listener?.onError(event.message)
            WidgetEvent.Fail -> listener?.onFail()
            WidgetEvent.Expire -> listener?.onExpire()
            WidgetEvent.Close -> listener?.onClose()
            WidgetEvent.ChallengeOpen -> listener?.onChallengeOpen()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (isChallengeOpen) {
            openOverlay()
        }
    }

    // A view that leaves its window, as when its fragment is replaced, takes its web view back from the overlay.
    override fun onDetachedFromWindow() {
        closeOverlay()
        super.onDetachedFromWindow()
    }

    private fun endChallenge() {
        mainHandler.removeCallbacks(closeOverlayAfterVerify)
        isChallengeOpen = false
        closeOverlay()
    }

    /**
     * Moves the web view into a window over the whole screen; the view keeps its size and shows its empty frame. Only
     * an activity that is alive can show the window, and only while the view is in it; the challenge stays in the view
     * otherwise.
     */
    private fun openOverlay() {
        val activity = context.activity()
        if (!movesChallengeToOverlay || overlay != null || activity == null || activity.isFinishing || activity.isDestroyed ||
            !isAttachedToWindow
        ) {
            return
        }

        val webView = bridge.webView
        ownMinimumWidth = minimumWidth
        ownMinimumHeight = minimumHeight
        minimumWidth = width
        minimumHeight = height
        removeView(webView)
        val overlay = ChallengeOverlay(activity, onBack = bridge::closeChallenge, onOwnerDestroyed = ::closeOverlay)
        this.overlay = overlay
        // Whatever else dismisses the overlay, the web view comes back.
        overlay.setOnDismissListener {
            if (this.overlay === overlay) {
                closeOverlay()
            }
        }
        overlay.show(webView)
    }

    /** Moves the web view back into the view and dismisses the overlay. */
    private fun closeOverlay() {
        if (endOverlay()) {
            addView(bridge.webView, 0, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    }

    /** Dismisses the overlay with the web view it shows; `false` when there is none. */
    private fun endOverlay(): Boolean {
        val overlay = overlay ?: return false

        this.overlay = null
        overlay.content.removeAllViews()
        minimumWidth = ownMinimumWidth
        minimumHeight = ownMinimumHeight
        overlay.dismiss()
        return true
    }
}
