# Timer App Development Roadmap - Status: CORE FEATURES COMPLETED

This document outlines the steps taken to create the timer application and future plans.

### Step 1: Build the Timer UI and Local Logic [DONE]
*   [x] **Create the UI**: Using Jetpack Compose, built a screen with custom scrollable number pickers.
*   [x] **Implement UI Logic & State Management**: Used a `ViewModel` to bridge UI and Service.
*   [x] **Implement Timer Countdown**: Logic moved to `TimerService` for reliability.

### Step 2: Move the Timer to a Foreground Service [DONE]
*   [x] **Create the Service**: `TimerService` is the single source of truth.
*   [x] **Make it a Foreground Service**: Runs with a persistent notification.
*   [x] **Refine Notification Behavior**: Shows remaining time and has a "Cancel" action.
*   [x] **Handle Permissions**: `POST_NOTIFICATIONS` is handled.
*   [x] **Communicate between UI and Service**: Implemented using the Bound Service pattern.

### Step 3: Observe Media Sessions for Headphone Controls [DONE]
*   [x] **Create a `NotificationListenerService`**: `MediaSessionObserverService` implemented.
*   [x] **Use `MediaSessionManager`**: Observes active sessions.
*   [x] **Implement `MediaController.Callback`**: Listens for `PlaybackState` changes.
*   [x] **Infer User Intent**: Detects quick "Pause -> Play" sequence (within 1s) to trigger the timer.
*   [x] **Request `BIND_NOTIFICATION_LISTENER_SERVICE` permission**: Managed via UI button.

### Step 4: Play Alarm and Schedule with AlarmManager [DONE]
*   [x] **Use `AlarmManager` for Scheduling**: Uses `setExactAndAllowWhileIdle` for reliability.
*   [x] **Play Sound with `MediaPlayer`**: Plays `beep.wav` from raw resources.
*   [x] **Handle Permissions**: `SCHEDULE_EXACT_ALARM` handled via settings check.

### Step 5: UI/UX Enhancements [DONE]
*   [x] **Implement Scrollable Number Pickers**: Custom `LazyColumn` implementation with snapping.
*   [x] **Improve Permission Handling**: Dynamic Green/Red buttons for status.
*   [x] **Refine Visuals and Animations**: Snapping animation and clear state transitions.

### Step 6: App Termination and Background Behavior [DONE]
*   [x] **Handle Task Removal**: `onTaskRemoved` implemented in both services to cleanup and update state.
*   [x] **Ensure Correct Behavior on Phone Lock**: Foreground service ensures timer reliability.
*   [x] **Manage App Lifecycle**: `KEY_APP_RUNNING` in `SharedPreferences` prevents accidental triggers.

### Step 7: Onboarding, UI, and State Management [DONE]
*   [x] **Polished UI**: Large timer display and intuitive pickers.
*   [x] **Clear Permission Status**: Immediate feedback on required system settings.
*   [x] **State Persistence**: Saves last set time in `SharedPreferences`.

### Step 8: Sound & Customization [DONE]
*   [x] **Sound Selector UI**: Dropdowns for the Arm, End, and Reset events on the main screen.
*   [x] **Sound Library**: Auto-discovers audio files in `assets/sounds/`, plus built-in beep and silent options.
*   [x] **Preview on Select**: Choosing a sound plays it immediately.
*   [x] **Arm & Reset Sounds**: Played when the timer starts and when it is reset/cancelled.

### Future Roadmap
*   [ ] **Distribution**:
    *   Add a beta app to the Google Play Store.
