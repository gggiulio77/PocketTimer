package com.example.simpletimer

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.simpletimer.ui.theme.SimpleTimerTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission grant or denial
    }

    private val timerViewModel: TimerViewModel by viewModels()

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Start services to ensure they are running for background playback
        startService(Intent(this, PlaybackService::class.java))
        startService(Intent(this, TimerService::class.java))

        setContent {
            SimpleTimerTheme {
                // Use DisposableEffect to bind and unbind the ViewModel to the service
                DisposableEffect(Unit) {
                    timerViewModel.bindService(this@MainActivity)
                    onDispose {
                        timerViewModel.unbindService(this@MainActivity)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TimerScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = timerViewModel
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TimerScreen(modifier: Modifier = Modifier, viewModel: TimerViewModel) {
    val context = LocalContext.current
    var mediaController by remember { mutableStateOf<MediaController?>(null) }

    // This effect handles the connection to the MediaController for playback commands
    DisposableEffect(context) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
            },
            MoreExecutors.directExecutor()
        )

        onDispose {
            mediaController?.release()
        }
    }

    // Collect state directly from the ViewModel
    val minutes by viewModel.minutes.collectAsState()
    val seconds by viewModel.seconds.collectAsState()
    val remainingTime by viewModel.remainingTime.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = minutes,
                onValueChange = { viewModel.onMinutesChanged(it) },
                modifier = Modifier.width(70.dp),
                label = { Text("MM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isRunning
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = seconds,
                onValueChange = { viewModel.onSecondsChanged(it) },
                modifier = Modifier.width(70.dp),
                label = { Text("SS") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isRunning
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.setTimer() }, enabled = isDirty && !isRunning) {
                Text(text = "Set")
            }
        }
        Spacer(modifier = Modifier.padding(16.dp))
        Text(
            text = formatTime(remainingTime),
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Row {
            Button(
                onClick = {
                    viewModel.startTimer()
                    mediaController?.play()
                },
                enabled = !isRunning
            ) {
                Text(text = "Start")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.resetToDefault() }) {
                Text(text = "Reset")
            }
        }
    }
}

fun formatTime(timeInMillis: Long): String {
    val totalSeconds = timeInMillis / 1000
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun TimerScreenPreview() {
    SimpleTimerTheme {
        // The preview will use a dummy ViewModel and will not have functional logic
        TimerScreen(viewModel = TimerViewModel())
    }
}
