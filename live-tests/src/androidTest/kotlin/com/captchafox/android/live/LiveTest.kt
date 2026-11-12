package com.captchafox.android.live

import android.app.Activity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import com.captchafox.android.CaptchaFoxConfiguration
import com.captchafox.android.CaptchaFoxException
import com.captchafox.android.CaptchaFoxListener
import com.captchafox.android.CaptchaFoxView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The widget from cdn.captchafox.com with the public test site keys, which work with any base URL. */
class LiveTest {
    @Test
    fun theTestKeyThatPassesGivesATokenWithoutInteraction() = onHost { host ->
        val (view, events) = loadedWidget(host, "sk_11111111000000001111111100000000")

        val token = withTimeout(TIMEOUT) { view.execute() }

        assertTrue(token.isNotEmpty())
        assertEquals(listOf("load", "verify"), events)
    }

    @Test
    fun theTestKeyThatFailsGivesTheWidgetsError() = onHost { host ->
        val (view, events) = loadedWidget(host, "sk_FFFFFFFF00000000FFFFFFFF00000000")

        try {
            withTimeout(TIMEOUT) { view.execute() }
            fail("execute() returned a token")
        } catch (error: CaptchaFoxException) {
            assertEquals(CaptchaFoxException.Code.WIDGET, error.code)
        }
        assertEquals(listOf("load", "fail", "error"), events)
    }

    private suspend fun loadedWidget(host: HostActivity, siteKey: String): Pair<CaptchaFoxView, List<String>> {
        val configuration = CaptchaFoxConfiguration(siteKey, "https://example.com/", CaptchaFoxConfiguration.Mode.HIDDEN)
        val view = CaptchaFoxView(host, configuration)
        host.setContentView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val events = mutableListOf<String>()
        view.listener = object : CaptchaFoxListener {
            override fun onLoad() { events += "load" }
            override fun onVerify(token: String) { events += "verify" }
            override fun onError(message: String) { events += "error" }
            override fun onFail() { events += "fail" }
            override fun onExpire() { events += "expire" }
            override fun onClose() { events += "close" }
            override fun onChallengeOpen() { events += "challengeOpen" }
        }

        withTimeout(TIMEOUT) {
            while (events.isEmpty()) {
                delay(50)
            }
        }
        assertEquals(listOf("load"), events)
        return view to events
    }

    private fun onHost(test: suspend (HostActivity) -> Unit) {
        ActivityScenario.launch(HostActivity::class.java).use { scenario ->
            lateinit var host: HostActivity
            scenario.onActivity { host = it }
            runBlocking(Dispatchers.Main) { test(host) }
        }
    }

    private companion object {
        // On the first boot of a new emulator, WebView may take minutes to load its first page.
        const val TIMEOUT = 600_000L
    }
}

/** The activity whose window shows the widget, as in an app. */
class HostActivity : Activity()
