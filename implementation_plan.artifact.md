# Fix Notification Icon

The current notification icon is using the full-color app launcher foreground, which Android renders as a solid white block in the status bar. This plan creates a dedicated, simplified vector icon for notifications.

## Proposed Changes

### [app]

#### [NEW] [ic_notification.xml](file:///C:/Users/gggiu/AndroidStudioProjects/PocketTimer/app/src/main/res/drawable/ic_notification.xml)
- Create a 24x24dp vector drawable.
- Include a white path representing the letters "PT" with a transparent background.

#### [MODIFY] [TimerService.kt](file:///C:/Users/gggiu/AndroidStudioProjects/PocketTimer/app/src/main/java/com/gonzalogiulio/pockettimer/TimerService.kt)
- Update `createNotification` to use `R.drawable.ic_notification` instead of `R.drawable.ic_launcher_foreground`.

## Verification Plan

### Manual Verification
- Build and run the app.
- Start the timer to trigger the foreground service notification.
- Verify the notification icon in the status bar and notification drawer shows the "PT" logo clearly.
