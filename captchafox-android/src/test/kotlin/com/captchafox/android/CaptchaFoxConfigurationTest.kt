package com.captchafox.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CaptchaFoxConfigurationTest {
    @Test
    fun theOriginIsSchemeHostAndPortOfTheBaseUrl() {
        assertEquals("https://app.example", CaptchaFoxConfiguration("sk", "https://App.Example/sign-up?x=1").origin)
        assertEquals("http://app.example:8080", CaptchaFoxConfiguration("sk", "HTTP://app.example:8080").origin)
    }

    @Test
    fun anInternationalizedDomainIsItsPunycode() {
        assertEquals("https://xn--bcher-kva.de", CaptchaFoxConfiguration("sk", "https://bücher.de/").origin)
        assertEquals("https://xn--bcher-kva.de:8443", CaptchaFoxConfiguration("sk", "https://BÜCHER.de:8443/shop?q=ä").origin)
    }

    @Test
    fun theBaseUrlIsAnHttpUrlWithAHost() {
        val invalid = listOf(
            "app.example",
            "file:///android_asset/",
            "data:text/html,x",
            "https://",
            "https://app example/",
            "https://user@bücher.de/",
            "https://my_site.example/",
        )
        for (baseUrl in invalid) {
            assertThrows(baseUrl, IllegalArgumentException::class.java) { CaptchaFoxConfiguration("sk", baseUrl) }
        }
    }

    @Test
    fun configurationsWithTheSameOptionsAreEqual() {
        val configuration = CaptchaFoxConfiguration("sk", "https://app.example/", CaptchaFoxConfiguration.Mode.HIDDEN)

        assertEquals(configuration, CaptchaFoxConfiguration("sk", "https://app.example/", CaptchaFoxConfiguration.Mode("hidden")))
        assertEquals(configuration.hashCode(), CaptchaFoxConfiguration("sk", "https://app.example/", CaptchaFoxConfiguration.Mode.HIDDEN).hashCode())
        assertNotEquals(configuration, CaptchaFoxConfiguration("sk", "https://app.example/", CaptchaFoxConfiguration.Mode.POPUP))
    }
}
