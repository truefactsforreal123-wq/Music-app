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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aura.player.di.AppContainer
import com.aura.player.ui.components.glass
import com.aura.player.ui.theme.AuraShapes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Paste-a-link import. Fetching shows a staged progress bar; on success the
 * playlist opens immediately with the checkbox multi-select + "Download all" available.
 */
@Composable
fun ImportScreen(container: AppContainer, navController: NavController) {
    var url by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<ImportState>(ImportState.Idle) }
    val recentUrls by container.settings.recentUrls.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Import playlist", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste a playlist URL (YouTube, Spotify, M3U…)") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    job?.cancel()
                    job = scope.launch {
                        state = ImportState.Fetching
                        try {
                            val res = container.library.fetchPlaylist(url.trim())
                            container.library.syncLibrary()
                            state = ImportState.Idle
                            url = ""
                            navController.navigate("playlist/${res.playlist.id}")
                        } catch (t: Throwable) {
                            state = ImportState.Error(t.message ?: "Something went wrong")
                        }
                    }
                },
                enabled = url.isNotBlank() && state !is ImportState.Fetching,
            ) {
                Text("Fetch")
            }
            TextButton(onClick = {
                val text = clipboard.getText()?.text.orEmpty().trim()
                if (text.isNotBlank()) url = text
            }) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Paste")
            }
        }

        // Staged progress bar while the server walks the playlist.
        if (state is ImportState.Fetching) {
            Spacer(Modifier.height(20.dp))
            var stage by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                while (true) {
                    stage++
                    delay(1200)
                }
            }
            val stageText = listOf("Reading link…", "Listing tracks…", "Importing artwork…")[stage % 3]
            Column(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    stageText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val s = state) {
            is ImportState.Error -> {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(AuraShapes.medium)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            else -> Unit
        }

        if (recentUrls.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Recent links", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            recentUrls.forEach { recent ->
                SuggestionChip(
                    onClick = { url = recent },
                    label = {
                        Text(
                            recent.takeLast(48),
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

private sealed interface ImportState {
    data object Idle : ImportState
    data object Fetching : ImportState
    data class Error(val message: String) : ImportState
}
