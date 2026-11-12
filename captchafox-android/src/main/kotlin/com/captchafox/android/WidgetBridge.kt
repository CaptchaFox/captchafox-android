package com.captchafox.android

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the web view with the widget's page: loads the page, turns its messages into events, settles `execute()` and
 * keeps the web view on the page. Used on the main thread only.
 */
@MainThread
internal class WidgetBridge(
    private val context: Context,
    private val configuration: CaptchaFoxConfiguration,
    scriptUrl: String = WidgetPage.SCRIPT_URL,
    private val openUrl: (Uri) -> Unit = { openInBrowser(context, it) },
    private val supportsMessages: () -> Boolean = { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) },
) {
    var onEvent: (WidgetEvent) -> Unit = {}

    /** Called when the widget's page was left and loads again, so that an open challenge is gone. */
    var onPageReloaded: () -> Unit = {}

    /** Called with the old and the new web view when the renderer of the old one ended and the new one took its place. */
    var onWebViewReplaced: (old: WebView, new: WebView) -> Unit = { _, _ -> }

    var webView: WebView = makeWebView()
        private set

    private val page = WidgetPage.html(configuration, scriptUrl)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var expectsPageLoad = false
    private var isLoaded = false
    @VisibleForTesting
    var isDestroyed = false
        private set
    private var pending: PendingExecution? = null
    private var executionCount = 0

    /** Every message the page's frames posted, also those the bridge ignores; tests wait for it. */
    @VisibleForTesting
    var receivedMessages = 0
        private set

    @get:VisibleForTesting
    val hasPendingExecution: Boolean
        get() = pending != null

    private class PendingExecution(val id: Int, val continuation: CancellableContinuation<String>)

    init {
        start()
    }

    suspend fun execute(): String {
        check(Looper.myLooper() == Looper.getMainLooper()) { "execute() must be called on the main thread." }
        currentCoroutineContext().ensureActive()
        if (!isLoaded) {
            throw CaptchaFoxException(CaptchaFoxException.Code.NOT_LOADED)
        }

        settle(Result.failure(CaptchaFoxException(CaptchaFoxException.Code.SUPERSEDED)))
        executionCount += 1
        val id = executionCount
        // A cancelled coroutine settles the call itself; settling it again later does nothing.
        return suspendCancellableCoroutine { continuation ->
            pending = PendingExecution(id, continuation)
            continuation.invokeOnCancellation {
                // May run on any thread; the coroutine has settled the call itself.
                mainHandler.post {
                    if (pending?.id == id) {
                        pending = null
                    }
                }
            }
            webView.evaluateJavascript("window.captchafoxExecute($id)", null)
        }
    }

    fun reset() {
        settle(Result.failure(CaptchaFoxException(CaptchaFoxException.Code.RESET)))
        if (!isLoaded) {
            return
        }

        webView.evaluateJavascript("window.captchafoxReset()", null)
    }

    /** Closes an open challenge as the widget's own close does, which the widget offers for the Escape key. */
    fun closeChallenge() {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            webView.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_ESCAPE))
        }
    }

    /** Destroys the web view; a pending `execute()` throws `CancellationException`, and later calls find nothing loaded. */
    fun destroy() {
        if (isDestroyed) {
            return
        }

        isDestroyed = true
        isLoaded = false
        settle(Result.failure(CancellationException("The widget was destroyed.")))
        webView.destroy()
    }

    /**
     * The main frame shows only the widget's page, which the bridge loads itself: every navigation of the main frame
     * is cancelled, and a link the visitor tapped opens in the system browser. Without multiple windows, a link to a
     * new window navigates the main frame as well.
     *
     * @return whether the navigation is cancelled.
     */
    fun cancelsNavigation(isForMainFrame: Boolean, url: Uri, hasGesture: Boolean): Boolean {
        if (!isForMainFrame) {
            // Frames inside the page, such as the widget's.
            return false
        }

        val scheme = url.scheme?.lowercase(Locale.ROOT)
        if (hasGesture && (scheme == "http" || scheme == "https")) {
            openUrl(url)
        }
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWebView(): WebView {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.webViewClient = Client()
        return webView
    }

    /** Prepares a new web view for the page's messages and loads the page. */
    // supportsMessages checks the feature, which lint cannot follow.
    @SuppressLint("RequiresFeature")
    private fun start() {
        if (!supportsMessages()) {
            // Never a JavaScript interface instead: it would take messages from every frame.
            mainHandler.post {
                deliver(WidgetEvent.Error("This device's WebView is too old for CaptchaFox; update Android System WebView."))
            }
            return
        }

        WebViewCompat.addWebMessageListener(webView, WidgetPage.BRIDGE_NAME, setOf(configuration.origin)) { _, message, _, isMainFrame, _ ->
            receive(message, isMainFrame)
        }
        loadPage()
    }

    private fun loadPage() {
        expectsPageLoad = true
        webView.loadDataWithBaseURL(configuration.baseUrl, page, "text/html", "utf-8", null)
    }

    /**
     * The web view is not asked about every navigation of the main frame, such as a form that a frame inside the page
     * submits to it with POST; when another page than the widget's starts, the widget's page loads again.
     */
    private fun pageStarted(view: WebView) {
        if (view !== webView || isDestroyed) {
            return
        }

        if (expectsPageLoad) {
            expectsPageLoad = false
            return
        }

        isLoaded = false
        settle(Result.failure(CaptchaFoxException(CaptchaFoxException.Code.WIDGET, "The widget's page was left.")))
        loadPage()
        onPageReloaded()
    }

    private fun receive(message: WebMessageCompat, isMainFrame: Boolean) {
        receivedMessages += 1
        // Only the widget's page runs in the main frame; a frame inside it never speaks for the widget.
        if (!isMainFrame || message.type != WebMessageCompat.TYPE_STRING) {
            return
        }

        val pageMessage = message.data?.let(PageMessage::parse) ?: return
        when (pageMessage) {
            is PageMessage.Event -> deliver(pageMessage.event)
            is PageMessage.ExecutionFailed -> {
                // A late failure of an earlier call must not settle the call that superseded it.
                if (pending?.id == pageMessage.id) {
                    settle(Result.failure(CaptchaFoxException(CaptchaFoxException.Code.WIDGET, pageMessage.message)))
                }
            }
        }
    }

    private fun deliver(event: WidgetEvent) {
        if (isDestroyed) {
            return
        }

        when (event) {
            WidgetEvent.Load -> isLoaded = true
            is WidgetEvent.Verify -> settle(Result.success(event.token))
            is WidgetEvent.Error -> settle(Result.failure(CaptchaFoxException(CaptchaFoxException.Code.WIDGET, event.message)))
            WidgetEvent.Close -> settle(Result.failure(CaptchaFoxException(CaptchaFoxException.Code.CLOSED)))
            WidgetEvent.Fail, WidgetEvent.Expire, WidgetEvent.ChallengeOpen -> Unit
        }
        onEvent(event)
    }

    private fun settle(result: Result<String>) {
        val pending = pending ?: return

        this.pending = null
        result.fold(pending.continuation::resume, pending.continuation::resumeWithException)
    }

    /** Android may end the renderer, as under memory pressure; the web view is unusable then, and a new one loads the page. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun rendererGone(gone: WebView) {
        if (gone !== webView || isDestroyed) {
            return
        }

        isLoaded = false
        settle(Result.failure(CaptchaFoxException(CaptchaFoxException.Code.WEB_CONTENT_TERMINATED)))
        webView = makeWebView()
        onWebViewReplaced(gone, webView)
        gone.destroy()
        start()
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            cancelsNavigation(request.isForMainFrame, request.url, request.hasGesture())

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            pageStarted(view)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            rendererGone(view)
            return true
        }
    }

    private companion object {
        fun openInBrowser(context: Context, url: Uri) {
            val intent = Intent(Intent.ACTION_VIEW, url)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (error: ActivityNotFoundException) {
                // No browser on the device; the link stays closed.
            }
        }
    }
}
