## Unreleased

- Show the CaptchaFox widget in Android apps and hand its token to the app: `CaptchaFoxView` and `CaptchaFoxDialog`
  (`com.captchafox:captchafox-android`), `CaptchaFoxWidget` and `CaptchaFoxDialog` for Compose
  (`com.captchafox:captchafox-android-compose`); `execute()` returns the token or throws a `CaptchaFoxException` with a
  code.
- The dialog takes a backdrop colour, a header, a footer, a loading view and a delay before it closes after verify.
- The views show a progress indicator until the widget has loaded; `showsLoadingIndicator` turns it off.
- `CaptchaFoxView.destroy()` ends the widget's web view; the dialog and `CaptchaFoxWidget` end theirs themselves.
- While a challenge is open, `CaptchaFoxView` and `CaptchaFoxWidget` show it over the whole screen.
- Android 7.0 (API level 24) and later.
- An example app in `example/`.
