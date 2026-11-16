package com.captchafox.android

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume

class DialogTest {
    @Test
    fun showsTheWidgetOnceItIsLoaded() = onHost { host ->
        var loaded = false
        val (dialog, harness) = showDialog(host, mode = null) { dialog ->
            dialog.listener = object : CaptchaFoxListener {
                override fun onLoad() { loaded = true }
            }
        }

        assertEquals(0f, dialog.widget!!.alpha)
        assertEquals(View.VISIBLE, dialog.loadingIndicator.visibility)
        until { loaded }

        assertEquals(1f, dialog.widget!!.alpha)
        assertEquals(View.GONE, dialog.loadingIndicator.visibility)
        assertEquals(0, harness.executionCount())
    }

    @Test
    fun inHiddenModeExecutesOnLoadAndShowsTheWidgetOnlyForAChallenge() = onHost { host ->
        var challengeOpened = false
        val (dialog, harness) = showDialog(host, CaptchaFoxConfiguration.Mode.HIDDEN) { dialog ->
            dialog.listener = object : CaptchaFoxListener {
                override fun onChallengeOpen() { challengeOpened = true }
            }
        }

        until { harness.executionCount() == 1 }
        assertEquals(0f, dialog.widget!!.alpha)
        assertEquals(View.VISIBLE, dialog.loadingIndicator.visibility)

        harness.widgetCalls("onChallengeOpen")
        until { challengeOpened }
        assertEquals(1f, dialog.widget!!.alpha)
        // The dialog fills the screen already: its widget shows the challenge in place.
        assertNull(dialog.widget!!.overlay)
        assertSame(dialog.widget, harness.bridge.webView.parent)
        // The indicator stays below the web view, for the time between the challenge and the token.
        assertEquals(View.VISIBLE, dialog.loadingIndicator.visibility)
        assertTrue(dialog.loadingIndicator.indexInParent() < (dialog.widget!!.parent as View).indexInParent())
    }

    @Test
    fun finishesOnVerify() = onHost { host ->
        assertFinishesOn(host, "onVerify", "token-1")
    }

    @Test
    fun finishesOnError() = onHost { host ->
        assertFinishesOn(host, "onError", "Something failed")
    }

    @Test
    fun finishesOnClose() = onHost { host ->
        assertFinishesOn(host, "onClose")
    }

    @Test
    fun inHiddenModeAFailedExecutionFinishesTheDialog() = onHost { host ->
        val calls = mutableListOf<String>()
        val (dialog, harness) = showDialog(host, CaptchaFoxConfiguration.Mode.HIDDEN) { dialog ->
            dialog.listener = object : CaptchaFoxListener {
                override fun onError(message: String) { calls += "onError $message" }
            }
            dialog.onFinish = { calls += "finish" }
        }
        until { harness.executionCount() == 1 }

        harness.page("window.fake.rejectExecution(new Error('challenge-aborted'))")
        until { calls.size == 2 }

        assertEquals(listOf("onError challenge-aborted", "finish"), calls)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun inHiddenModeAnErrorIsReportedOnce() = onHost { host ->
        val calls = mutableListOf<String>()
        val (_, harness) = showDialog(host, CaptchaFoxConfiguration.Mode.HIDDEN) { dialog ->
            dialog.listener = object : CaptchaFoxListener {
                override fun onError(message: String) { calls += "onError $message" }
                override fun onFail() { calls += "onFail" }
            }
            dialog.onFinish = { calls += "finish" }
        }
        until { harness.executionCount() == 1 }

        // As the widget does: its error callback, then the rejection of its execute(), in one turn.
        harness.page("window.fake.options.onError('challenge-failed'); window.fake.rejectExecution(new Error('challenge-failed'));")
        until { "finish" in calls }
        runPostedWork()

        assertEquals(listOf("onError challenge-failed", "finish"), calls)
        assertTrue(harness.bridge.isDestroyed)
    }

    @Test
    fun dismissingItEndsThePendingExecutionAndDestroysTheWidget() = onHost { host ->
        val calls = mutableListOf<String>()
        val (dialog, harness) = showDialog(host, CaptchaFoxConfiguration.Mode.HIDDEN) { dialog ->
            dialog.listener = object : CaptchaFoxListener {
                override fun onError(message: String) { calls += "onError $message" }
            }
        }
        until { harness.executionCount() == 1 }

        dialog.dismiss()

        assertTrue(harness.bridge.isDestroyed)
        assertFalse(harness.bridge.hasPendingExecution)
        // A cancelled execution is no failure to report.
        runPostedWork()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun aDialogWhoseActivityIsDestroyedIsDismissedWithIt() {
        lateinit var harness: WidgetHarness
        lateinit var dialog: CaptchaFoxDialog
        val calls = mutableListOf<String>()
        val scenario = ActivityScenario.launch(HostActivity::class.java)
        scenario.onActivity { host ->
            harness = WidgetHarness(host, WidgetHarness.configuration(mode = CaptchaFoxConfiguration.Mode.HIDDEN), attached = false)
            dialog = CaptchaFoxDialog(host, CaptchaFoxConfiguration.Mode.HIDDEN) { CaptchaFoxView(it, harness.bridge) }
            dialog.listener = object : CaptchaFoxListener {
                override fun onError(message: String) { calls += "onError $message" }
            }
            dialog.show()
        }
        runBlocking(Dispatchers.Main) { until { harness.executionCount() == 1 } }

        // As a configuration change does to an app that leaves the dialog to itself.
        scenario.close()

        runBlocking(Dispatchers.Main) {
            assertFalse(dialog.isShowing)
            assertTrue(harness.bridge.isDestroyed)
            assertFalse(harness.bridge.hasPendingExecution)
            runPostedWork()
            assertEquals(emptyList<String>(), calls)
        }
    }

    @Test
    fun createsTheWidgetOnlyWhenShown() = onHost { host ->
        var made = 0
        val dialog = CaptchaFoxDialog(host, CaptchaFoxConfiguration.Mode.HIDDEN) {
            made += 1
            CaptchaFoxView(it, WidgetHarness(host, attached = false).bridge)
        }

        assertEquals(0, made)
        dialog.show()
        assertEquals(1, made)
        dialog.dismiss()
    }

    @Test
    fun placesTheHeaderAboveAndTheFooterBelowTheWidgetOnItsBackdrop() = onHost { host ->
        val header = TextView(host).apply { text = "Header" }
        val footer = TextView(host).apply { text = "Footer" }
        val (dialog, _) = showDialog(host, mode = null) { dialog ->
            dialog.backdropColor = Color.RED
            dialog.headerView = header
            dialog.footerView = footer
        }
        val widget = dialog.widget!!
        until { widget.height > 0 }

        assertEquals(Color.RED, (dialog.content!!.background as ColorDrawable).color)
        assertTrue(header.height > 0 && footer.height > 0)
        assertEquals(header.bottom, widget.top)
        assertEquals(footer.top, widget.bottom)
        assertSame(header.parent, widget.parent)
    }

    @Test
    fun aLoadingViewTakesTheIndicatorsPlaceBelowTheWidget() = onHost { host ->
        val loadingView = TextView(host).apply { text = "Loading" }
        var loaded = false
        val (dialog, _) = showDialog(host, mode = null) { dialog ->
            dialog.loadingView = loadingView
            dialog.listener = object : CaptchaFoxListener {
                override fun onLoad() { loaded = true }
            }
        }

        assertNull(dialog.loadingIndicator.parent)
        assertTrue(loadingView.indexInParent() < (dialog.widget!!.parent as View).indexInParent())
        assertEquals(View.VISIBLE, loadingView.visibility)

        until { loaded }
        assertEquals(View.GONE, loadingView.visibility)
    }

    @Test
    fun dismissesTheDelayAfterVerify() = onHost { host ->
        var verifiedAt = 0L
        var finishedAt = 0L
        val (dialog, harness) = showDialog(host, mode = null) { dialog ->
            dialog.dismissDelayAfterVerifyMillis = 300
            dialog.listener = object : CaptchaFoxListener {
                override fun onVerify(token: String) { verifiedAt = SystemClock.uptimeMillis() }
            }
            dialog.onFinish = { finishedAt = SystemClock.uptimeMillis() }
        }
        until { dialog.widget!!.alpha == 1f }

        harness.widgetCalls("onVerify", "token-1")
        until { finishedAt != 0L }

        assertTrue(verifiedAt != 0L)
        assertTrue(finishedAt - verifiedAt >= 300)
    }

    @Test
    fun aCloseDuringTheDelayIsPassedOnButFinishesNothing() = onHost { host ->
        val calls = mutableListOf<String>()
        val (dialog, harness) = showDialog(host, mode = null) { dialog ->
            // Longer than the test runs, so that only a close could finish the dialog.
            dialog.dismissDelayAfterVerifyMillis = 3_600_000
            dialog.listener = object : CaptchaFoxListener {
                override fun onVerify(token: String) { calls += "onVerify" }
                override fun onClose() { calls += "onClose" }
            }
            dialog.onFinish = { calls += "finish" }
        }
        until { dialog.widget!!.alpha == 1f }

        harness.widgetCalls("onVerify", "token-1")
        harness.widgetCalls("onClose")
        until { "onClose" in calls }

        assertEquals(listOf("onVerify", "onClose"), calls)
        assertTrue(dialog.isShowing)
        dialog.dismiss()
    }

    @Test
    fun theWidgetShowsNoIndicatorOfItsOwn() = onHost { host ->
        val (dialog, _) = showDialog(host, mode = null)

        assertFalse(dialog.widget!!.showsLoadingIndicator)
    }

    private suspend fun assertFinishesOn(host: HostActivity, callback: String, vararg arguments: String) {
        val calls = mutableListOf<String>()
        val (dialog, harness) = showDialog(host, mode = null) { dialog ->
            dialog.listener = object : CaptchaFoxListener {
                override fun onLoad() { calls += "onLoad" }
                override fun onFail() { calls += "onFail" }
                override fun onExpire() { calls += "onExpire" }
                override fun onVerify(token: String) { calls += "onVerify" }
                override fun onError(message: String) { calls += "onError" }
                override fun onClose() { calls += "onClose" }
            }
            dialog.onFinish = { calls += "finish" }
        }
        until { calls == listOf("onLoad") }

        harness.widgetCalls("onFail")
        harness.widgetCalls("onExpire")
        // Twice in one turn: the dismissed dialog has destroyed the widget before the second call arrives.
        val call = "window.fake.options[${JSONObject.quote(callback)}].apply(null, ${JSONArray(arguments.toList())});"
        harness.page(call + call)
        until { "finish" in calls }
        runPostedWork()

        assertEquals(listOf("onLoad", "onFail", "onExpire", callback, "finish"), calls)
        assertFalse(dialog.isShowing)
        assertTrue(harness.bridge.isDestroyed)
    }

    /** A shown dialog whose widget runs on the harness's page; the harness's own events stop, the dialog's listener reports them. */
    private fun showDialog(
        host: HostActivity,
        mode: CaptchaFoxConfiguration.Mode?,
        beforeShow: (CaptchaFoxDialog) -> Unit = {},
    ): Pair<CaptchaFoxDialog, WidgetHarness> {
        val harness = WidgetHarness(host, WidgetHarness.configuration(mode = mode), attached = false)
        val dialog = CaptchaFoxDialog(host, mode) { CaptchaFoxView(it, harness.bridge) }
        dialog.dismissDelayAfterVerifyMillis = 0
        beforeShow(dialog)
        dialog.show()
        return dialog to harness
    }

    /** Waits until the main thread has run what the dialog posted before, such as the end of its execution. */
    private suspend fun runPostedWork() {
        repeat(2) {
            suspendCancellableCoroutine { continuation -> Handler(Looper.getMainLooper()).post { continuation.resume(Unit) } }
        }
    }
}
