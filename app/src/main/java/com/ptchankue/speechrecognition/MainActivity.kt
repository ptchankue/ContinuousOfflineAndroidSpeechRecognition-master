package com.ptchankue.speechrecognition

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: SpeechViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                SpeechScreen(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Android does not allow mic access from the background without a foreground service.
        viewModel.stop()
    }
}

@Composable
fun SpeechScreen(viewModel: SpeechViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.start() else viewModel.onPermissionDenied() }

    fun onMicClick() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted || state.isListening) viewModel.toggle()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Offline Speech Recognition", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Engine: ${state.engine}  •  ${state.status}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Text("Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ResultCard(
                transcript = state.transcript,
                partial = state.partial,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = viewModel::clear, modifier = Modifier.weight(1f)) { Text("Clear") }
                Button(
                    onClick = ::onMicClick,
                    shape = CircleShape,
                    modifier = Modifier.size(88.dp),
                    colors = if (state.isListening) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else ButtonDefaults.buttonColors(),
                ) { Text(if (state.isListening) "Stop" else "Start") }
                OutlinedButton(
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(state.transcript)) },
                    enabled = state.transcript.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Copy") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ResultCard(transcript: String, partial: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(transcript, partial) { scroll.animateScrollTo(scroll.maxValue) }

    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp).verticalScroll(scroll)) {
            if (transcript.isEmpty() && partial.isEmpty()) {
                Text(
                    "Recognised speech will appear here…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )
            } else {
                Text(
                    buildAnnotatedString {
                        append(transcript)
                        if (partial.isNotEmpty()) {
                            if (transcript.isNotEmpty()) append(" ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontStyle = FontStyle.Italic)) {
                                append(partial)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
