# CaptchaFox for Android

Official [CaptchaFox](https://captchafox.com) integration for Android. It shows the CaptchaFox widget in Android apps and generates a token for backend verification.

## Compatibility

| Android   | API level | Library |
| --------- | --------- | ------- |
| 7.0 to 17 | 24 to 37  | 1.x     |

Requires Android 7.0 (API level 24) or later, with an Android System WebView that supports web message listeners. On a
WebView without them, the widget reports an error instead of loading.

## Installation

Add the library to your app module's `build.gradle.kts`; the Compose artifact brings the view and the dialog as well:

```kotlin
dependencies {
    // Views and the dialog
    implementation("com.captchafox:captchafox-android:1.0.0")
    // Jetpack Compose
    implementation("com.captchafox:captchafox-android-compose:1.0.0")
}
```

Both are published to Maven Central. `captchafox-android` depends on `androidx.webkit` and `kotlinx-coroutines-core`,
`captchafox-android-compose` on it and Compose UI 1.12.1 or later; it brings no Compose BOM, so your app keeps its own
Compose versions where they are newer. The library adds the `INTERNET` permission to your app.

## Configuration

### Get your keys

In the [CaptchaFox portal](https://portal.captchafox.com), create a site with the **Android** integration, open it
under **Sites > Site Details** and copy the **site key** and the **domain**. The **secret key** of the site belongs on
your backend, never in the app.

### Set up the widget

```kotlin
import com.captchafox.android.CaptchaFoxConfiguration

val configuration = CaptchaFoxConfiguration(
    siteKey = "YOUR_SITE_KEY",
    baseUrl = "https://YOUR_DOMAIN",
)
```

### Options

| Option     | Default      | Meaning                                                                              |
| ---------- | ------------ | ------------------------------------------------------------------------------------ |
| `siteKey`  |              | The site key.                                                                        |
| `baseUrl`  |              | The URL the widget is loaded with. Its host must be the site's domain in the portal. |
| `mode`     | the widget's | `Mode.INLINE`, `Mode.POPUP` or `Mode.HIDDEN`. Hidden stays invisible until a challenge opens. |
| `theme`    | `Theme.LIGHT` | `Theme.LIGHT` or `Theme.DARK`.                                                     |
| `language` | detected     | A language code, such as `en` or `de`.                                               |

Options left `null` use the widget's default. A `baseUrl` that is no `http` or `https` URL with a host throws
`IllegalArgumentException`. An internationalized domain such as `bücher.de` works; a host with `_` does not.

## Usage

Send the token to your backend with the request it protects. The backend verifies it with the site's secret key (see
[Verify the Response Token on the Server](https://docs.captchafox.com/getting-started#verify-the-response-token-on-the-server))
and processes the request only if the verification passes.

[`example/`](example) holds a Compose app that uses the library with the public test keys or your own site key.
`./gradlew :example:installDebug` installs it on a connected device or a running emulator.

### Dialog

Shows the widget over the current screen and closes on verify, error or close. In hidden mode it starts the
verification itself and only appears if a challenge opens.

Views:

```kotlin
val captcha = CaptchaFoxDialog(activity, configuration)
captcha.listener = object : CaptchaFoxListener {
    override fun onVerify(token: String) {
        // Send the token to your backend.
    }

    override fun onError(message: String) {
        // Show the error or let the user retry.
    }
}
captcha.show()
```

Compose:

```kotlin
var showsCaptcha by remember { mutableStateOf(false) }

Button(onClick = { showsCaptcha = true }) { Text("Sign up") }
if (showsCaptcha) {
    CaptchaFoxDialog(
        configuration = configuration,
        onDismissRequest = { showsCaptcha = false },
        onVerify = { token ->
            // Send the token to your backend.
        },
    )
}
```

The dialog takes a backdrop colour, a header above and a footer below the widget, and a view in place of its loading
indicator. With views, set them before showing it:

```kotlin
captcha.backdropColor = Color.argb(217, 0, 0, 0)
captcha.headerView = titleView
captcha.footerView = footerView
captcha.loadingView = loadingView
```

In Compose:

```kotlin
CaptchaFoxDialog(
    configuration = configuration,
    onDismissRequest = { showsCaptcha = false },
    backdropColor = Color.Black.copy(alpha = 0.85f),
    header = { Text("Verify you are human", color = Color.White, modifier = Modifier.padding(16.dp)) },
    footer = { Text("Protected by CaptchaFox", color = Color.White, modifier = Modifier.padding(16.dp)) },
    loading = { CircularProgressIndicator() },
    onVerify = { token ->
        // Send the token to your backend.
    },
)
```

The backdrop is black at 60 % opacity by default, and the loading view a progress indicator. A dismissed dialog has
destroyed its widget; create a new one each time. The dialog also dismisses itself when its activity is destroyed, as on
a rotation.

After verify, the dialog stays for 500 milliseconds so that the user sees the widget's success state. Change it with
`dismissDelayAfterVerifyMillis` (views) or the parameter of the same name (Compose); `0` closes the dialog at once.
`onVerify` is called at once either way. Back closes the dialog as well; in Compose, it calls `onDismissRequest`.

### View

`CaptchaFoxView` (views) and `CaptchaFoxWidget` (Compose) put the widget in your layout; give them a height, such as
80 dp. `CaptchaFoxWidget` supports inline and popup; for hidden mode in Compose, use `CaptchaFoxDialog`. Both show a
progress indicator until the widget has loaded; turn it off with `showsLoadingIndicator = false`. `CaptchaFoxView` also
has `execute()`, a suspending function that returns the token, `reset()` and `destroy()`; call them on the main thread.
Call `destroy()` when you no longer show the view, such as in your activity's `onDestroy`; `CaptchaFoxWidget` does it
itself when it leaves the composition. While a challenge is open, both show it over the whole screen and keep their own
size; back closes the challenge.

```kotlin
val captcha = CaptchaFoxView(context, configuration)
captcha.listener = object : CaptchaFoxListener {
    override fun onLoad() {
        // From now on execute() works.
    }
}
container.addView(captcha)

lifecycleScope.launch {
    try {
        val token = captcha.execute()
        // Send the token to your backend.
    } catch (error: CaptchaFoxException) {
        if (error.code == CaptchaFoxException.Code.CLOSED) {
            // The user closed the challenge.
        }
    }
}
```

`execute()` returns the token or throws a `CaptchaFoxException` with one of these codes:

| Code                     | When                                                                          |
| ------------------------ | ----------------------------------------------------------------------------- |
| `WIDGET`                 | The widget reported an error, see `message`.                                  |
| `CLOSED`                 | The user closed the challenge.                                                |
| `SUPERSEDED`             | `execute()` was called again.                                                 |
| `RESET`                  | `reset()` was called.                                                         |
| `NOT_LOADED`             | It was called before `onLoad`, for example because the script failed to load. |
| `WEB_CONTENT_TERMINATED` | Android ended the WebView's renderer; the widget reloads.                     |

If the calling coroutine is cancelled or the view destroyed, it throws `CancellationException`. Minor versions may add
codes, so a `when` over `code` needs an `else` branch.

### Callbacks

The dialog and the view take a `CaptchaFoxListener`, whose methods you override as needed; the Compose functions take
the same callbacks as parameters. They are called on the main thread:

| Callback           | Called when                                          |
| ------------------ | ---------------------------------------------------- |
| `onLoad`           | The widget is rendered.                              |
| `onVerify(token)`  | The user is verified.                                |
| `onError(message)` | An error occurs, for example the widget cannot load. |
| `onFail`           | The user failed the challenge.                       |
| `onExpire`         | The token expired before it was used.                |
| `onClose`          | The user closed the challenge.                       |
| `onChallengeOpen`  | A challenge opens, also in hidden mode.              |

## Operation

### If CaptchaFox is unreachable

If the widget's script cannot be loaded from cdn.captchafox.com, or the widget cannot reach CaptchaFox, you get
`onError` or `execute()` throws (`NOT_LOADED` or `WIDGET`), and the dialog closes. Without a token, your backend decides
whether the request goes through.

### Log

The library logs nothing.

### Links in the widget

The WebView shows only the widget. Links tapped in it open in the browser.

### Privacy

CaptchaFox is operated by Scoria Labs GmbH, Germany. How CaptchaFox processes data and how to get a data processing
agreement is described in the [CaptchaFox Privacy Center](https://captchafox.com/privacy). A suggested paragraph for
your privacy policy is on the [GDPR page](https://captchafox.com/privacy/gdpr).

The library itself holds only the configuration you pass, sends nothing on its own and hands the token only to your
app. The widget it shows in a WebView loads from cdn.captchafox.com and talks to CaptchaFox; what it collects is
described in the Privacy Center. Use that for your app's data safety form in Google Play.

## License

MIT. See [LICENSE](LICENSE).
