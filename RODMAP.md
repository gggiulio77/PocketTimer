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

### Step 3: Implement Headphone Controls with `MediaSession`

This is the core feature. Your service will create a media session to listen for headphone button presses.

*   **Tasks:**
    1.  **Create a MediaSession:** In your service, create and initialize a `MediaSessionCompat` object.
    2.  **Implement Callbacks:** Create a `MediaSessionCompat.Callback` to handle media button events.
        *   `onPlay()`: A single click starts the timer. A second, consecutive click will reset the timer.
        *   `onPause()`: A single click resets the timer.
    3.  **Set Session Active:** Make your media session the active one by calling `setActive(true)`.
        *   **Key Insight:** To reliably control the timer from a locked screen without interfering with other media apps, the session is made active when the app starts and, most importantly, is re-activated every time the screen is turned off by listening for the `ACTION_SCREEN_OFF` broadcast. This ensures the app has control when it's most needed.
    4.  **Declare a Media Button Receiver:** In your `AndroidManifest.xml`, declare a `BroadcastReceiver` for `Intent.ACTION_MEDIA_BUTTON`. This is crucial for receiving button events when your app is in the background.

*   **Concepts to learn:**
    *   **MediaSessionCompat:** The standard way to interact with Android's media system.
    *   **MediaSession Callbacks (`onPlay`, `onPause`):** The methods that are triggered by media controls.
    *   **MediaButtonReceiver:** The component that listens for media button broadcasts system-wide.
    *   **BroadcastReceiver:** For listening to system-wide events like the screen turning off (`ACTION_SCREEN_OFF`).

*   **Documentation:**
    *   [Guide to media sessions](https://developer.android.com/guide/topics/media-apps/media-apps-overview)
    *   [Respond to media buttons](https://developer.android.com/guide/topics/media-apps/mediabuttons)

### Step 4: Play the Alarm with Audio Focus

When the timer finishes, you need to play a sound. To do this politely without abruptly stopping the user's music, you'll request "audio focus".

*   **Tasks:**
    1.  **Request Audio Focus:** When the timer in your service reaches zero, get an instance of `AudioManager` and request audio focus. You should request `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, which tells other apps to lower their volume temporarily.
    2.  **Play Sound:** Once you get audio focus, use the `MediaPlayer` class to play your generic beep sound.
    3.  **Release Focus:** After your sound has finished playing, release the audio focus so the other app can return to its normal volume.

*   **Concepts to learn:**
    *   **Audio Focus:** The system for managing audio playback between multiple apps.
    *   **AudioManager:** The class used to manage audio, including focus.
    *   **MediaPlayer:** A simple class for playing audio files.

*   **Documentation:**
    *   [Managing audio focus](https://developer.android.com/guide/topics/media-apps/audio-focus)
    *   [MediaPlayer overview](https://developer.android.com/guide/topics/media/mediaplayer)

### Step 5: Onboarding and Polish

The final step is to make the app user-friendly by being transparent about its behavior.

*   **Tasks:**
    1.  **Create an Onboarding Screen:** On the first launch, show a simple screen or dialog.
    2.  **Explain the Behavior:** Clearly. Explain that the app will take over the headphone buttons to control the timer and that this will prevent them from controlling music apps.
    3.  **Refine UI/UX:** Test the app and make sure it's intuitive and handles edge cases gracefully (e.g., what happens if the user denies the notification permission?).
