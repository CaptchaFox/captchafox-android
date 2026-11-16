package com.captchafox.android

import java.util.Locale

/** The HTML page that renders the widget and reports its callbacks to the bridge. */
internal object WidgetPage {
    const val SCRIPT_URL = "https://cdn.captchafox.com/api.js?render=explicit&onload=captchafoxOnLoad"

    /** The name of the object through which the page posts its messages. */
    const val BRIDGE_NAME = "CaptchaFoxAndroid"

    // form-action 'none': the web view is never asked about POST navigations, so no form may submit the page away.
    fun html(configuration: CaptchaFoxConfiguration, scriptUrl: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta http-equiv="Content-Security-Policy" content="form-action 'none'">
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
        <style>
        html, body { margin: 0; height: 100%; background: transparent; }
        body { display: flex; align-items: center; justify-content: center; }
        </style>
        <script type="application/json" id="captchafox-options">${optionsJson(configuration)}</script>
        <script>
        (function () {
          var bridge = window.$BRIDGE_NAME;
          function post(message) { bridge.postMessage(JSON.stringify(message)); }
          function messageOf(error) {
            return error && typeof error.message === 'string' ? error.message : String(error || 'Unknown error');
          }
          function postError(error) { post({ type: 'error', message: messageOf(error) }); }
          window.captchafoxOnLoad = function () {
            try {
              var options = JSON.parse(document.getElementById('captchafox-options').textContent);
              options.onVerify = function (token) { post({ type: 'verify', token: token }); };
              options.onError = postError;
              options.onFail = function () { post({ type: 'fail' }); };
              options.onExpire = function () { post({ type: 'expire' }); };
              options.onClose = function () { post({ type: 'close' }); };
              options.onChallengeOpen = function () { post({ type: 'challengeOpen' }); };
              Promise.resolve(window.captchafox.render('#captchafox', options))
                .then(function () { post({ type: 'load' }); }, postError);
            } catch (error) {
              postError(error);
            }
          };
          window.captchafoxScriptFailed = function () {
            postError(new Error('The CaptchaFox script could not be loaded.'));
          };
          window.captchafoxExecute = function (id) {
            function postExecutionFailed(error) { post({ type: 'executionFailed', id: id, message: messageOf(error) }); }
            try {
              window.captchafox.execute().catch(postExecutionFailed);
            } catch (error) {
              postExecutionFailed(error);
            }
          };
          window.captchafoxReset = function () {
            try {
              window.captchafox.reset();
            } catch (error) {
              postError(error);
            }
          };
        })();
        </script>
        <script async src="${attributeEscaped(scriptUrl)}" onerror="captchafoxScriptFailed()"></script>
        </head>
        <body><div id="captchafox"></div></body>
        </html>
    """.trimIndent()

    /**
     * The options as a JSON data block. Values never enter script source; `<` is escaped so that no value can end the
     * `<script>` element.
     */
    fun optionsJson(configuration: CaptchaFoxConfiguration): String {
        val options = sortedMapOf("sitekey" to configuration.siteKey)
        configuration.mode?.let { options["mode"] = it.value }
        configuration.theme?.let { options["theme"] = it.value }
        configuration.language?.let { options["lang"] = it }

        return options.entries.joinToString(",", "{", "}") { (key, value) -> "${jsonString(key)}:${jsonString(value)}" }
    }

    private fun jsonString(value: String): String {
        val json = StringBuilder("\"")
        for (character in value) {
            when {
                character == '"' -> json.append("\\\"")
                character == '\\' -> json.append("\\\\")
                character == '<' || character < ' ' -> json.append(String.format(Locale.ROOT, "\\u%04x", character.code))
                else -> json.append(character)
            }
        }
        return json.append('"').toString()
    }

    private fun attributeEscaped(value: String): String = value.replace("&", "&amp;").replace("\"", "&quot;")
}
