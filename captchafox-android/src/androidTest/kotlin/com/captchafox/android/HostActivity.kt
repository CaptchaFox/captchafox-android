package com.captchafox.android

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** The activity whose window the tests put the web view, the view and the dialog in, as an app does. */
class HostActivity : Activity() {
    lateinit var container: FrameLayout
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        container = FrameLayout(this)
        setContentView(container)
    }

    fun show(view: View) {
        container.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
}

/** Runs the test on the main thread, with a host activity that is in the foreground until the test ends. */
fun onHost(test: suspend (HostActivity) -> Unit) {
    ActivityScenario.launch(HostActivity::class.java).use { scenario ->
        lateinit var host: HostActivity
        scenario.onActivity { host = it }
        runBlocking(Dispatchers.Main) {
            test(host)
        }
    }
}
