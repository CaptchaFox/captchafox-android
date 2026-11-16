package com.captchafox.android.example

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.captchafox.android.CaptchaFoxConfiguration
import com.captchafox.android.compose.CaptchaFoxDialog
import com.captchafox.android.compose.CaptchaFoxWidget
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    Example()
                }
            }
        }
    }
}

private enum class Key(val label: String, val siteKey: String?) {
    PASSES("Passes", "sk_11111111000000001111111100000000"),
    FAILS("Fails", "sk_FFFFFFFF00000000FFFFFFFF00000000"),
    CUSTOM("Custom", null),
}

private const val TEST_DOMAIN = "https://example.com"

/** `example.com` and `https://example.com` both become `https://example.com`; `null` for no domain. */
private fun urlOf(domain: String): String? {
    val trimmed = domain.trim()
    if (trimmed.isEmpty()) {
        return null
    }

    val url = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    return runCatching { CaptchaFoxConfiguration("sk", url) }.getOrNull()?.let { url }
}

/**
 * Shows the widget with the public test keys, one that always passes and one that always fails the challenge, or with
 * a site key and domain of your own.
 */
@Composable
private fun Example() {
    val preferences = LocalContext.current.getSharedPreferences("example", Context.MODE_PRIVATE)
    var key by remember { mutableStateOf(Key.PASSES) }
    var showsDialog by remember { mutableStateOf(false) }
    var isStyled by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("No result yet") }
    // The widget's events, each with the seconds since the last load or Verify.
    val events = remember { mutableStateListOf<String>() }
    var start by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    // The custom site key and domain as typed, and as last applied; the widget reloads only on Use.
    var customSiteKey by remember { mutableStateOf(preferences.getString("customSiteKey", "")!!) }
    var customDomain by remember { mutableStateOf(preferences.getString("customDomain", "")!!) }
    var appliedSiteKey by remember { mutableStateOf("") }
    var appliedDomain by remember { mutableStateOf(TEST_DOMAIN) }

    fun log(event: String) {
        val seconds = (SystemClock.elapsedRealtime() - start) / 1000.0
        events += String.format(Locale.ROOT, "+%.2fs  %s", seconds, event)
    }

    val canApply = customSiteKey.isNotBlank() && urlOf(customDomain) != null
    fun apply() {
        val domain = urlOf(customDomain) ?: return
        preferences.edit().putString("customSiteKey", customSiteKey).putString("customDomain", customDomain).apply()
        appliedSiteKey = customSiteKey.trim()
        appliedDomain = domain
        result = "No result yet"
        events.clear()
    }
    LaunchedEffect(Unit) {
        if (canApply) {
            apply()
        }
    }

    fun configuration(mode: CaptchaFoxConfiguration.Mode): CaptchaFoxConfiguration = when (key) {
        Key.PASSES, Key.FAILS -> CaptchaFoxConfiguration(key.siteKey!!, TEST_DOMAIN, mode)
        Key.CUSTOM -> CaptchaFoxConfiguration(appliedSiteKey, appliedDomain, mode)
    }

    Column(
        Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("CaptchaFox", style = MaterialTheme.typography.headlineMedium)

        Text("Site key", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in Key.entries) {
                FilterChip(selected = key == option, onClick = { key = option }, label = { Text(option.label) })
            }
        }
        if (key == Key.CUSTOM) {
            OutlinedTextField(customSiteKey, { customSiteKey = it }, Modifier.fillMaxWidth(), label = { Text("Site key, sk_…") }, singleLine = true)
            OutlinedTextField(
                customDomain,
                { customDomain = it },
                Modifier.fillMaxWidth(),
                label = { Text("Domain of the site, such as example.com") },
                singleLine = true,
            )
            Button(onClick = ::apply, enabled = canApply) { Text("Use") }
        }

        if (key != Key.CUSTOM || appliedSiteKey.isNotEmpty()) {
            Text("Dialog, hidden mode", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(isStyled, { isStyled = it })
                Text("Header, footer, darker backdrop")
            }
            Button(onClick = {
                start = SystemClock.elapsedRealtime()
                events.clear()
                showsDialog = true
            }) { Text("Verify") }

            Text("Inline", style = MaterialTheme.typography.titleSmall)
            CaptchaFoxWidget(
                configuration = configuration(CaptchaFoxConfiguration.Mode.INLINE),
                modifier = Modifier.fillMaxWidth().height(80.dp),
                onLoad = {
                    start = SystemClock.elapsedRealtime()
                    events.clear()
                    log("load")
                },
                onVerify = {
                    result = "Token: $it"
                    log("verify")
                },
                onError = {
                    result = "Error: $it"
                    log("error: $it")
                },
                onFail = { log("fail") },
                onExpire = { log("expire") },
                onClose = { log("close") },
                onChallengeOpen = { log("challenge open") },
            )
        }

        Text("Result", style = MaterialTheme.typography.titleSmall)
        SelectionContainer {
            Text(result, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }

        if (events.isNotEmpty()) {
            Text("Events", style = MaterialTheme.typography.titleSmall)
            for (event in events) {
                Text(event, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showsDialog) {
        CaptchaFoxDialog(
            configuration = configuration(CaptchaFoxConfiguration.Mode.HIDDEN),
            onDismissRequest = {
                showsDialog = false
                log("dialog dismissed")
            },
            backdropColor = Color.Black.copy(alpha = if (isStyled) 0.85f else 0.6f),
            header = if (isStyled) {
                { Text("Verify you are human", Modifier.padding(16.dp), color = Color.White, style = MaterialTheme.typography.titleMedium) }
            } else {
                null
            },
            footer = if (isStyled) {
                { Text("Protected by CaptchaFox", Modifier.padding(16.dp), color = Color.White.copy(alpha = 0.7f)) }
            } else {
                null
            },
            onLoad = { log("load") },
            onVerify = {
                result = "Token: $it"
                log("verify")
            },
            onError = {
                result = "Error: $it"
                log("error: $it")
            },
            onFail = { log("fail") },
            onExpire = { log("expire") },
            onClose = {
                result = "Closed"
                log("close")
            },
            onChallengeOpen = { log("challenge open") },
        )
    }
}
