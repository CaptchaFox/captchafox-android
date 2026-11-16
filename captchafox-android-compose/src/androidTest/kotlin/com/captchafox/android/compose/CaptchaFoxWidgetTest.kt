package com.captchafox.android.compose

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.captchafox.android.CaptchaFoxConfiguration
import com.captchafox.android.CaptchaFoxView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test

class CaptchaFoxWidgetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun passesEachCallbackAndTheIndicatorSettingToTheView() {
        val received = mutableListOf<String>()
        compose.setContent {
            CaptchaFoxWidget(
                configuration = CaptchaFoxConfiguration("sk_test", "https://app.example/"),
                showsLoadingIndicator = false,
                onLoad = { received += "load" },
                onVerify = { received += "verify $it" },
                onError = { received += "error $it" },
                onFail = { received += "fail" },
                onExpire = { received += "expire" },
                onClose = { received += "close" },
                onChallengeOpen = { received += "challengeOpen" },
            )
            WithoutWidgetPages()
        }

        compose.runOnIdle {
            val view = viewsInAllWindows<CaptchaFoxView>().single()
            assertFalse(view.showsLoadingIndicator)
            val listener = view.listener!!
            listener.onLoad()
            listener.onChallengeOpen()
            listener.onFail()
            listener.onVerify("token-1")
            listener.onExpire()
            listener.onClose()
            listener.onError("Something failed")
        }

        assertEquals(listOf("load", "challengeOpen", "fail", "verify token-1", "expire", "close", "error Something failed"), received)
    }

    @Test
    fun callsTheCallbackOfTheLatestComposition() {
        var label by mutableStateOf("first")
        val received = mutableListOf<String>()
        compose.setContent {
            val current = label
            CaptchaFoxWidget(CaptchaFoxConfiguration("sk_test", "https://app.example/"), onVerify = { received += current })
            WithoutWidgetPages()
        }
        compose.runOnIdle { label = "second" }

        compose.runOnIdle { viewsInAllWindows<CaptchaFoxView>().single().listener!!.onVerify("token-1") }

        assertEquals(listOf("second"), received)
    }

    @Test
    fun aChangedConfigurationLoadsTheWidgetAnew() {
        var siteKey by mutableStateOf("sk_first")
        compose.setContent {
            CaptchaFoxWidget(CaptchaFoxConfiguration(siteKey, "https://app.example/"))
            WithoutWidgetPages()
        }
        val first = compose.runOnIdle { viewsInAllWindows<CaptchaFoxView>().single() }

        compose.runOnIdle { siteKey = "sk_second" }

        compose.runOnIdle {
            assertNotSame(first, viewsInAllWindows<CaptchaFoxView>().single())
            // The replaced view has given up its web view.
            assertEquals(emptyList<WebView>(), first.descendants().filterIsInstance<WebView>())
        }
    }

    @Test
    fun leavingTheCompositionDestroysTheWebView() {
        var shows by mutableStateOf(true)
        compose.setContent {
            if (shows) {
                CaptchaFoxWidget(CaptchaFoxConfiguration("sk_test", "https://app.example/"))
            }
            WithoutWidgetPages()
        }
        val view = compose.runOnIdle { viewsInAllWindows<CaptchaFoxView>().single() }

        compose.runOnIdle { shows = false }

        compose.runOnIdle { assertEquals(emptyList<WebView>(), view.descendants().filterIsInstance<WebView>()) }
    }

    @Test
    fun aChallengeShowsOverTheWholeScreen() {
        val received = mutableListOf<String>()
        compose.setContent {
            CaptchaFoxWidget(
                CaptchaFoxConfiguration("sk_test", "https://app.example/"),
                Modifier.height(80.dp),
                onError = { received += "error" },
                onClose = { received += "close" },
            )
            WithoutNetwork()
        }
        compose.waitUntil(180_000) { received.contains("error") }
        val view = compose.runOnIdle { viewsInAllWindows<CaptchaFoxView>().single() }
        val webView = compose.runOnIdle { view.descendants().filterIsInstance<WebView>().single() }

        // The messages of the widget, as its page posts them.
        compose.runOnIdle { webView.evaluateJavascript("window.CaptchaFoxAndroid.postMessage(JSON.stringify({ type: 'challengeOpen' }))", null) }
        compose.waitUntil(60_000) { webView.parent !== view }
        compose.runOnIdle { assertEquals(80 * view.resources.displayMetrics.density, view.height.toFloat(), 1f) }
        compose.runOnIdle { webView.evaluateJavascript("window.CaptchaFoxAndroid.postMessage(JSON.stringify({ type: 'close' }))", null) }

        compose.waitUntil(60_000) { webView.parent === view }
        compose.runOnIdle { assertEquals(listOf("error", "close"), received) }
    }
}
