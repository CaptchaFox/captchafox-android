package com.captchafox.android.compose

import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.captchafox.android.CaptchaFoxConfiguration
import com.captchafox.android.CaptchaFoxView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CaptchaFoxDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val configuration = CaptchaFoxConfiguration("sk_test", "https://app.example/")

    @Test
    fun showsItsSlotsOnTheBackdropWhileComposedAndLeavesWithoutARequest() {
        var shows by mutableStateOf(true)
        var requests = 0
        compose.setContent {
            if (shows) {
                CaptchaFoxDialog(
                    configuration = configuration,
                    onDismissRequest = { requests += 1 },
                    backdropColor = Color.Red,
                    header = { Text("Header") },
                    footer = { Text("Footer") },
                    loading = { Text("Loading") },
                )
            }
            WithoutWidgetPages()
        }

        compose.onNodeWithText("Header").assertExists()
        compose.onNodeWithText("Footer").assertExists()
        compose.onNodeWithText("Loading").assertExists()
        compose.runOnIdle {
            val content = viewsInAllWindows<CaptchaFoxView>().single().parent.parent as View
            assertEquals(Color.Red.toArgb(), (content.background as ColorDrawable).color)
        }

        compose.runOnIdle { shows = false }

        compose.runOnIdle {
            assertEquals(emptyList<CaptchaFoxView>(), viewsInAllWindows<CaptchaFoxView>())
            assertEquals(0, requests)
        }
    }

    @Test
    fun passesEachCallbackAndAsksToBeDismissedAfterVerify() {
        val received = mutableListOf<String>()
        compose.setContent {
            CaptchaFoxDialog(
                configuration = configuration,
                onDismissRequest = { received += "dismissRequest" },
                dismissDelayAfterVerifyMillis = 0,
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

        val view = compose.runOnIdle { viewsInAllWindows<CaptchaFoxView>().single() }
        compose.runOnIdle {
            // The widget's callbacks, which the dialog passes on.
            val widget = view.listener!!
            widget.onLoad()
            widget.onChallengeOpen()
            widget.onFail()
            widget.onExpire()
            widget.onVerify("token-1")
        }

        compose.runOnIdle {
            assertEquals(listOf("load", "challengeOpen", "fail", "expire", "verify token-1", "dismissRequest"), received)
            // The dismissed dialog has destroyed the widget's web view.
            assertEquals(emptyList<WebView>(), view.descendants().filterIsInstance<WebView>())
        }
    }

    @Test
    fun backAsksToBeDismissed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var requests = 0
        compose.setContent {
            CaptchaFoxDialog(configuration = configuration, onDismissRequest = { requests += 1 })
            WithoutWidgetPages()
        }
        // A newly booted emulator may show that a system app is not responding; that dialog would take the key.
        val device = UiDevice.getInstance(instrumentation)
        val deadline = SystemClock.uptimeMillis() + 180_000
        while (!compose.runOnIdle { viewsInAllWindows<CaptchaFoxView>().single().hasWindowFocus() }) {
            assertTrue(SystemClock.uptimeMillis() < deadline)
            // The dialog may close by itself between finding and clicking.
            runCatching { device.findObject(By.res("android", "aerr_wait"))?.click() }
            Thread.sleep(200)
        }

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

        compose.runOnIdle { assertEquals(1, requests) }
    }
}
