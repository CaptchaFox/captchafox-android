package com.captchafox.android

import android.view.View
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewTest {
    @Test
    fun showsAnIndicatorUntilTheWidgetIsLoaded() = onHost { host ->
        val view = CaptchaFoxView(host, WidgetHarness(host, attached = false).bridge)
        host.show(view)
        var loaded = false
        view.listener = object : CaptchaFoxListener {
            override fun onLoad() { loaded = true }
        }

        assertEquals(View.VISIBLE, view.loadingIndicator.visibility)
        until { loaded }
        assertEquals(View.GONE, view.loadingIndicator.visibility)
    }

    @Test
    fun showsNoIndicatorWhenTurnedOff() = onHost { host ->
        val view = CaptchaFoxView(host, WidgetHarness(host, attached = false).bridge)

        view.showsLoadingIndicator = false

        assertEquals(View.GONE, view.loadingIndicator.visibility)
    }

    @Test
    fun stopsTheIndicatorWhenTheWidgetCannotLoad() = onHost { host ->
        val harness = WidgetHarness(host, scriptUrl = "unreachable://cdn.example/api.js", attached = false)
        val view = CaptchaFoxView(host, harness.bridge)
        host.show(view)
        var failed = false
        view.listener = object : CaptchaFoxListener {
            override fun onError(message: String) { failed = true }
        }

        until { failed }
        assertEquals(View.GONE, view.loadingIndicator.visibility)
    }

    @Test
    fun destroyEndsTheWidget() = onHost { host ->
        val harness = WidgetHarness(host, attached = false)
        val view = CaptchaFoxView(host, harness.bridge)
        host.show(view)
        var loaded = false
        view.listener = object : CaptchaFoxListener {
            override fun onLoad() { loaded = true }
        }
        until { loaded }
        val execution = harness.startExecution()
        val webView = harness.bridge.webView

        view.destroy()

        until { execution.result != null }
        assertTrue(execution.result!!.exceptionOrNull() is CancellationException)
        assertNull(webView.parent)
        assertTrue(harness.bridge.isDestroyed)
        assertNotLoaded(harness.bridge)
    }
}
