package com.captchafox.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageTest {
    @Test
    fun theOptionsReachTheWidgetAsData() = onHost { host ->
        // `<!--<script>` puts the HTML parser into a state in which even an escaped `</script>` no longer ends the element.
        val siteKey = "sk_\"</script><!--<script>window.injected = true</script>"
        val configuration = CaptchaFoxConfiguration(
            siteKey,
            WidgetHarness.BASE_URL,
            CaptchaFoxConfiguration.Mode.HIDDEN,
            CaptchaFoxConfiguration.Theme.DARK,
            "de",
        )
        val harness = WidgetHarness.loaded(host, configuration)

        assertEquals(
            mapOf("sitekey" to siteKey, "mode" to "hidden", "theme" to "dark", "lang" to "de"),
            renderedOptions(harness),
        )
        assertEquals("\"undefined\"", harness.page("return typeof window.injected"))
    }

    @Test
    fun unsetOptionsAreLeftOut() = onHost { host ->
        val harness = WidgetHarness.loaded(host)

        assertEquals(mapOf("sitekey" to "sk_test"), renderedOptions(harness))
    }

    @Test
    fun aScriptThatDoesNotLoadIsAnError() = onHost { host ->
        val harness = WidgetHarness(host, scriptUrl = "unreachable://cdn.example/api.js")

        until { harness.events.isNotEmpty() }

        assertEquals(listOf(WidgetEvent.Error("The CaptchaFox script could not be loaded.")), harness.events)
        assertNotLoaded(harness.bridge)
    }

    @Test
    fun withoutMessagesTheWidgetIsAnErrorAndNoPageLoads() = onHost { host ->
        val harness = WidgetHarness(host, supportsMessages = { false })

        until { harness.events.isNotEmpty() }

        assertEquals(
            listOf(WidgetEvent.Error("This device's WebView is too old for CaptchaFox; update Android System WebView.")),
            harness.events,
        )
        assertNull(harness.bridge.webView.url)
        assertNotLoaded(harness.bridge)
    }

    /** The options the page passed to the widget's `render`, without the callbacks. */
    private suspend fun renderedOptions(harness: WidgetHarness): Map<String, String> {
        // The result of the page is JSON of the string that JSON.stringify returned.
        val json = JSONObject(JSONObject("{\"json\":${harness.page("return JSON.stringify(window.fake.options)")}}").getString("json"))
        return json.keys().asSequence().associateWith(json::getString)
    }
}
