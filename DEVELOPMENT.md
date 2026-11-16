# Development

## Requirements

JDK 17 or later in `JAVA_HOME` (Android Studio's own works), and the Android SDK in `ANDROID_HOME` or as `sdk.dir` in
`local.properties`. The Gradle wrapper downloads Gradle; Gradle downloads the emulator image the tests need. CI uses
Java 21 on Ubuntu.

## Build

`./gradlew assemble` builds both libraries and the example app in `example/`; CI builds them for every push to `main`
and every pull request. `./gradlew :example:installDebug` installs the example on a connected device or a running
emulator. `./gradlew publishToMavenLocal` puts both artifacts in the local Maven repository, unsigned.

## Test

| Command | Runs |
|---|---|
| `./gradlew test` | The JVM tests of the page, its messages and the configuration. |
| `./gradlew deviceTest` | The instrumented tests of both libraries on an emulator, with a fake of the widget's script in place of cdn.captchafox.com, and the test that uses the public interface as an app does. Needs no network. CI runs it for every push to `main` and every pull request. |
| `./gradlew liveTest` | The tests against the widget of cdn.captchafox.com with the public test site keys, on the same emulator. Needs the network. |

The emulator is the Gradle managed device `testDevice` (a Pixel 6 on API 37), which Gradle creates and starts for the
tests and stops after them; it never uses another device. On the first boot of a new device, WebView may take minutes
to load its first page: the tests first load one widget page, in a new web view every two minutes for up to ten, and
then wait up to three minutes for each page. The modules run one after another, and their tests one at a time.

CI splits `deviceTest` into parallel jobs, one emulator each, listed in `.github/workflows/ci.yml`; a check there fails
when the jobs and `deviceTest` differ.
