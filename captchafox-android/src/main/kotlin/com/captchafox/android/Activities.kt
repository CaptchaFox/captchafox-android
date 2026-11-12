package com.captchafox.android

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle

/** The activity this context belongs to, or `null` for a context of no activity, such as the application's. */
internal fun Context.activity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()

/**
 * Calls [destroyed] when [activity] is destroyed, while its window still exists. Register it with the application while
 * a window over the activity is shown, and unregister it when that window is gone.
 */
internal class OnActivityDestroyed(
    private val activity: Activity?,
    private val destroyed: () -> Unit,
) : Application.ActivityLifecycleCallbacks {
    fun register() {
        activity?.application?.registerActivityLifecycleCallbacks(this)
    }

    fun unregister() {
        activity?.application?.unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === this.activity) {
            destroyed()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
