package com.example.simpletimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.simpletimer.ui.theme.SimpleTimerTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleTimerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TimerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun TimerScreen(modifier: Modifier = Modifier, timerViewModel: TimerViewModel = viewModel()) {
    val minutes by timerViewModel.minutes.collectAsState()
    val seconds by timerViewModel.seconds.collectAsState()

    val remainingTime by timerViewModel.remainingTime.collectAsState()
    val isRunning by timerViewModel.isRunning.collectAsState()
    val isDirty by timerViewModel.isDirty.collectAsState()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = minutes,
                onValueChange = { timerViewModel.onMinutesChanged(it) },
                modifier = Modifier.width(70.dp),
                label = { Text("MM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isRunning
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = seconds,
                onValueChange = { timerViewModel.onSecondsChanged(it) },
                modifier = Modifier.width(70.dp),
                label = { Text("SS") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isRunning
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { timerViewModel.setTimer() }, enabled = isDirty && !isRunning) {
                Text(text = "Set")
            }
        }
        Spacer(modifier = Modifier.padding(16.dp))
        Text(
            text = formatTime(remainingTime),
            fontSize = 48.sp // Make timer text larger
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Row {
            Button(
                onClick = { timerViewModel.startTimer() },
                enabled = !isRunning
            ) {
                Text(text = "Start")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { timerViewModel.pauseTimer() },
                enabled = isRunning
            ) {
                Text(text = "Pause")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { timerViewModel.resetToDefault() }) {
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


@Preview(showBackground = true)
@Composable
fun TimerScreenPreview() {
    SimpleTimerTheme {
        TimerScreen()
    }
}
