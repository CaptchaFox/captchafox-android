package com.captchafox.android.publicinterface

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import com.captchafox.android.CaptchaFoxConfiguration
import com.captchafox.android.CaptchaFoxDialog
import com.captchafox.android.CaptchaFoxException
import com.captchafox.android.CaptchaFoxListener
import com.captchafox.android.CaptchaFoxView
import com.captchafox.android.compose.CaptchaFoxWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import com.captchafox.android.compose.CaptchaFoxDialog as CaptchaFoxDialogComposable

/** Uses the artifacts only as an app does. A change that breaks an app's code breaks the build of this module. */
class PublicInterfaceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val configuration = CaptchaFoxConfiguration(
        siteKey = "sk_11111111000000001111111100000000",
        baseUrl = "https://example.com/",
        mode = CaptchaFoxConfiguration.Mode.HIDDEN,
        theme = CaptchaFoxConfiguration.Theme.DARK,
        language = "de",
    )

    @Test
    fun configurationOptions() {
        val configuration = CaptchaFoxConfiguration("sk_11111111000000001111111100000000", "https://example.com/")
        assertNull(configuration.mode)
        assertNull(configuration.theme)
        assertNull(configuration.language)
        assertEquals("sk_11111111000000001111111100000000", configuration.siteKey)
        assertEquals("https://example.com/", configuration.baseUrl)

        val same = CaptchaFoxConfiguration(
            "sk_11111111000000001111111100000000",
            "https://example.com/",
            CaptchaFoxConfiguration.Mode.HIDDEN,
            CaptchaFoxConfiguration.Theme.DARK,
            "de",
        )
        assertEquals(this.configuration, same)
        assertEquals(setOf(this.configuration, same, configuration).size, 2)
        assertNotEquals(this.configuration, configuration)
    }

    @Test
    fun modesAndThemes() {
        val modes = listOf(CaptchaFoxConfiguration.Mode.INLINE, CaptchaFoxConfiguration.Mode.POPUP, CaptchaFoxConfiguration.Mode.HIDDEN)
        assertEquals(listOf("inline", "popup", "hidden"), modes.map { it.value })
        assertEquals(CaptchaFoxConfiguration.Mode.HIDDEN, CaptchaFoxConfiguration.Mode("hidden"))

        val themes = listOf(CaptchaFoxConfiguration.Theme.LIGHT, CaptchaFoxConfiguration.Theme.DARK)
        assertEquals(listOf("light", "dark"), themes.map { it.value })
        assertEquals(CaptchaFoxConfiguration.Theme.DARK, CaptchaFoxConfiguration.Theme("dark"))

        val handled = modes.map { mode ->
            when (mode) {
                CaptchaFoxConfiguration.Mode.HIDDEN -> "hidden"
                else -> "shown"
            }
        }
        assertEquals(listOf("shown", "shown", "hidden"), handled)
    }

    @Test
    fun view() = onMain {
        val widget = CaptchaFoxView(context, configuration)
        val asView: FrameLayout = widget
        assertTrue(asView.childCount > 0)
        assertTrue(widget.showsLoadingIndicator)
        widget.showsLoadingIndicator = false

        assertNull(widget.listener)
        widget.listener = object : CaptchaFoxListener {}
        widget.listener = allCallbacks()
        widget.reset()
        try {
            val token: String = widget.execute()
            fail("execute() returned $token before the widget was loaded")
        } catch (error: CaptchaFoxException) {
            assertEquals(CaptchaFoxException.Code.NOT_LOADED, error.code)
        }
        // In the turn of the main thread that created it, before the widget's page loads anything.
        widget.destroy()
    }

    @Test
    fun dialog() = onMain {
        val captcha = CaptchaFoxDialog(context, configuration)
        val asDialog: Dialog = captcha

        assertNull(captcha.listener)
        captcha.listener = allCallbacks()
        assertEquals(Color.argb(153, 0, 0, 0), captcha.backdropColor)
        assertTrue(captcha.headerView == null && captcha.footerView == null && captcha.loadingView == null)
        captcha.backdropColor = Color.BLACK
        captcha.headerView = TextView(context)
        captcha.footerView = TextView(context)
        captcha.loadingView = ProgressBar(context)
        assertEquals(500L, captcha.dismissDelayAfterVerifyMillis)
        captcha.dismissDelayAfterVerifyMillis = 0
        assertFalse(asDialog.isShowing)
    }

    @Test
    fun errors() {
        val codes = listOf(
            CaptchaFoxException.Code.NOT_LOADED,
            CaptchaFoxException.Code.WIDGET,
            CaptchaFoxException.Code.CLOSED,
            CaptchaFoxException.Code.SUPERSEDED,
            CaptchaFoxException.Code.RESET,
            CaptchaFoxException.Code.WEB_CONTENT_TERMINATED,
        )
        assertEquals(listOf("notLoaded", "widget", "closed", "superseded", "reset", "webContentTerminated"), codes.map { it.value })
        assertEquals(CaptchaFoxException.Code.CLOSED, CaptchaFoxException.Code("closed"))

        val error = CaptchaFoxException(CaptchaFoxException.Code.WIDGET, "message")
        val asException: Exception = error
        assertEquals(CaptchaFoxException.Code.WIDGET, error.code)
        assertEquals("message", asException.message)
        assertNull(CaptchaFoxException(CaptchaFoxException.Code.CLOSED).message)

        val handled = codes.map { code ->
            when (code) {
                CaptchaFoxException.Code.CLOSED -> "closed"
                else -> "other"
            }
        }
        assertEquals(1, handled.count { it == "closed" })
    }

    private fun allCallbacks(): CaptchaFoxListener = object : CaptchaFoxListener {
        override fun onLoad() {}
        override fun onVerify(token: String) {}
        override fun onError(message: String) {}
        override fun onFail() {}
        override fun onExpire() {}
        override fun onClose() {}
        override fun onChallengeOpen() {}
    }

    private fun onMain(test: suspend () -> Unit) = runBlocking(Dispatchers.Main) { test() }
}

/** Compiled, never run: the Compose interface as an app uses it. */
@Composable
@Suppress("unused")
private fun ComposeUsage(configuration: CaptchaFoxConfiguration, shows: Boolean, onDismissRequest: () -> Unit) {
    CaptchaFoxWidget(configuration)
    CaptchaFoxWidget(configuration, Modifier, showsLoadingIndicator = false)
    CaptchaFoxWidget(
        configuration = configuration,
        modifier = Modifier,
        showsLoadingIndicator = true,
        onLoad = {},
        onVerify = { _: String -> },
        onError = { _: String -> },
        onFail = {},
        onExpire = {},
        onClose = {},
        onChallengeOpen = {},
    )
    if (shows) {
        CaptchaFoxDialogComposable(configuration, onDismissRequest)
        CaptchaFoxDialogComposable(
            configuration = configuration,
            onDismissRequest = onDismissRequest,
            backdropColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f),
            dismissDelayAfterVerifyMillis = 1_000,
            header = { Text("Verify") },
            footer = { Text("Protected by CaptchaFox") },
            loading = { CircularProgressIndicator() },
            onLoad = {},
            onVerify = { _: String -> },
            onError = { _: String -> },
            onFail = {},
            onExpire = {},
            onClose = {},
            onChallengeOpen = {},
        )
    }
}

