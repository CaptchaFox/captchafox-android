package com.captchafox.android

import android.net.Uri
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {
    @Test
    fun theMainFrameDoesNotLeaveThePage() = onHost { host ->
        val harness = WidgetHarness.loaded(host)

        // A navigation that is not cancelled fails within milliseconds and shows an error page in place of the widget.
        harness.page("window.location.href = 'https://127.0.0.1:9/'")
        delay(2_000)

        assertEquals("true", harness.page("return document.getElementById('captchafox') !== null"))
    }

    @Test
    fun aFormDoesNotSubmitThePageAway() = onHost { host ->
        val harness = WidgetHarness.loaded(host)

        harness.page(
            """
            var form = document.createElement('form');
            form.method = 'post';
            form.action = 'https://127.0.0.1:9/';
            document.body.appendChild(form);
            form.submit();
            """,
        )
        delay(2_000)

        assertEquals("true", harness.page("return document.getElementById('captchafox') !== null"))
    }

    @Test
    fun aFrameThatSubmitsToTheMainFrameMakesThePageLoadAgain() = onHost { host ->
        // A frame of another origin, as the widget's challenge is, which the page's form-action does not bind. The page is
        // on the device as well, at another port, because WebView lets no page of the internet load a local frame.
        LocalServer { port ->
            """
            <!DOCTYPE html>
            <html><body style="margin: 0">
            <form method="post" action="http://127.0.0.1:$port/away" target="_top">
            <button style="position: fixed; top: 0; left: 0; width: 100%; height: 100%">Go</button>
            </form>
            </body></html>
            """
        }.use { server ->
            val harness = WidgetHarness.loaded(host, CaptchaFoxConfiguration("sk_test", "http://127.0.0.1:1/"))
            val execution = harness.startExecution()
            harness.page(
                """
                var frame = document.createElement('iframe');
                frame.src = 'http://127.0.0.1:${server.port}/frame';
                frame.style.cssText = 'position: fixed; top: 0; left: 0; width: 100%; height: 100%; border: 0;';
                frame.onload = function () { window.fake.frameLoaded = true; };
                document.body.appendChild(frame);
                """,
            )
            until { harness.page("return window.fake.frameLoaded === true") == "true" }

            tap(harness.bridge.webView)

            until { server.requests.any { it.startsWith("POST /away ") } }
            until { harness.events == listOf(WidgetEvent.Load, WidgetEvent.Load) }
            assertFailure(execution, CaptchaFoxException.Code.WIDGET, "The widget's page was left.")
            assertEquals("true", harness.page("return document.getElementById('captchafox') !== null"))
        }
    }

    @Test
    fun aTappedLinkOpensInTheSystemBrowser() = onHost { host ->
        assertTappedLinkOpens(host, target = null)
    }

    @Test
    fun aTappedLinkToANewWindowOpensInTheSystemBrowser() = onHost { host ->
        assertTappedLinkOpens(host, target = "_blank")
    }

    @Test
    fun theDecisionOnceThePageIsLoaded() = onHost { host ->
        val harness = WidgetHarness.loaded(host)
        // Main frame, URL, gesture, cancelled, opened in the browser.
        val cases = listOf(
            PolicyCase(false, "https://captchafox.com/frame", false, cancels = false, opens = false),
            PolicyCase(true, "https://app.example/", false, cancels = true, opens = false),
            PolicyCase(true, "https://elsewhere.example/", false, cancels = true, opens = false),
            PolicyCase(true, "https://captchafox.com/privacy", true, cancels = true, opens = true),
            PolicyCase(true, "HTTP://captchafox.com/terms", true, cancels = true, opens = true),
            PolicyCase(true, "tel:123", true, cancels = true, opens = false),
            PolicyCase(true, "intent://scan/#Intent;scheme=zxing;end", true, cancels = true, opens = false),
            PolicyCase(true, "javascript:alert(1)", true, cancels = true, opens = false),
        )

        for (case in cases) {
            harness.opened.clear()
            val url = Uri.parse(case.url)

            val cancels = harness.bridge.cancelsNavigation(case.isForMainFrame, url, case.hasGesture)

            assertEquals(case.toString(), case.cancels, cancels)
            assertEquals(case.toString(), if (case.opens) listOf(url) else emptyList<Uri>(), harness.opened)
        }
    }

    private suspend fun assertTappedLinkOpens(host: HostActivity, target: String?) {
        val harness = WidgetHarness.loaded(host)
        until { harness.bridge.webView.height > 0 }
        harness.page(
            """
            var link = document.createElement('a');
            link.href = 'https://captchafox.com/privacy';
            ${if (target == null) "" else "link.target = '$target';"}
            link.style.cssText = 'position: fixed; top: 0; left: 0; width: 100%; height: 100%; display: block;';
            document.body.appendChild(link);
            """,
        )

        tap(harness.bridge.webView)
        until { harness.opened.size == 1 }

        assertEquals(listOf(Uri.parse("https://captchafox.com/privacy")), harness.opened)
        assertEquals("true", harness.page("return document.getElementById('captchafox') !== null"))
    }

    private data class PolicyCase(
        val isForMainFrame: Boolean,
        val url: String,
        val hasGesture: Boolean,
        val cancels: Boolean,
        val opens: Boolean,
    )
}
