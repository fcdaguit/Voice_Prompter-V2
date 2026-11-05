package com.voiceprompter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voiceprompter.data.ThemeMode
import com.voiceprompter.tts.TtsEngine

private const val PREVIEW_SENTENCE =
    "This is how your script will sound at the current voice and speed."

private val TEXT_COLORS = listOf(
    0xFFFFFFFF, 0xFFF6AD55, 0xFF90CDF4, 0xFF9AE6B4, 0xFF111111
)
private val BACKGROUND_COLORS = listOf(
    0xFF000000, 0xFF101014, 0xFF1A202C, 0xFF2D3748, 0xFFFFFFFF
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsState()

    // A short-lived engine instance just for listing and auditioning voices;
    // the service owns the one used for real playback.
    val engine = remember { TtsEngine(context) }
    var voicesLoaded by remember { mutableStateOf(false) }
    DisposableEffect(engine) {
        engine.onReady = { voicesLoaded = true }
        onDispose { engine.shutdown() }
    }

    val voices = remember(voicesLoaded) { engine.availableVoices() }
    var voiceMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            SectionLabel("Voice")

            Box {
                OutlinedButton(
                    onClick = { voiceMenuOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        prefs.voiceName
                            ?.let { name -> voices.firstOrNull { it.name == name } }
                            ?.let { "${it.locale.displayName} · ${it.name}" }
                            ?: if (voices.isEmpty()) "Loading installed voices…"
                            else "System default"
                    )
                }
                DropdownMenu(
                    expanded = voiceMenuOpen,
                    onDismissRequest = { voiceMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("System default") },
                        onClick = { viewModel.setVoice(null); voiceMenuOpen = false }
                    )
                    HorizontalDivider()
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text("${voice.locale.displayName} · ${voice.name}") },
                            onClick = { viewModel.setVoice(voice.name); voiceMenuOpen = false }
                        )
                    }
                }
            }

            if (voices.isEmpty() && voicesLoaded) {
                Text(
                    "No offline voices found. Install voice data in " +
                        "Settings > Accessibility > Text-to-speech output.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            TextButton(
                onClick = {
                    engine.preview(
                        prefs.voiceName,
                        prefs.speechRate,
                        prefs.pitch,
                        PREVIEW_SENTENCE
                    )
                },
                modifier = Modifier.padding(top = 4.dp)
            ) { Text("Preview voice") }

            LabelledSlider(
                label = "Speed",
                value = prefs.speechRate,
                range = 0.5f..2.0f,
                display = "%.2fx".format(prefs.speechRate),
                onChange = viewModel::setRate
            )

            LabelledSlider(
                label = "Pitch",
                value = prefs.pitch,
                range = 0.5f..2.0f,
                display = "%.2f".format(prefs.pitch),
                onChange = viewModel::setPitch
            )

            SectionLabel("Display")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = prefs.theme == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            LabelledSlider(
                label = "Prompter text size",
                value = prefs.fontSizeSp.toFloat(),
                range = 14f..72f,
                display = "${prefs.fontSizeSp} sp",
                onChange = { viewModel.setFontSize(it.toInt()) }
            )

            ColorRow(
                label = "Text colour",
                colors = TEXT_COLORS,
                selected = prefs.textColor,
                onSelect = viewModel::setTextColor
            )

            ColorRow(
                label = "Background colour",
                colors = BACKGROUND_COLORS,
                selected = prefs.backgroundColor,
                onSelect = viewModel::setBackgroundColor
            )

            SectionLabel("Controls")

            ToggleRow(
                title = "Volume keys change section",
                subtitle = "Volume up for previous, down for next. " +
                    "Only works while the prompter is on screen.",
                checked = prefs.volumeKeyNavigation,
                onChange = viewModel::setVolumeKeyNavigation
            )

            ToggleRow(
                title = "Keep screen on while prompting",
                subtitle = "Uses more battery, but the script stays visible.",
                checked = prefs.keepScreenOn,
                onChange = viewModel::setKeepScreenOn
            )

            Text(
                "Bluetooth headsets with media buttons can play, pause and skip " +
                    "even when the screen is off. Presentation remotes that send " +
                    "page-up and page-down only work while this app is in front.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f))
            Text(display, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ColorRow(
    label: String,
    colors: List<Long>,
    selected: Long,
    onSelect: (Long) -> Unit
) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(label)
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            colors.forEach { value ->
                Box(
                    Modifier
                        .size(36.dp)
                        .background(Color(value), CircleShape)
                        .border(
                            width = if (value == selected) 3.dp else 1.dp,
                            color = if (value == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                        .clickable { onSelect(value) }
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
