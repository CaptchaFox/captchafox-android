package com.captchafox.android

import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteTest {
    @Test
    fun returnsTheTokenOnVerify() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val execution = harness.startExecution()

        harness.widgetCalls("onVerify", "token-1")

        assertEquals("token-1", execution.token())
    }

    @Test
    fun throwsTheWidgetsErrorOnError() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val execution = harness.startExecution()

        harness.widgetCalls("onError", "Something failed")

        assertFailure(execution, CaptchaFoxException.Code.WIDGET, "Something failed")
    }

    @Test
    fun throwsWhenTheWidgetRejectsTheExecution() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val execution = harness.startExecution()

        harness.page("window.fake.rejectExecution(new Error('Rejected'))")

        assertFailure(execution, CaptchaFoxException.Code.WIDGET, "Rejected")
        // The widget reports the failure with its error callback as well; the rejection is no second event.
        assertEquals(listOf(WidgetEvent.Load), harness.events)
    }

    @Test
    fun throwsClosedOnClose() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val execution = harness.startExecution()

        harness.widgetCalls("onClose")

        assertFailure(execution, CaptchaFoxException.Code.CLOSED, null)
    }

    @Test
    fun otherEventsLeaveItPending() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val execution = harness.startExecution()

        harness.widgetCalls("onChallengeOpen")
        harness.widgetCalls("onFail")
        harness.widgetCalls("onExpire")
        until { harness.events.last() == WidgetEvent.Expire }
        assertNull(execution.result)
        harness.widgetCalls("onVerify", "token-1")

        assertEquals("token-1", execution.token())
        assertEquals(
            listOf(WidgetEvent.Load, WidgetEvent.ChallengeOpen, WidgetEvent.Fail, WidgetEvent.Expire, WidgetEvent.Verify("token-1")),
            harness.events,
        )
    }

    @Test
    fun aSecondCallSettlesThePendingOne() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val first = harness.startExecution()
        val second = harness.startExecution()

        assertFailure(first, CaptchaFoxException.Code.SUPERSEDED, null)
        harness.widgetCalls("onVerify", "token-2")
        assertEquals("token-2", second.token())
    }

    @Test
    fun aLateFailureOfAnEarlierCallLeavesTheNewOnePending() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val first = harness.startExecution()
        val second = harness.startExecution()
        assertFailure(first, CaptchaFoxException.Code.SUPERSEDED, null)

        harness.page("window.fake.rejections[0](new Error('Late'))")
        harness.widgetCalls("onFail")
        until { harness.events.last() == WidgetEvent.Fail }

        assertNull(second.result)
        harness.widgetCalls("onVerify", "token-2")
        assertEquals("token-2", second.token())
    }

    @Test
    fun cancellingTheCallingCoroutineSettlesIt() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val execution = harness.startExecution()

        execution.cancel()

        until { execution.result != null }
        assertTrue(execution.result!!.exceptionOrNull() is CancellationException)
        until { !harness.bridge.hasPendingExecution }
        harness.widgetCalls("onVerify", "late")
        until { harness.events.last() == WidgetEvent.Verify("late") }
        // The cancelled call is forgotten: a new call settles with the next token.
        val next = harness.startExecution()
        harness.widgetCalls("onVerify", "token-2")
        assertEquals("token-2", next.token())
    }

    @Test
    fun settlesOnlyOnce() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val execution = harness.startExecution()

        harness.widgetCalls("onVerify", "token-1")
        harness.widgetCalls("onVerify", "token-2")
        harness.widgetCalls("onError", "Something failed")
        harness.widgetCalls("onClose")
        until { harness.events.last() == WidgetEvent.Close }

        assertEquals("token-1", execution.token())
        val next = harness.startExecution()
        harness.widgetCalls("onVerify", "token-3")
        assertEquals("token-3", next.token())
    }

    @Test
    fun resetSettlesIt() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val execution = harness.startExecution()

        harness.bridge.reset()

        assertFailure(execution, CaptchaFoxException.Code.RESET, null)
        until { harness.page("return window.fake.resets") == "1" }
    }

    @Test
    fun theEndOfTheRendererSettlesItAndLoadsTheWidgetAgain() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val execution = harness.startExecution()
        val webView = harness.bridge.webView
        assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_RENDERER))
        assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE))

        assertTrue(WebViewCompat.getWebViewRenderProcess(webView)!!.terminate())

        assertFailure(execution, CaptchaFoxException.Code.WEB_CONTENT_TERMINATED, null)
        assertTrue(harness.bridge.webView !== webView)
        assertNotLoaded(harness.bridge)
        until { harness.events == listOf(WidgetEvent.Load, WidgetEvent.Load) }
        val next = harness.startExecution()
        harness.widgetCalls("onVerify", "token-after-reload")
        assertEquals("token-after-reload", next.token())
    }

    @Test
    fun throwsBeforeTheWidgetIsLoaded() = onHost { host ->
        val harness = WidgetHarness(host)

        assertNotLoaded(harness.bridge)
    }
}

internal suspend fun assertFailure(execution: Execution, code: CaptchaFoxException.Code, message: String?) {
    until { execution.result != null }
    val error = execution.result!!.exceptionOrNull()
    assertTrue("$error", error is CaptchaFoxException)
    assertEquals(code, (error as CaptchaFoxException).code)
    assertEquals(message, error.message)
}

/** Calls `execute()` at once, before the page can report a load in the meantime. */
internal suspend fun assertNotLoaded(bridge: WidgetBridge) {
    val error = runCatching { bridge.execute() }.exceptionOrNull()
    assertTrue("$error", error is CaptchaFoxException)
    assertEquals(CaptchaFoxException.Code.NOT_LOADED, (error as CaptchaFoxException).code)
}
