package com.captchafox.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageMessageTest {
    @Test
    fun eachMessageOfThePageIsAnEvent() {
        assertEquals(PageMessage.Event(WidgetEvent.Load), PageMessage.parse("""{"type":"load"}"""))
        assertEquals(PageMessage.Event(WidgetEvent.Verify("token-1")), PageMessage.parse("""{"type":"verify","token":"token-1"}"""))
        assertEquals(PageMessage.Event(WidgetEvent.Error("Failed")), PageMessage.parse("""{"type":"error","message":"Failed"}"""))
        assertEquals(PageMessage.Event(WidgetEvent.Error("Unknown error")), PageMessage.parse("""{"type":"error"}"""))
        assertEquals(PageMessage.Event(WidgetEvent.Fail), PageMessage.parse("""{"type":"fail"}"""))
        assertEquals(PageMessage.Event(WidgetEvent.Expire), PageMessage.parse("""{"type":"expire"}"""))
        assertEquals(PageMessage.Event(WidgetEvent.Close), PageMessage.parse("""{"type":"close"}"""))
        assertEquals(PageMessage.Event(WidgetEvent.ChallengeOpen), PageMessage.parse("""{"type":"challengeOpen"}"""))
        assertEquals(PageMessage.ExecutionFailed(3, "Rejected"), PageMessage.parse("""{"type":"executionFailed","id":3,"message":"Rejected"}"""))
    }

    @Test
    fun aMessageOfNoKnownFormIsNone() {
        val messages = listOf(
            """{"type":"VERIFY","token":"upper-case"}""",
            """{"type":"token","token":"unknown-type"}""",
            """"{\"type\":\"verify\",\"token\":\"string\"}"""",
            """{"type":"verify"}""",
            """{"type":"verify","token":""}""",
            """{"type":"verify","token":42}""",
            """{"token":"no-type"}""",
            """{"type":"executionFailed","message":"no id"}""",
            """{"type":"executionFailed","id":"3"}""",
            """not json""",
        )

        for (message in messages) {
            assertNull(message, PageMessage.parse(message))
        }
    }
}
