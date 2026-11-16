package com.captchafox.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPageTest {
    @Test
    fun theOptionsAreJsonThatNoValueCanLeave() {
        // `<!--<script>` puts the HTML parser into a state in which even an escaped `</script>` no longer ends the element.
        val siteKey = "sk_\"</script><!--<script>window.injected = true</script>\\\n"
        val configuration = CaptchaFoxConfiguration(
            siteKey,
            "https://app.example/",
            CaptchaFoxConfiguration.Mode.HIDDEN,
            CaptchaFoxConfiguration.Theme.DARK,
            "de",
        )

        val json = WidgetPage.optionsJson(configuration)

        assertFalse(json.contains('<'))
        val options = JSONObject(json)
        assertEquals(4, options.length())
        assertEquals(siteKey, options.getString("sitekey"))
        assertEquals("hidden", options.getString("mode"))
        assertEquals("dark", options.getString("theme"))
        assertEquals("de", options.getString("lang"))
    }

    @Test
    fun unsetOptionsAreLeftOut() {
        val json = WidgetPage.optionsJson(CaptchaFoxConfiguration("sk_test", "https://app.example/"))

        assertEquals("{\"sitekey\":\"sk_test\"}", json)
    }

    @Test
    fun thePageLoadsTheScriptAndHoldsTheOptionsAsData() {
        val html = WidgetPage.html(CaptchaFoxConfiguration("sk_</script>", "https://app.example/"), "https://cdn.example/api.js?a=1&b=\"2\"")

        assertTrue(html.contains("<script async src=\"https://cdn.example/api.js?a=1&amp;b=&quot;2&quot;\" onerror=\"captchafoxScriptFailed()\"></script>"))
        assertTrue(html.contains("<script type=\"application/json\" id=\"captchafox-options\">{\"sitekey\":\"sk_\\u003c/script>\"}</script>"))
        assertTrue(html.contains("form-action 'none'"))
    }
}
