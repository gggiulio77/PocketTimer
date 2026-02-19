# Simple Timer

## Summary

Simple Timer is an Android application that provides a straightforward timer functionality with a unique twist: it can be controlled through media playback actions, such as skipping a track on your headphones. This allows you to start a timer without ever touching your phone, making it ideal for situations like workouts, cooking, or any hands-free activity.

## Goal of the Project

The primary goal of this project is to create a timer that is both simple to use and powerful enough to integrate into a user's existing workflow. By observing media session events instead of hijacking media buttons, the app co-exists peacefully with music or podcast apps, offering a seamless user experience.

## How to Use

The timer is controlled by a specific hands-free gesture using your headphone controls or any other media controller:

1.  **Pause** the current media (music, podcast, etc.).
2.  **Play** the media again within one second.

This quick **pause-and-play** sequence is interpreted as the signal to start the timer. Any other media control actions, such as skipping to the next or previous track, will be ignored and will not trigger the timer.

## Project Structure

The application is built around a few key components that separate concerns and ensure reliability:

*   **`MainActivity.kt`**: The single entry point for the UI, built with Jetpack Compose. It's responsible for displaying the timer, handling user input, and managing permissions.
*   **`TimerService.kt`**: A foreground `Service` that acts as the single source of truth for the timer's state and logic. It runs independently of the UI, ensuring the timer continues even if the app is in the background or the phone is locked.
*   **`MediaSessionObserverService.kt`**: A `NotificationListenerService` that listens to changes in the system's media sessions. It infers user intent to start the timer.

## App Services & Responsibilities

### TimerService
This is the core of the application.

*   **Responsibility**: Manages the timer's lifecycle (start, reset), holds the current countdown state, and displays a persistent notification while running.
*   **Implementation**: It runs as a foreground service to stay alive when the app is backgrounded. It uses `AlarmManager` to reliably schedule the end of the timer, making it robust against system optimizations like Doze mode. When the timer finishes, it plays an alarm sound using `MediaPlayer`.
*   **Communication**: The UI binds to this service to display the timer's state and send commands (start, reset).

### MediaSessionObserverService
This service provides the "hands-free" control feature.

*   **Responsibility**: To observe media control events from other applications (like Spotify, YouTube Music, etc.) and decide when to start the timer.
*   **Implementation**: It's a `NotificationListenerService` that gets callbacks when media playback state or metadata changes. It contains logic to infer a user action from a rapid sequence of pause and play events.
*   **Relationship**: When this service infers a user action, it sends an `Intent` to start the `TimerService`. It only does this when it knows the main application is running, a state tracked via `SharedPreferences`.

## App Behavior

### Phone Lock
When the phone is locked, the timer continues to run uninterrupted. The `TimerService` is a foreground service, which is prioritized by the Android system. A notification remains visible on the lock screen, showing the current countdown.

### App Close
If the user closes the app from the "recents" screen, the `onTaskRemoved` method within `TimerService` is triggered. This gracefully stops the timer, dismisses the notification, and stops the service itself to prevent it from running indefinitely in the background and consuming resources. The application also updates a `SharedPreferences` flag to indicate that the app is no longer active, preventing the `MediaSessionObserverService` from starting a new timer unintentionally.
