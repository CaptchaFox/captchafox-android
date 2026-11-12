package com.captchafox.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageTest {
    @Test
    fun eachEventReachesTheView() = onHost { host ->
        val harness = WidgetHarness(host, attached = false)
        val view = CaptchaFoxView(host, harness.bridge)
        host.show(view)
        val received = mutableListOf<String>()
        view.listener = object : CaptchaFoxListener {
            override fun onLoad() { received += "load" }
            override fun onVerify(token: String) { received += "verify $token" }
            override fun onError(message: String) { received += "error $message" }
            override fun onFail() { received += "fail" }
            override fun onExpire() { received += "expire" }
            override fun onClose() { received += "close" }
            override fun onChallengeOpen() { received += "challengeOpen" }
        }
        until { received == listOf("load") }

        harness.widgetCalls("onChallengeOpen")
        harness.widgetCalls("onFail")
        harness.widgetCalls("onVerify", "token-1")
        harness.widgetCalls("onExpire")
        harness.widgetCalls("onClose")
        harness.widgetCalls("onError", "Something failed")
        until { received.size == 7 }

        assertEquals(listOf("load", "challengeOpen", "fail", "verify token-1", "expire", "close", "error Something failed"), received)
    }

    @Test
    fun resetResetsTheWidget() = onHost { host ->
        val harness = WidgetHarness(host, attached = false)
        val view = CaptchaFoxView(host, harness.bridge)
        host.show(view)
        var loaded = false
        view.listener = object : CaptchaFoxListener {
            override fun onLoad() { loaded = true }
        }
        until { loaded }

        view.reset()

        until { harness.page("return window.fake.resets") == "1" }
    }

    @Test
    fun aMessageFromAFrameInsideThePageIsIgnored() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val execution = harness.startExecution()

        val received = harness.bridge.receivedMessages
        harness.page(
            """
            var frame = document.createElement('iframe');
            frame.srcdoc = '<script>window.${WidgetHarness.BRIDGE}.postMessage(JSON.stringify({ type: "verify", token: "from-frame" }));<\/script>';
            document.body.appendChild(frame);
            """,
        )
        // The bridge has the frame's message: a token from it would have arrived by now.
        until { harness.bridge.receivedMessages == received + 1 }
        harness.post("""{"type":"fail"}""")
        until { harness.events.last() == WidgetEvent.Fail }

        assertEquals(listOf(WidgetEvent.Load, WidgetEvent.Fail), harness.events)
        assertNull(execution.result)
        harness.widgetCalls("onVerify", "from-widget")
        assertEquals("from-widget", execution.token())
    }

    @Test
    fun aMessageOfNoKnownFormIsIgnored() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        val execution = harness.startExecution()

        harness.post("""{"type":"VERIFY","token":"upper-case"}""")
        harness.post("""{"type":"token","token":"unknown-type"}""")
        harness.post(""""{\"type\":\"verify\",\"token\":\"string\"}"""")
        harness.post("""{"type":"verify"}""")
        harness.post("""{"type":"verify","token":""}""")
        harness.post("""{"type":"verify","token":42}""")
        harness.post("""{"token":"no-type"}""")
        harness.post("verify")
        harness.page("window.${WidgetHarness.BRIDGE}.postMessage(new TextEncoder().encode('{\"type\":\"verify\",\"token\":\"bytes\"}').buffer)")
        harness.post("""{"type":"fail"}""")
        until { harness.events.last() == WidgetEvent.Fail }

        assertEquals(listOf(WidgetEvent.Load, WidgetEvent.Fail), harness.events)
        assertNull(execution.result)
        harness.widgetCalls("onVerify", "from-widget")
        assertEquals("from-widget", execution.token())
    }
}
