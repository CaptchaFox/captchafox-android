package com.captchafox.android

/**
 * Why [CaptchaFoxView.execute] returned no token. A cancelled calling coroutine gets a `CancellationException` instead.
 *
 * [message] is the widget's message, where it gave one.
 */
public class CaptchaFoxException @JvmOverloads constructor(
    public val code: Code,
    message: String? = null,
) : Exception(message) {
    /** The kind of error. New codes may be added in a minor version. */
    public class Code(public val value: String) {
        override fun equals(other: Any?): Boolean = other is Code && other.value == value

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = value

        public companion object {
            /** The widget has not reported that it is loaded. */
            @JvmField
            public val NOT_LOADED: Code = Code("notLoaded")

            /** The widget reported an error; `message` holds its message. */
            @JvmField
            public val WIDGET: Code = Code("widget")

            /** The visitor closed the challenge. */
            @JvmField
            public val CLOSED: Code = Code("closed")

            /** `execute()` was called again before this call settled. */
            @JvmField
            public val SUPERSEDED: Code = Code("superseded")

            /** `reset()` was called before this call settled. */
            @JvmField
            public val RESET: Code = Code("reset")

            /** The web view's renderer process ended, as Android may end it under memory pressure; the widget loads again. */
            @JvmField
            public val WEB_CONTENT_TERMINATED: Code = Code("webContentTerminated")
        }
    }

    override fun toString(): String =
        if (message == null) "CaptchaFoxException($code)" else "CaptchaFoxException($code: $message)"
}
