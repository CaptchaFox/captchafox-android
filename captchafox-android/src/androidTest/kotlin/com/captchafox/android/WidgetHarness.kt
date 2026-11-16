package com.captchafox.android

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * A bridge whose page loads a fake of the widget's script instead of cdn.captchafox.com, so that tests drive the
 * widget's callbacks and need no network. Unless [attached] is false, its web view is shown in the host's window.
 */
internal class WidgetHarness(
    host: HostActivity?,
    configuration: CaptchaFoxConfiguration = configuration(),
    scriptUrl: String = FAKE_SCRIPT_URL,
    supportsMessages: () -> Boolean = { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) },
    attached: Boolean = true,
) {
    val events = mutableListOf<WidgetEvent>()

    /** The URLs the bridge opened in the system browser. */
    val opened = mutableListOf<Uri>()

    val bridge = WidgetBridge(host ?: WarmUp.context, configuration, scriptUrl, { opened += it }, supportsMessages)

    init {
        bridge.onEvent = { events += it }
        if (attached && host != null) {
            host.show(bridge.webView)
            bridge.onWebViewReplaced = { old, new ->
                host.container.removeView(old)
                host.show(new)
            }
        }
    }

    /** Calls `execute()` and returns once the page has called the widget's `execute`. */
    suspend fun startExecution(): Execution {
        val executions = executionCount()
        val execution = Execution(bridge)
        until { executionCount() == executions + 1 }
        return execution
    }

    suspend fun executionCount(): Int = page("return window.fake ? window.fake.executions : -1").toInt()

    /** Calls the callback that the page passed to the fake widget's `render`, as the widget does. */
    suspend fun widgetCalls(callback: String, vararg arguments: String) {
        page("window.fake.options[${JSONObject.quote(callback)}].apply(null, ${JSONArray(arguments.toList())})")
    }

    /** Posts a message to the bridge from the page's main frame. */
    suspend fun post(message: String) {
        page("window.$BRIDGE.postMessage(${JSONObject.quote(message)})")
    }

    /** Runs JavaScript in the page as the body of a function and returns its result as JSON. */
    suspend fun page(body: String): String = withTimeout(180_000) {
        suspendCancellableCoroutine { continuation ->
            bridge.webView.evaluateJavascript("(function () { $body })()") { continuation.resume(it) }
        }
    }

    companion object {
        const val BASE_URL = "https://app.example/"
        const val BRIDGE = WidgetPage.BRIDGE_NAME

        fun configuration(siteKey: String = "sk_test", mode: CaptchaFoxConfiguration.Mode? = null): CaptchaFoxConfiguration =
            CaptchaFoxConfiguration(siteKey, BASE_URL, mode)

        /** A harness whose widget has reported that it is loaded. */
        suspend fun loaded(host: HostActivity, configuration: CaptchaFoxConfiguration = configuration()): WidgetHarness {
            val harness = WidgetHarness(host, configuration)
            until { harness.events == listOf(WidgetEvent.Load) }
            return harness
        }

        private const val FAKE_SCRIPT = """
            window.fake = { executions: 0, resets: 0, rejections: [] };
            window.captchafox = {
              render: function (element, options) {
                window.fake.element = element;
                window.fake.options = options;
                return 'widget';
              },
              execute: function () {
                window.fake.executions += 1;
                return new Promise(function (resolve, reject) {
                  window.fake.rejections.push(reject);
                  window.fake.rejectExecution = reject;
                });
              },
              reset: function () { window.fake.resets += 1; },
            };
            // As the widget closes its challenge on the Escape key.
            document.addEventListener('keydown', function (event) {
              if (event.key === 'Escape' && event.keyCode === 27) {
                window.fake.options.onClose();
              }
            });
            window.captchafoxOnLoad();
        """

        val FAKE_SCRIPT_URL = "data:text/javascript;base64," + Base64.encodeToString(FAKE_SCRIPT.toByteArray(), Base64.NO_WRAP)
    }
}

/** A call of `execute()` in a coroutine of its own, whose outcome tests wait for with a timeout instead of hanging. */
internal class Execution(bridge: WidgetBridge) {
    var result: Result<String>? = null
        private set

    private val job: Job = CoroutineScope(Dispatchers.Main).launch {
        result = runCatching { bridge.execute() }
    }

    fun cancel() {
        job.cancel()
    }

    /** The token, or the exception the call threw. */
    suspend fun token(): String {
        until { result != null }
        return result!!.getOrThrow()
    }
}

/**
 * Taps the middle of the web view as a finger does, once its content is drawn. A newly booted emulator may show that a
 * system app is not responding; that dialog takes the focus, and WebView takes no taps without it, so the tap waits for
 * the app instead.
 */
suspend fun tap(view: WebView) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    until {
        if (!view.hasWindowFocus()) {
            // UI Automator waits for the main thread to be idle, so it runs on another thread.
            withContext(Dispatchers.IO) {
                // The dialog may close by itself between finding and clicking.
                runCatching { device.findObject(By.res("android", "aerr_wait"))?.click() }
            }
        }
        view.hasWindowFocus()
    }
    // WebView drops taps on content that it has not drawn yet.
    suspendCancellableCoroutine { continuation ->
        WebViewCompat.postVisualStateCallback(view, 0) { continuation.resume(Unit) }
    }

    val down = SystemClock.uptimeMillis()
    for ((action, time) in listOf(MotionEvent.ACTION_DOWN to down, MotionEvent.ACTION_UP to down + 50)) {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coordinates = MotionEvent.PointerCoords().apply {
            x = view.width / 2f
            y = view.height / 2f
            pressure = 1f
            size = 1f
        }
        val event = MotionEvent.obtain(
            down, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}

/** The index of the child in its parent, which draws later children above earlier ones. */
fun View.indexInParent(): Int = (parent as ViewGroup).indexOfChild(this)

/** Waits until the condition holds. The timeout counts once WebView has loaded its first page. */
suspend fun until(timeoutMillis: Long = 180_000, condition: suspend () -> Boolean) {
    WarmUp.ready()
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    while (!condition()) {
        if (SystemClock.uptimeMillis() > deadline) {
            throw AssertionError("The condition did not hold within $timeoutMillis ms.")
        }
        delay(10)
    }
}

/**
 * On the first boot of a new emulator, WebView may take minutes to load its first page, and a first web view may never
 * load. Before any wait starts counting, one widget page loads, in a new web view every two minutes, for at most ten.
 */
object WarmUp {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var warmUp: Deferred<Boolean>? = null

    suspend fun ready() {
        val warmUp = warmUp ?: CoroutineScope(Dispatchers.Main).async {
            val deadline = SystemClock.uptimeMillis() + 600_000
            while (SystemClock.uptimeMillis() < deadline) {
                val harness = WidgetHarness(null)
                val retry = minOf(deadline, SystemClock.uptimeMillis() + 120_000)
                while (SystemClock.uptimeMillis() < retry) {
                    if (WidgetEvent.Load in harness.events) {
                        harness.bridge.webView.destroy()
                        return@async true
                    }
                    delay(200)
                }
                harness.bridge.webView.destroy()
            }
            false
        }
        this.warmUp = warmUp
        if (!warmUp.await()) {
            throw AssertionError("WebView loaded no page within ten minutes.")
        }
    }
}
