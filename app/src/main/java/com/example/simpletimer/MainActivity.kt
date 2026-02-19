package com.example.simpletimer

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.simpletimer.ui.theme.SimpleTimerTheme
import java.util.Locale
import kotlin.math.abs

const val PREFS_NAME = "TimerPrefs"
const val KEY_APP_RUNNING = "isAppRunning"

class MainActivity : ComponentActivity() {

    private val timerViewModel: TimerViewModel by viewModels()
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TimerService.TimerBinder
            timerViewModel.setTimerService(binder.getService())
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission grant or denial
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set the app running flag
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_APP_RUNNING, true).apply()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            SimpleTimerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TimerScreen(modifier = Modifier.padding(innerPadding), timerViewModel = timerViewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to TimerService
        Intent(this, TimerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        // Start MediaSessionObserverService to link its lifecycle to the app
        Intent(this, MediaSessionObserverService::class.java).also { intent ->
            startService(intent)
        }
    }
}

@Composable
fun TimerScreen(modifier: Modifier = Modifier, timerViewModel: TimerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasNotificationListenerPermission by remember {
        mutableStateOf(isNotificationListenerEnabled(context))
    }
    var canScheduleExactAlarms by remember {
        mutableStateOf(hasExactAlarmPermission(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationListenerPermission = isNotificationListenerEnabled(context)
                canScheduleExactAlarms = hasExactAlarmPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val minutes by timerViewModel.minutes.collectAsState()
    val seconds by timerViewModel.seconds.collectAsState()
    val remainingTime by timerViewModel.remainingTime.collectAsState()
    val isRunning by timerViewModel.isRunning.collectAsState()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isRunning) {
            // --- Timer Running View ---
            Text(
                text = formatTime(remainingTime),
                fontSize = 64.sp,
                color = Color.White
            )
        } else {
            // --- Timer Picker View ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberPicker(value = minutes.toIntOrNull() ?: 0, onValueChange = { timerViewModel.onMinutesChanged(it.toString()) }, range = 0..59)
                Text(text = ":", fontSize = 32.sp, color = Color.White)
                NumberPicker(value = seconds.toIntOrNull() ?: 0, onValueChange = { timerViewModel.onSecondsChanged(it.toString()) }, range = 0..59)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Action Buttons ---
        Row {
            Button(onClick = { if (isRunning) timerViewModel.resetTimer() else timerViewModel.startTimer() }) {
                Text(text = if (isRunning) "Reset" else "Start")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Permission Buttons ---
        PermissionButton(
            isGranted = hasNotificationListenerPermission,
            grantedText = "Media Observer: Enabled",
            notGrantedText = "Media Observer: Disabled",
            onClick = { openNotificationListenerSettings(context) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionButton(
            isGranted = canScheduleExactAlarms,
            grantedText = "Alarm Permission: Granted",
            notGrantedText = "Alarm Permission: Required",
            onClick = { openExactAlarmSettings(context) }
        )
    }
}

@Composable
fun NumberPicker(
    modifier: Modifier = Modifier,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange
) {
    val items = remember { range.toList() }
    val itemHeight = 48.dp
    val visibleItemsCount = 3
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = value)

    val centralItemIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                -1
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }?.index ?: -1
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && centralItemIndex != -1) {
            listState.animateScrollToItem(centralItemIndex)
            onValueChange(items[centralItemIndex])
        }
    }

    Box(modifier = modifier.height(itemHeight * visibleItemsCount).width(100.dp)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItemsCount / 2))
        ) {
            items(items.size) {
                index ->
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillParentMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "%02d".format(items[index]),
                        textAlign = TextAlign.Center,
                        style = if (index == centralItemIndex) {
                            TextStyle(fontSize = 32.sp, color = Color.White)
                        } else {
                            TextStyle(fontSize = 24.sp, color = Color.Gray)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionButton(
    isGranted: Boolean,
    grantedText: String,
    notGrantedText: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isGranted) Color.Green.copy(alpha = 0.5f) else Color.Red.copy(alpha = 0.5f),
            contentColor = Color.White
        )
    ) {
        Text(text = if (isGranted) grantedText else notGrantedText)
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val componentName = "com.example.simpletimer/com.example.simpletimer.MediaSessionObserverService"
    return enabledListeners?.contains(componentName) == true
}

private fun hasExactAlarmPermission(context: Context): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}

private fun openNotificationListenerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    context.startActivity(intent)
}

private fun openExactAlarmSettings(context: Context) {
    Intent().also {
        it.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
        context.startActivity(it)
    }
}

fun formatTime(timeInMillis: Long): String {
    val totalSeconds = (timeInMillis + 999) / 1000 // Round up to the nearest second
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

@Preview(showBackground = true)
@Composable
fun TimerScreenPreview() {
    SimpleTimerTheme {
        //TimerScreen()
    }
}
