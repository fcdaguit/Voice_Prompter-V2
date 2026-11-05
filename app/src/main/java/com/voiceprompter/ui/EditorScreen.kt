package com.voiceprompter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    scriptId: Long,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    LaunchedEffect(scriptId) { viewModel.load(scriptId) }

    val script by viewModel.script.collectAsState()

    var title by remember(script?.id) { mutableStateOf(script?.title.orEmpty()) }
    var body by remember(script?.id) { mutableStateOf(script?.body.orEmpty()) }
    var dirty by remember { mutableStateOf(false) }

    // Auto-save: hold off until typing pauses so we are not writing on every keystroke.
    LaunchedEffect(title, body, dirty) {
        if (!dirty) return@LaunchedEffect
        delay(600)
        viewModel.update(title, body)
        dirty = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (dirty) "Editing…" else "Saved") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(
                        onClick = {
                            // Flush pending edits before handing the text to the prompter.
                            viewModel.update(title, body)
                            dirty = false
                            onPlay()
                        },
                        enabled = body.isNotBlank()
                    ) { Text("Prompt") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = title,
                onValueChange = { title = it; dirty = true },
                placeholder = { Text("Title") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                colors = transparentFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )

            TextField(
                value = body,
                onValueChange = { body = it; dirty = true },
                placeholder = { Text("Write or paste your script here…") },
                textStyle = TextStyle(fontSize = 17.sp, lineHeight = 28.sp),
                colors = transparentFieldColors(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

/**
 * The editor should feel like a page, not a form: no filled container, no
 * underline, maximum room for the text itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent
)
