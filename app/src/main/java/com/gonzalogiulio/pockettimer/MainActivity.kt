package com.gonzalogiulio.pockettimer

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gonzalogiulio.pockettimer.AppConstants.KEY_APP_RUNNING
import com.gonzalogiulio.pockettimer.AppConstants.PREFS_NAME
import com.gonzalogiulio.pockettimer.ui.theme.PocketTimerTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
            PocketTimerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TimerScreen(
                        modifier = Modifier.padding(innerPadding),
                        timerViewModel = timerViewModel
                    )
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

    val isReady by timerViewModel.isReady.collectAsState()
    if (!isReady) {
        // You can show a loading indicator here if you want
        return
    }

    val minutes by timerViewModel.minutes.collectAsState()
    val seconds by timerViewModel.seconds.collectAsState()
    val remainingTime by timerViewModel.remainingTime.collectAsState()
    val isRunning by timerViewModel.isRunning.collectAsState()
    val armSound by timerViewModel.armSound.collectAsState()
    val endSound by timerViewModel.endSound.collectAsState()
    val resetSound by timerViewModel.resetSound.collectAsState()
    val timerVolume by timerViewModel.timerVolume.collectAsState()
    val soundOptions = remember(context) { SoundCatalog.list(context) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isRunning) {
                // --- Timer Running View ---
                Text(
                    text = formatTime(remainingTime),
                    fontSize = 64.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                // --- Timer Picker View ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberPicker(
                        value = minutes.toIntOrNull() ?: 0,
                        onValueChange = { timerViewModel.onMinutesChanged(it.toString()) },
                        range = 0..59
                    )
                    Text(text = ":", fontSize = 32.sp, color = MaterialTheme.colorScheme.onBackground)
                    NumberPicker(
                        value = seconds.toIntOrNull() ?: 0,
                        onValueChange = { timerViewModel.onSecondsChanged(it.toString()) },
                        range = 0..59
                    )
                }
            }
        }

        if (!isRunning) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoundDropdown(
                    label = "Arm",
                    selectedId = armSound,
                    options = soundOptions,
                    onSelect = { timerViewModel.onArmSoundChanged(it) },
                    modifier = Modifier.weight(1f)
                )
                SoundDropdown(
                    label = "End",
                    selectedId = endSound,
                    options = soundOptions,
                    onSelect = { timerViewModel.onEndSoundChanged(it) },
                    modifier = Modifier.weight(1f)
                )
                SoundDropdown(
                    label = "Reset",
                    selectedId = resetSound,
                    options = soundOptions,
                    onSelect = { timerViewModel.onResetSoundChanged(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Timer volume")
                    Text(text = "$timerVolume%")
                }
                Slider(
                    value = timerVolume.toFloat(),
                    onValueChange = { timerViewModel.onTimerVolumeChanged(it.roundToInt()) },
                    valueRange = 0f..100f
                )
                Text(
                    text = "Other audio is briefly lowered while timer sounds play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            grantedText = "Notification/Media Observer: Enabled",
            notGrantedText = "Notification/Media Observer: Disabled",
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
    val itemHeight = 72.dp
    val visibleItemsCount = 3
    val listState =
        rememberLazyListState(initialFirstVisibleItemIndex = items.indexOf(value).coerceAtLeast(0))

    val centralItemIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                -1
            } else {
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }?.index
                    ?: -1
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && centralItemIndex != -1) {
            listState.animateScrollToItem(centralItemIndex)
            onValueChange(items[centralItemIndex])
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItemsCount)
            .width(120.dp)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItemsCount / 2))
        ) {
            items(items.size) { index ->
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
                            TextStyle(fontSize = 64.sp, color = MaterialTheme.colorScheme.onBackground)
                        } else {
                            TextStyle(fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundDropdown(
    label: String,
    selectedId: String,
    options: List<SoundCatalog.SoundOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.id == selectedId }?.label ?: selectedId

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(text = selectedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(text = option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        onClick = {
                            expanded = false
                            onSelect(option.id)
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
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text = if (isGranted) grantedText else notGrantedText)
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledListeners =
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val componentName =
        "com.gonzalogiulio.pockettimer/com.gonzalogiulio.pockettimer.MediaSessionObserverService"
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
    PocketTimerTheme {
        //TimerScreen()
    }
}
