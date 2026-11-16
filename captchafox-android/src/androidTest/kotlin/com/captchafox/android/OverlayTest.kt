package com.captchafox.android

import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTest {
    @Test
    fun aChallengeShowsOverTheWholeScreenWhileTheViewKeepsItsSize() = onHost { host ->
        val (view, harness) = shownView(host)
        val height = view.height

        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true }

        val overlay = view.overlay!!
        assertSame(overlay.content, harness.bridge.webView.parent)
        until { harness.bridge.webView.height > height }
        assertEquals(height, view.height)

        harness.widgetCalls("onVerify", "token-1")
        until { view.overlay == null }

        assertSame(view, harness.bridge.webView.parent)
        assertFalse(overlay.isShowing)
        until { view.height == height && harness.bridge.webView.height == height }
    }

    @Test
    fun theWebViewReturnsWhenTheChallengeEnds() = onHost { host ->
        val (view, harness) = shownView(host)
        val endings = listOf(listOf("onVerify", "token-1"), listOf("onError", "Failed"), listOf("onFail"), listOf("onClose"), listOf("onExpire"))

        for (ending in endings) {
            harness.widgetCalls("onChallengeOpen")
            until { view.overlay?.isShowing == true }

            harness.widgetCalls(ending.first(), *ending.drop(1).toTypedArray())

            until { view.overlay == null }
            assertSame(ending.first(), view, harness.bridge.webView.parent)
        }
    }

    @Test
    fun afterVerifyTheChallengeStaysASecondUnlessItOpensAgain() = onHost { host ->
        val (view, harness) = shownView(host)
        var verifiedAt = 0L
        view.listener = object : CaptchaFoxListener {
            override fun onVerify(token: String) { verifiedAt = SystemClock.uptimeMillis() }
        }
        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true }

        harness.widgetCalls("onVerify", "token-1")
        until { verifiedAt != 0L }
        assertTrue(view.overlay?.isShowing == true)
        until { view.overlay == null }
        assertTrue(SystemClock.uptimeMillis() - verifiedAt >= 1_000)

        // A challenge that opens again within the second keeps the overlay.
        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true }
        harness.page("window.fake.options.onVerify('token-2'); window.fake.options.onChallengeOpen();")
        delay(1_500)
        assertTrue(view.overlay?.isShowing == true)
    }

    @Test
    fun theWebViewReturnsOnResetAndDestroy() = onHost { host ->
        val (view, harness) = shownView(host)
        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true }
        val overlay = view.overlay!!

        view.reset()

        assertNull(view.overlay)
        assertFalse(overlay.isShowing)
        assertSame(view, harness.bridge.webView.parent)

        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true }
        val webView = harness.bridge.webView

        view.destroy()

        assertNull(view.overlay)
        assertNull(webView.parent)
        assertTrue(harness.bridge.isDestroyed)
    }

    @Test
    fun theWebViewReturnsWhenThePageIsLeft() = onHost { host ->
        LocalServer { "<!DOCTYPE html><html><body>away</body></html>" }.use { server ->
            val (view, harness) = shownView(host)
            harness.widgetCalls("onChallengeOpen")
            until { view.overlay?.isShowing == true }

            // A main-frame navigation the web view is not asked about, as a form that a frame submits to the page.
            harness.bridge.webView.loadUrl("http://127.0.0.1:${server.port}/away")

            until { view.overlay == null }
            assertSame(view, harness.bridge.webView.parent)
        }
    }

    @Test
    fun aNewWebViewAfterTheEndOfTheRendererShowsInTheView() = onHost { host ->
        val (view, harness) = shownView(host)
        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true }
        val webView = harness.bridge.webView

        assertTrue(WebViewCompat.getWebViewRenderProcess(webView)!!.terminate())

        until { harness.bridge.webView !== webView }
        assertNull(view.overlay)
        assertSame(view, harness.bridge.webView.parent)
        assertNotSame(view, webView.parent)
    }

    @Test
    fun backClosesTheChallenge() = onHost { host ->
        val (view, harness) = shownView(host)
        var closed = false
        view.listener = object : CaptchaFoxListener {
            override fun onClose() { closed = true }
        }
        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true }
        waitForFocus(harness.bridge.webView)

        withContext(Dispatchers.IO) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK) }

        until { closed }
        until { view.overlay == null }
        assertSame(view, harness.bridge.webView.parent)
    }

    @Test
    fun anOverlayWhoseActivityIsDestroyedEndsWithIt() {
        lateinit var harness: WidgetHarness
        lateinit var view: CaptchaFoxView
        val scenario = ActivityScenario.launch(HostActivity::class.java)
        scenario.onActivity { host ->
            harness = WidgetHarness(host, attached = false)
            view = CaptchaFoxView(host, harness.bridge)
            host.container.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, 300))
        }
        runBlocking(Dispatchers.Main) {
            until { harness.page("return window.fake && window.fake.options !== undefined") == "true" }
            harness.widgetCalls("onChallengeOpen")
            until { view.overlay?.isShowing == true }
        }
        val overlay = runBlocking(Dispatchers.Main) { view.overlay!! }

        // As a configuration change does to an app that leaves the view to itself.
        scenario.close()

        runBlocking(Dispatchers.Main) {
            assertFalse(overlay.isShowing)
            assertNull(view.overlay)
            assertSame(view, harness.bridge.webView.parent)
        }
    }

    @Test
    fun escapeClosesTheChallengeAndNothingElseEndsTheOverlayBehindTheView() = onHost { host ->
        val (view, harness) = shownView(host)
        var closed = false
        view.listener = object : CaptchaFoxListener {
            override fun onClose() { closed = true }
        }
        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true }
        waitForFocus(harness.bridge.webView)

        // A hardware Escape key, which cancels a cancelable dialog.
        withContext(Dispatchers.IO) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE) }

        until { closed && view.overlay == null }
        assertSame(view, harness.bridge.webView.parent)

        // Dismissed by anything else, the overlay gives the web view back, and the next challenge opens it again.
        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true }
        view.overlay!!.dismiss()
        until { view.overlay == null }
        assertSame(view, harness.bridge.webView.parent)
        harness.widgetCalls("onClose")
        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true }
    }

    @Test
    fun aViewOfARecreatedActivityKeepsTheChallenge() {
        lateinit var harness: WidgetHarness
        lateinit var view: CaptchaFoxView
        ActivityScenario.launch(HostActivity::class.java).use { scenario ->
            scenario.onActivity { old ->
                harness = WidgetHarness(old, attached = false)
                view = CaptchaFoxView(old, harness.bridge)
            }

            // An app that keeps the view across a recreation, as for a configuration change.
            scenario.recreate()
            scenario.onActivity { new -> new.container.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, 300)) }

            runBlocking(Dispatchers.Main) {
                var challengeOpened = false
                view.listener = object : CaptchaFoxListener {
                    override fun onChallengeOpen() { challengeOpened = true }
                }
                until { view.isAttachedToWindow && harness.page("return window.fake && window.fake.options !== undefined") == "true" }
                harness.widgetCalls("onChallengeOpen")
                until { challengeOpened }

                // The destroyed activity can show no window; the challenge stayed in the view.
                assertNull(view.overlay)
                assertSame(view, harness.bridge.webView.parent)
            }
        }
    }

    @Test
    fun theViewKeepsItsSizeUnderAWrappingParentAndGetsItsOwnMinimumBack() = onHost { host ->
        val harness = WidgetHarness(host, attached = false)
        val view = CaptchaFoxView(host, harness.bridge)
        view.minimumHeight = 7
        val column = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        column.addView(view, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        host.container.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        until { harness.page("return window.fake && window.fake.options !== undefined") == "true" }
        // A page as high as its content, so that the web view sets the view's height.
        harness.page(
            "document.documentElement.style.height = 'auto'; document.body.style.height = 'auto'; " +
                "document.getElementById('captchafox').style.height = '120px';",
        )
        until { view.height > 7 }
        val height = view.height

        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true && !view.isLayoutRequested }

        assertEquals(height, view.height)
        harness.widgetCalls("onClose")
        until { view.overlay == null }
        assertEquals(7, view.minimumHeight)
    }

    @Test
    fun aViewThatLeavesItsWindowTakesItsWebViewBackAndShowsTheChallengeAgainWhenItReturns() = onHost { host ->
        val (view, harness) = shownView(host)
        harness.widgetCalls("onChallengeOpen")
        until { view.overlay?.isShowing == true }
        val overlay = view.overlay!!

        host.container.removeView(view)

        assertNull(view.overlay)
        assertFalse(overlay.isShowing)
        assertSame(view, harness.bridge.webView.parent)

        host.container.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, 300))
        until { view.overlay?.isShowing == true }

        harness.widgetCalls("onClose")
        until { view.overlay == null }
        host.container.removeView(view)
        host.container.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, 300))
        until { view.isAttachedToWindow }
        assertNull(view.overlay)
    }

    /** A view of a fixed size in the host's window, whose widget has loaded. */
    private suspend fun shownView(host: HostActivity): Pair<CaptchaFoxView, WidgetHarness> {
        val harness = WidgetHarness(host, attached = false)
        val view = CaptchaFoxView(host, harness.bridge)
        host.container.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, 300))
        until { harness.page("return window.fake && window.fake.options !== undefined") == "true" && view.height == 300 }
        return view to harness
    }

    /** Waits until the web view's window has the focus, which a system app that is not responding may hold. */
    private suspend fun waitForFocus(webView: WebView) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        until {
            if (!webView.hasWindowFocus()) {
                withContext(Dispatchers.IO) { runCatching { device.findObject(By.res("android", "aerr_wait"))?.click() } }
            }
            webView.hasWindowFocus()
        }
    }
}
