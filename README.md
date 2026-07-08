# Simple Timer

## Summary

Simple Timer is an Android application that provides a straightforward timer functionality with a unique hands-free control: it can be triggered by media playback actions, specifically a quick "pause and play" sequence on your headphones or any media controller. This allows you to start a timer without ever touching your phone, making it ideal for workouts, cooking, or any activity where your hands are busy.

## Goal of the Project

The primary goal is to create a robust timer that integrates seamlessly with the Android ecosystem. By observing media session states rather than intercepting hardware buttons, the app co-exists perfectly with music and podcast players like Spotify or YouTube Music.

## How to Use

The timer is designed for effortless, hands-free operation:

1.  **Set the Timer**: Use the custom **scrollable number pickers** to select minutes and seconds. The pickers feature smooth snapping and visual emphasis on the selected value.
2.  **Enable Permissions**: Grant the required "Notification/Media Observer" and "Alarm" permissions using the buttons on the main screen. The buttons provide immediate visual feedback:
    *   **Green**: Permission granted.
    *   **Red**: Permission required (tap to open system settings).
3.  **The Hands-Free Gesture**: 
    *   **Pause** your current media (music/podcast).
    *   **Resume (Play)** the media within **one second**.
4.  The timer starts automatically upon detecting this sequence. The app intelligently ignores track skips or volume changes to prevent false triggers.
5.  **Completion**: A beep sound will trigger once the countdown is complete, signaling the end of the timer.

## Quick Start

### Android Version Support
*   **Minimum OS**: Android 12.0 (API level 31) or higher.
*   **Target OS**: Android 15 (API level 35).

### Building and Installing
To get the app running on your device:

1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/yourusername/SimpleTimer.git
    ```
2.  **Open in Android Studio**: Open the project folder and allow Gradle to sync.
3.  **Build the APK**:
    *   Using Android Studio: Go to `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
    *   Using Command Line: Run `./gradlew assembleDebug`.
4.  **Install on Device**:
    *   Enable **Developer Options** and **USB Debugging** on your phone.
    *   Connect your phone via USB.
    *   Run `adb install app/build/outputs/apk/debug/app-debug.apk` or simply click the **Run** button in Android Studio.

## Project Structure

*   **`MainActivity.kt`**: Built with **Jetpack Compose**, featuring custom-built scrollable number pickers (`LazyColumn` with snapping) and dynamic permission status buttons.
*   **`TimerService.kt`**: A **Foreground Service** that acts as the single source of truth. It manages the countdown logic, persistent notification, and reliable alarms using `AlarmManager`.
*   **`MediaSessionObserverService.kt`**: A `NotificationListenerService` that monitors system media sessions to detect user intent via playback state changes.
*   **`TimerViewModel.kt`**: Manages UI state and facilitates communication with the background services.

## Key Features & Implementation

*   **Tactile UI**: Custom number pickers using `LazyColumn` and `derivedStateOf` for smooth, precise time selection.
*   **Reliable Background Operation**: As a Foreground Service, the timer remains active even when the screen is locked.
*   **Exact Timing**: Uses `AlarmManager.setExactAndAllowWhileIdle` to ensure the alarm triggers precisely, even in Doze mode.
*   **Intelligent Media Observation**: Filters out its own media sessions and includes a safety window to prevent accidental restarts after the timer finishes.
*   **Persistence**: Saves your last set timer value and tracking state in `SharedPreferences`.
*   **Lifecycle Awareness**: Gracefully handles app termination via `onTaskRemoved`, ensuring no orphaned processes or battery drain.

## App Behavior

### Phone Lock
The timer continues counting down when the phone is locked. A persistent notification on the lock screen provides real-time updates and a "Cancel" action.

### App Close
Swiping the app away from the "Recents" screen stops the services and cleans up resources. The app tracks its running state to ensure the media observer only triggers the timer when the app is intended to be active.
