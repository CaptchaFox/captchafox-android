package com.captchafox.android.compose

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

/**
 * Stops the widget's page in every web view in the same turn of the main thread in which it started to load, so that it
 * never commits: the tests drive the widget's callbacks themselves, without the network. Compose it after the function
 * under test.
 */
@Composable
fun WithoutWidgetPages() {
    SideEffect {
        for (webView in viewsInAllWindows<WebView>()) {
            webView.stopLoading()
        }
    }
}

/**
 * Lets the widget's page load, but not its script, in every web view: the page reports an error and then the messages
 * the test posts in its name. Compose it after the function under test.
 */
@Composable
fun WithoutNetwork() {
    SideEffect {
        for (webView in viewsInAllWindows<WebView>()) {
            webView.settings.blockNetworkLoads = true
        }
    }
}

/** The views of the given class in every window of the app, such as a dialog's. */
inline fun <reified T : View> viewsInAllWindows(): List<T> =
    WindowInspector.getGlobalWindowViews().flatMap { it.descendants() }.filterIsInstance<T>()

fun View.descendants(): List<View> =
    if (this is ViewGroup) listOf(this) + (0 until childCount).flatMap { getChildAt(it).descendants() } else listOf(this)
