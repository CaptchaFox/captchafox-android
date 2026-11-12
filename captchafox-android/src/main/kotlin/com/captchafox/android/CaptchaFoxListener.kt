package com.captchafox.android

/** The widget's callbacks, called on the main thread. Each does nothing unless overridden. */
public interface CaptchaFoxListener {
    public fun onLoad() {}

    /** Called with the token, which your backend verifies. */
    public fun onVerify(token: String) {}

    /** Called with the widget's error message. */
    public fun onError(message: String) {}

    /** Called when the visitor failed the challenge. */
    public fun onFail() {}

    /** Called when the token expired before it was used. */
    public fun onExpire() {}

    /** Called when the visitor closed the challenge. */
    public fun onClose() {}

    /** Called when a challenge opens, also in hidden mode. */
    public fun onChallengeOpen() {}
}
