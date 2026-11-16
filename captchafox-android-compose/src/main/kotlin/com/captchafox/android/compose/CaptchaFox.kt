package com.captchafox.android.compose

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.captchafox.android.CaptchaFoxConfiguration
import com.captchafox.android.CaptchaFoxListener
import com.captchafox.android.CaptchaFoxView

/**
 * The CaptchaFox widget in a composition, for inline and popup mode; for hidden mode, use [CaptchaFoxDialog]. A changed
 * configuration loads the widget anew.
 *
 * @param showsLoadingIndicator Whether a progress indicator shows until the widget is loaded or reports an error.
 * @param onVerify Called with the token, which your backend verifies.
 * @param onError Called with the widget's error message.
 * @param onFail Called when the visitor failed the challenge.
 * @param onExpire Called when the token expired before it was used.
 * @param onClose Called when the visitor closed the challenge.
 * @param onChallengeOpen Called when a challenge opens.
 */
@Composable
public fun CaptchaFoxWidget(
    configuration: CaptchaFoxConfiguration,
    modifier: Modifier = Modifier,
    showsLoadingIndicator: Boolean = true,
    onLoad: () -> Unit = {},
    onVerify: (token: String) -> Unit = {},
    onError: (message: String) -> Unit = {},
    onFail: () -> Unit = {},
    onExpire: () -> Unit = {},
    onClose: () -> Unit = {},
    onChallengeOpen: () -> Unit = {},
) {
    val listener = rememberListener(Callbacks(onLoad, onVerify, onError, onFail, onExpire, onClose, onChallengeOpen))
    key(configuration) {
        AndroidView(
            factory = { context -> CaptchaFoxView(context, configuration) },
            modifier = modifier,
            onRelease = CaptchaFoxView::destroy,
        ) { view ->
            view.showsLoadingIndicator = showsLoadingIndicator
            view.listener = listener
        }
    }
}

/**
 * Shows the CaptchaFox widget over the current screen while it is in the composition, as
 * [com.captchafox.android.CaptchaFoxDialog] does. It calls [onDismissRequest] when it dismisses itself on verify, error
 * and close, and when the visitor presses back; leave it out of the composition then.
 *
 * @param backdropColor The colour over the current screen.
 * @param dismissDelayAfterVerifyMillis How many milliseconds the dialog stays after verify, so that the visitor sees the
 *   widget's success state; 0 dismisses it at once. [onVerify] is called at once either way.
 * @param header Shown above the widget, such as a title.
 * @param footer Shown below the widget.
 * @param loading Shown in place of the progress indicator while the widget loads, and in hidden mode until the token
 *   arrives.
 * @param onVerify Called with the token, which your backend verifies.
 * @param onError Called with the widget's error message.
 * @param onFail Called when the visitor failed the challenge.
 * @param onExpire Called when the token expired before it was used.
 * @param onClose Called when the visitor closed the challenge.
 * @param onChallengeOpen Called when a challenge opens, also in hidden mode.
 */
@Composable
public fun CaptchaFoxDialog(
    configuration: CaptchaFoxConfiguration,
    onDismissRequest: () -> Unit,
    backdropColor: Color = Color.Black.copy(alpha = 0.6f),
    dismissDelayAfterVerifyMillis: Long = 500,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    loading: (@Composable () -> Unit)? = null,
    onLoad: () -> Unit = {},
    onVerify: (token: String) -> Unit = {},
    onError: (message: String) -> Unit = {},
    onFail: () -> Unit = {},
    onExpire: () -> Unit = {},
    onClose: () -> Unit = {},
    onChallengeOpen: () -> Unit = {},
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val compositionContext = rememberCompositionContext()
    val listener = rememberListener(Callbacks(onLoad, onVerify, onError, onFail, onExpire, onClose, onChallengeOpen))
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentHeader by rememberUpdatedState(header)
    val currentFooter by rememberUpdatedState(footer)
    val currentLoading by rememberUpdatedState(loading)
    val dialog = remember(configuration) { com.captchafox.android.CaptchaFoxDialog(context, configuration) }

    DisposableEffect(dialog) {
        dialog.listener = listener
        dialog.headerView = header?.let { hosted(hostView, compositionContext) { currentHeader?.invoke() } }
        dialog.footerView = footer?.let { hosted(hostView, compositionContext) { currentFooter?.invoke() } }
        dialog.loadingView = loading?.let { hosted(hostView, compositionContext) { currentLoading?.invoke() } }
        dialog.setOnDismissListener { currentOnDismissRequest() }
        dialog.show()
        onDispose {
            // Leaving the composition is no request to dismiss.
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
    SideEffect {
        dialog.backdropColor = backdropColor.toArgb()
        dialog.dismissDelayAfterVerifyMillis = dismissDelayAfterVerifyMillis
    }
}

private class Callbacks(
    val onLoad: () -> Unit,
    val onVerify: (token: String) -> Unit,
    val onError: (message: String) -> Unit,
    val onFail: () -> Unit,
    val onExpire: () -> Unit,
    val onClose: () -> Unit,
    val onChallengeOpen: () -> Unit,
)

/** A listener that calls the callbacks of the latest composition. */
@Composable
private fun rememberListener(callbacks: Callbacks): CaptchaFoxListener {
    val current = rememberUpdatedState(callbacks)
    return remember { CallbackListener(current) }
}

private class CallbackListener(private val callbacks: State<Callbacks>) : CaptchaFoxListener {
    override fun onLoad() {
        callbacks.value.onLoad()
    }

    override fun onVerify(token: String) {
        callbacks.value.onVerify(token)
    }

    override fun onError(message: String) {
        callbacks.value.onError(message)
    }

    override fun onFail() {
        callbacks.value.onFail()
    }

    override fun onExpire() {
        callbacks.value.onExpire()
    }

    override fun onClose() {
        callbacks.value.onClose()
    }

    override fun onChallengeOpen() {
        callbacks.value.onChallengeOpen()
    }
}

/**
 * A view in the dialog's window that composes [content] as part of the composition around the dialog, with its
 * lifecycle and saved state.
 */
private fun hosted(hostView: View, parent: CompositionContext, content: @Composable () -> Unit): View {
    val view = ComposeView(hostView.context)
    view.setParentCompositionContext(parent)
    view.setViewTreeLifecycleOwner(hostView.findViewTreeLifecycleOwner())
    view.setViewTreeSavedStateRegistryOwner(hostView.findViewTreeSavedStateRegistryOwner())
    view.setContent(content)
    return view
}
