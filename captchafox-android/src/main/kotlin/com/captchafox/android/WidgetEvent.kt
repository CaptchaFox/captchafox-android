package com.captchafox.android

import org.json.JSONException
import org.json.JSONObject

/** A callback of the widget. */
internal sealed interface WidgetEvent {
    data object Load : WidgetEvent

    data class Verify(val token: String) : WidgetEvent

    data class Error(val message: String) : WidgetEvent

    data object Fail : WidgetEvent

    data object Expire : WidgetEvent

    data object Close : WidgetEvent

    data object ChallengeOpen : WidgetEvent
}

/**
 * A message the page posts: a callback of the widget, or the rejection of the widget's `execute()` for the call with
 * the given id, which often follows the widget's own error callback and so is no event of its own.
 */
internal sealed interface PageMessage {
    data class Event(val event: WidgetEvent) : PageMessage

    data class ExecutionFailed(val id: Int, val message: String) : PageMessage

    companion object {
        /** `null` for a message that is not one the page sends. */
        fun parse(data: String): PageMessage? {
            val body = try {
                JSONObject(data)
            } catch (error: JSONException) {
                return null
            }

            return when (body.opt("type") as? String) {
                "load" -> Event(WidgetEvent.Load)
                "verify" -> {
                    val token = body.opt("token") as? String
                    if (token.isNullOrEmpty()) {
                        null
                    } else {
                        Event(WidgetEvent.Verify(token))
                    }
                }
                "error" -> Event(WidgetEvent.Error(errorMessage(body)))
                "fail" -> Event(WidgetEvent.Fail)
                "expire" -> Event(WidgetEvent.Expire)
                "close" -> Event(WidgetEvent.Close)
                "challengeOpen" -> Event(WidgetEvent.ChallengeOpen)
                "executionFailed" -> {
                    val id = body.opt("id") as? Int
                    if (id == null) {
                        null
                    } else {
                        ExecutionFailed(id, errorMessage(body))
                    }
                }
                else -> null
            }
        }

        private fun errorMessage(body: JSONObject): String = body.opt("message") as? String ?: "Unknown error"
    }
}
