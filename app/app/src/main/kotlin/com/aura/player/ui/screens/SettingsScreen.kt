package com.aura.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.player.data.prefs.SettingsRepository
import com.aura.player.data.prefs.ThemeMode
import com.aura.player.di.AppContainer
import com.aura.player.ui.components.glass
import com.aura.player.ui.theme.AuraShapes
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer) {
    val settings = container.settings
    val scope = rememberCoroutineScope()
    val serverUrl by settings.serverUrl.collectAsState(initial = SettingsRepository.DEFAULT_SERVER_URL)
    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val wifiOnly by settings.wifiOnly.collectAsState(initial = true)
    val skipSilence by settings.skipSilence.collectAsState(initial = false)

    var urlDraft by remember(serverUrl) { mutableStateOf(serverUrl) }
    var health by remember { mutableStateOf<com.aura.player.data.api.HealthResponse?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(20.dp))

        // ---- Server ----
        Text("Server", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = urlDraft,
            onValueChange = { urlDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Aura server address") },
            supportingText = { Text("Shown when you start the server on your PC") },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { scope.launch { settings.setServerUrl(urlDraft) } }) { Text("Save") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        testing = true
                        health = runCatching { container.api.health() }.getOrNull()
                        testing = false
                    }
                },
            ) { Text(if (testing) "Testing…" else "Test connection") }
        }

        health?.let { h ->
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(AuraShapes.medium)
                    .padding(14.dp),
            ) {
                Text(
                    if (h.ok) "Connected · server v${h.version}" else "Server unreachable",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "yt-dlp: ${if (h.ytDlp.available) h.ytDlp.version ?: "ready" else "missing — run npm run setup"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (h.lanIps.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Server suggested:", style = MaterialTheme.typography.labelMedium)
                    h.lanIps.forEach { ip ->
                        Text(
                            "http://$ip:8787",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))

        // ---- Appearance ----
        Text("Theme", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { scope.launch { settings.setThemeMode(mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                ) {
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.LIGHT -> "Light"
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))

        // ---- Playback & downloads ----
        Text("Playback & downloads", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        ToggleRow("Download on Wi-Fi only", "Avoids mobile data for downloads", wifiOnly) {
            scope.launch { settings.setWifiOnly(it) }
        }
        ToggleRow("Skip silence", "Trims silent stretches during playback", skipSilence) {
            scope.launch { settings.setSkipSilence(it) }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))

        // ---- Storage ----
        val totalBytes by container.db.trackDao().observeTotalDownloadSize().collectAsState(initial = 0L)
        Text("Storage", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .glass(AuraShapes.medium)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Downloaded songs", style = MaterialTheme.typography.titleSmall)
                Text(
                    formatBytes(totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { scope.launch { container.downloads.deleteAllLocal() } }) {
                Text("Clear all")
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "Aura 1.0 — personal music player. Fetches playlist links on your own server and plays them anywhere, on any headset.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}
