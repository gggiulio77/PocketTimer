# Timer App Development Roadmap

This document outlines the steps to create the timer application.

### Step 1: Build the Timer UI and Local Logic

This step focuses on creating a robust, user-friendly timer that runs entirely within the app.

*   **Tasks:**
    1.  **Create the UI:** Using Jetpack Compose, build a screen with:
        *   `TextField`s for minutes and seconds, with input validation to limit values to a maximum of 59.
        *   A large `Text` element to display the current countdown.
        *   Buttons for "Start", "Pause", "Set", and "Reset".
    2.  **Implement UI Logic & State Management:**
        *   Use a `ViewModel` to hold and manage all UI state (remaining time, running status, input values).
        *   The app starts with a default 2-minute timer displayed and ready to go.
        *   The "Start" button is enabled when the timer is not running.
        *   The "Set" button is only enabled if the user modifies the time inputs.
        *   When the timer finishes, it automatically resets to the 2-minute default, ready for the next use.
    3.  **Implement Timer Countdown:** Use Kotlin Coroutines within the `ViewModel` for the countdown logic.

*   **Concepts to learn:**
    *   **Jetpack Compose:** For building the UI. Focus on `Column`, `Row`, `Text`, `Button`, `TextField` and state management.
    *   **ViewModel & StateFlow:** To store and manage UI-related data in a lifecycle-conscious way, creating a single source of truth for the UI.
    *   **Kotlin Coroutines:** For running the countdown logic without blocking the main thread.

*   **Documentation:**
    *   [Compose UI basics](https://developer.android.com/jetpack/compose/layouts/basics)
    *   [State in Jetpack Compose](https://developer.android.com/jetpack/compose/state)
    *   [ViewModel Overview](https://developer.android.com/topic/libraries/architecture/viewmodel)
    *   [Kotlin coroutines on Android](https://developer.android.com/kotlin/coroutines)

### Step 2: Move the Timer to a Foreground Service

A timer that stops when the phone is locked is not very useful. To fix this, we'll move the timer logic into a service that can run in the background.

*   **Tasks:**
    1.  **Create the Service:** Create a class that inherits from `Service` and make it the **single source of truth** for all timer state and logic.
    2.  **Make it a Foreground Service:** Start the service as a "foreground service".
    3.  **Refine Notification Behavior:** Show a persistent notification that displays the remaining time. The notification should only be visible while the timer is running and must be dismissed when the timer is reset, or finished.
    4.  **Handle Permissions:** Request necessary permissions like `POST_NOTIFICATIONS` and `FOREGROUND_SERVICE_DATA_SYNC`.
    5.  **Communicate between UI and Service:** Use the **Bound Service** pattern. The `ViewModel` becomes a "dumb bridge" that connects the UI to the service, collects its state, and forwards user events.

*   **Concepts to learn:**
    *   **Services:** Android components for long-running operations in the background.
    *   **Foreground Services:** A special type of service that the user is actively aware of, with a required notification.
    *   **Bound Services:** The recommended pattern for UI-Service communication, allowing direct method calls and data observation.
    *   **Notifications & Channels:** How to create, display, and dismiss notifications.

*   **Documentation:**
    *   [Services overview](https://developer.android.com/guide/components/services)
    *   [Foreground Services](https://developer.android.com/guide/components/foreground-services)
    *   [Bound Services](https://developer.android.com/guide/components/bound-services)
    *   [Create a Notification](https://developer.android.com/develop/ui/views/notifications/build-notification)

### Step 3: Observe Media Sessions for Headphone Controls

Instead of taking control of media buttons, we listen to the system's active media sessions to infer user actions. This allows for a more seamless integration with other media apps.

*   **Tasks:**
    1.  **Create a `NotificationListenerService`:** This service has the permission to view notifications and media sessions.
    2.  **Use `MediaSessionManager`:** Get a list of active `MediaController` objects.
    3.  **Implement `MediaController.Callback`:** Register a callback for each active media session to listen for changes in `PlaybackState` and `MediaMetadata`.
    4.  **Infer User Intent:** By observing patterns like a quick pause/play or a track skip, the app can infer that the user wants to start the timer. For example, if the user resumes playback within a second of pausing, we trigger the timer.
    5.  **Request `BIND_NOTIFICATION_LISTENER_SERVICE` permission:** This is a sensitive permission that the user must grant through system settings.

*   **Concepts to learn:**
    *   **`NotificationListenerService`:** A service for observing system notifications and media sessions.
    *   **`MediaSessionManager`:** The primary class for managing media sessions.
    *   **`MediaController`:** Used to monitor and control an ongoing media session.
    *   **`PlaybackState` & `MediaMetadata`:** Key classes for understanding the state of a media player.

*   **Documentation:**
    *   [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
    *   [Working with media sessions](https://developer.android.com/develop/ui/views/media-and-entertainment/working-with-media-sessions)

### Step 4: Play Alarm and Schedule with AlarmManager

To ensure the timer is reliable and the alarm plays correctly, we use `AlarmManager` for scheduling and a simple `MediaPlayer` for the sound, letting Android handle audio mixing.

*   **Tasks:**
    1.  **Use `AlarmManager` for Scheduling:** Instead of relying on a `Coroutine` alone in the service, use `AlarmManager` to schedule an exact alarm. This is crucial for reliability, especially on newer Android versions with Doze mode.
    2.  **Play Sound with `MediaPlayer`:** When the timer finishes, use the `MediaPlayer` class to play a simple alarm sound. We don't request audio focus; we let Android mix our alarm with any other audio that might be playing.
    3.  **Handle Permissions:** For exact alarms on Android 12+, you may need to request the `SCHEDULE_EXACT_ALARM` permission.

*   **Concepts to learn:**
    *   **`AlarmManager`:** A system service for scheduling operations to happen at a later time, even if the app is not running.
    *   **Exact Alarms:** A category of alarms for time-sensitive tasks.
    *   **`MediaPlayer`:** A straightforward class for playing audio files.

*   **Documentation:**
    *   [Schedule alarms with AlarmManager](https://developer.android.com/training/scheduling/alarms)
    *   [MediaPlayer overview](https://developer.android.com/guide/topics/media/mediaplayer)

### Step 5: UI/UX Enhancements

This step is about creating a more polished and intuitive user experience with Jetpack Compose.

*   **Tasks:**
    1.  **Implement Scrollable Number Pickers:** Replace the `TextField`s with custom scrollable number pickers for a more tactile and visually appealing way to set the timer.
    2.  **Improve Permission Handling:** Create a clear and helpful UI for requesting necessary permissions (`POST_NOTIFICATIONS`, `BIND_NOTIFICATION_LISTENER_SERVICE`). Guide the user to the system settings if needed.
    3.  **Refine Visuals and Animations:** Add subtle animations to state changes (e.g., when the timer starts or finishes) to make the UI feel more responsive.

*   **Concepts to learn:**
    *   **Custom Layouts in Compose:** How to build your own custom UI components.
    *   **Compose Animations:** Using `animate*AsState`, `AnimatedVisibility`, etc., to add motion.
    *   **Permission Handling Best Practices:** Designing a user-friendly permission request flow.

*   **Documentation:**
    *   [Custom layouts and graphics in Compose](https://developer.android.com/jetpack/compose/layouts/custom)
    *   [Compose Animation](https://developer.android.com/jetpack/compose/animation)
    *   [Requesting Permissions](https://developer.android.com/training/permissions/requesting)

### Step 6: App Termination and Background Behavior

Understanding how the app behaves in different states is crucial for a reliable timer.

*   **Tasks:**
    1.  **Handle Task Removal:** In the `TimerService`, override `onTaskRemoved`. When the user swipes the app away from the recents screen, this method is called. Use it to stop the service and clean up resources, ensuring no orphaned processes are left.
    2.  **Ensure Correct Behavior on Phone Lock:** Because the timer runs in a foreground service, it continues to operate correctly even when the phone is locked. The notification keeps the user informed.
    3.  **Manage App Lifecycle:** Use a shared preference to track if the main app is running, so the `MediaSessionObserverService` knows when to trigger the timer.

*   **Concepts to learn:**
    *   **`onTaskRemoved`:** A callback in the `Service` class.
    *   **Android Process Lifecycle:** The states an app and its processes can be in.

*   **Documentation:**
    *   [Service `onTaskRemoved`](https://developer.android.com/reference/android/app/Service#onTaskRemoved(android.content.Intent))

### Step 7: Onboarding, UI, and State Management

This final step focuses on creating a polished, user-friendly interface and transparently managing app state and permissions.

*   **Tasks:**
    1.  **Polished UI with Scrollable Number Pickers:**
        *   Instead of `TextField`s, the UI uses two `LazyColumn` components to create scrollable number pickers for minutes and seconds. This provides a more tactile and intuitive way for the user to set the timer.
        *   The central item in the `LazyColumn` is visually emphasized (larger font size, brighter color) to indicate the selected value.
        *   The `LaunchedEffect` and `derivedStateOf` are used to automatically snap the picker to the nearest value when scrolling stops, ensuring a precise selection.

    2.  **Clear Permission Status with Colored Buttons:**
        *   The UI includes dedicated buttons for the "Notification/Media Observer" and "Alarm" permissions.
        *   These buttons use color to provide immediate visual feedback on the permission status: **green** for granted and **red** for denied.
        *   Tapping a button takes the user directly to the relevant system settings page, making it easy to grant the required permissions.

    3.  **State Management with `SharedPreferences`:**
        *   The app uses `SharedPreferences` to persist key pieces of information:
            *   **`KEY_APP_RUNNING`**: A boolean flag that indicates whether the app is in the foreground. This is crucial for the `MediaSessionObserverService` to know when it should infer a skip and start the timer.
            *   **Last Set Time**: The app saves the last timer value set by the user, so it can be restored the next time the app is opened.

*   **Key Composables and Concepts:**
    *   **`LazyColumn`:** Used to create the efficient and customizable number pickers. [Documentation](https://developer.android.com/jetpack/compose/lists)
    *   **`rememberLazyListState`:**  To control and observe the scroll state of the `LazyColumn`. [Documentation](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/package-summary#rememberlazyliststate)
    *   **`derivedStateOf`:** To efficiently calculate the central item of the number picker based on the scroll position. [Documentation](https://developer.android.com/jetpack/compose/side-effects#derivedstateof)
    *   **`LaunchedEffect`:**  To trigger the "snap-to-center" animation when the user stops scrolling. [Documentation](https://developer.android.com/jetpack/compose/side-effects#launchedeffect)
    *   **`Button` with `ButtonDefaults`:**  To create the permission buttons with custom colors. [Documentation](https://developer.android.com/reference/kotlin/androidx/compose/material3/Button)
    *   **`SharedPreferences`:** The standard Android API for storing simple key-value pairs. [Documentation](https://developer.android.com/training/data-storage/shared-preferences)
