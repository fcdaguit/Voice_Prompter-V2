package com.voiceprompter.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voiceprompter.tts.PlaybackService

@Composable
fun PrompterScreen(
    scriptId: Long,
    onBack: () -> Unit,
    viewModel: PrompterViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current

    val playback by viewModel.playback.collectAsState()
    val prefs by viewModel.preferences.collectAsState()

    LaunchedEffect(scriptId) { viewModel.prepare(scriptId) }

    // Notification permission is what makes the foreground service visible on
    // Android 13+. Playback still works without it, but the controls are hidden.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(prefs.keepScreenOn) {
        view.keepScreenOn = prefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.release() }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(playback.index) {
        if (playback.hasScript) {
            // Keep the spoken line a third of the way down rather than at the very top.
            listState.animateScrollToItem(
                index = playback.index,
                scrollOffset = -160
            )
        }
    }

    val background = Color(prefs.backgroundColor)
    val textColor = Color(prefs.textColor)

    Column(
        Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                PlaybackService.send(context, PlaybackService.ACTION_STOP)
                onBack()
            }) { Text("Close", color = textColor) }

            Text(
                text = if (playback.hasScript) {
                    "${playback.index + 1} / ${playback.sentences.size}"
                } else "",
                color = textColor.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }

        if (playback.sentences.isNotEmpty()) {
            LinearProgressIndicator(
                progress = {
                    (playback.index + 1f) / playback.sentences.size
                },
                color = MaterialTheme.colorScheme.primary,
                trackColor = textColor.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            )
        }

        playback.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        Box(Modifier.weight(1f)) {
            if (!playback.hasScript) {
                Text(
                    "This script has no readable text.",
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 24.dp,
                        // Generous bottom padding so the last line can still scroll
                        // up into the comfortable reading zone.
                        bottom = 400.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        playback.sentences,
                        key = { index, _ -> index }
                    ) { index, sentence ->
                        SentenceLine(
                            text = sentence,
                            isCurrent = index == playback.index,
                            isSpoken = index < playback.index,
                            fontSizeSp = prefs.fontSizeSp,
                            textColor = textColor,
                            onTap = {
                                PlaybackService.send(
                                    context,
                                    PlaybackService.ACTION_SEEK,
                                    index
                                )
                            }
                        )
                    }
                }
            }
        }

        TransportBar(
            isPlaying = playback.isPlaying,
            enabled = playback.hasScript,
            textColor = textColor,
            onPrevious = { PlaybackService.send(context, PlaybackService.ACTION_PREVIOUS) },
            onToggle = {
                PlaybackService.send(
                    context,
                    if (playback.isPlaying) PlaybackService.ACTION_PAUSE
                    else PlaybackService.ACTION_PLAY
                )
            },
            onNext = { PlaybackService.send(context, PlaybackService.ACTION_NEXT) }
        )
    }
}

@Composable
private fun SentenceLine(
    text: String,
    isCurrent: Boolean,
    isSpoken: Boolean,
    fontSizeSp: Int,
    textColor: Color,
    onTap: () -> Unit
) {
    val alpha = when {
        isCurrent -> 1f
        isSpoken -> 0.35f
        else -> 0.65f
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .then(
                if (isCurrent) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = textColor.copy(alpha = alpha),
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.45).sp,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun TransportBar(
    isPlaying: Boolean,
    enabled: Boolean,
    textColor: Color,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        ) { Text("Back", color = textColor) }

        Button(
            onClick = onToggle,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.weight(1.4f)
        ) { Text(if (isPlaying) "Pause" else "Play") }

        OutlinedButton(
            onClick = onNext,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        ) { Text("Next", color = textColor) }
    }
}
