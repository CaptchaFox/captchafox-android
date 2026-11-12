package com.captchafox.android

import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * What the widget shows and for which site. An option that is `null` is not passed to the widget, so its default holds.
 *
 * @property baseUrl The URL the widget's page is loaded with, such as `https://example.com`. Its host must be the
 *   domain set up for the site in the CaptchaFox portal.
 * @property language A language code such as `en` or `de`; by default the widget detects the language.
 * @throws IllegalArgumentException if [baseUrl] is no `http` or `https` URL with a host.
 */
public class CaptchaFoxConfiguration @JvmOverloads constructor(
    public val siteKey: String,
    public val baseUrl: String,
    public val mode: Mode? = null,
    public val theme: Theme? = null,
    public val language: String? = null,
) {
    /** The origin of [baseUrl]: the only one whose page may send the widget's messages. */
    internal val origin: String = originOf(baseUrl)

    /** How the widget is shown. New modes may be added in a minor version. */
    public class Mode(public val value: String) {
        override fun equals(other: Any?): Boolean = other is Mode && other.value == value

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = value

        public companion object {
            @JvmField
            public val INLINE: Mode = Mode("inline")

            @JvmField
            public val POPUP: Mode = Mode("popup")

            /** Invisible until a challenge opens; `execute()` starts the verification. */
            @JvmField
            public val HIDDEN: Mode = Mode("hidden")
        }
    }

    /** The widget's colours. New themes may be added in a minor version. */
    public class Theme(public val value: String) {
        override fun equals(other: Any?): Boolean = other is Theme && other.value == value

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = value

        public companion object {
            @JvmField
            public val LIGHT: Theme = Theme("light")

            @JvmField
            public val DARK: Theme = Theme("dark")
        }
    }

    override fun equals(other: Any?): Boolean =
        other is CaptchaFoxConfiguration &&
            other.siteKey == siteKey &&
            other.baseUrl == baseUrl &&
            other.mode == mode &&
            other.theme == theme &&
            other.language == language

    override fun hashCode(): Int = listOf(siteKey, baseUrl, mode, theme, language).hashCode()

    override fun toString(): String =
        "CaptchaFoxConfiguration(siteKey=$siteKey, baseUrl=$baseUrl, mode=$mode, theme=$theme, language=$language)"

    private companion object {
        /**
         * `scheme://host[:port]` with the host in ASCII, as an origin rule of the web view's message listener expects it.
         * An internationalized domain, which `URI` holds as no host, becomes its punycode.
         */
        fun originOf(baseUrl: String): String {
            val uri = try {
                URI(baseUrl)
            } catch (error: URISyntaxException) {
                throw IllegalArgumentException("baseUrl is no URL: $baseUrl", error)
            }

            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            require(scheme == "https" || scheme == "http") { "baseUrl must be an http or https URL: $baseUrl" }

            val server = if (uri.host == null) asciiServer(uri) else uri
            val host = server?.host?.lowercase(Locale.ROOT)
            require(server != null && !host.isNullOrEmpty()) { "baseUrl must be an http or https URL with a host: $baseUrl" }

            return if (server.port == -1) "$scheme://$host" else "$scheme://$host:${server.port}"
        }

        /** The authority of the URI with its host in punycode, parsed as host and port; `null` where that fails. */
        private fun asciiServer(uri: URI): URI? {
            val authority = uri.authority ?: return null
            if ('@' in authority) {
                return null
            }

            val portStart = authority.lastIndexOf(':')
            val host = if (portStart == -1) authority else authority.substring(0, portStart)
            val port = if (portStart == -1) "" else authority.substring(portStart)
            return try {
                URI("${uri.scheme}://${IDN.toASCII(host)}$port").parseServerAuthority()
            } catch (error: IllegalArgumentException) {
                null
            } catch (error: URISyntaxException) {
                null
            }
        }
    }
}
