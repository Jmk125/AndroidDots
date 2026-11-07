# Motion Dots

An Android take on iOS "Vehicle Motion Cues": dots float over every app and slide with the
phone's motion (accelerometer), so your eyes see the movement your inner ear feels.

## Features
- Full-screen, touch-through overlay that works on top of any app
- Patterns: left & right edges, full frame, grid, ring
- Customise dot count, size, opacity, colour, edge distance, outline, sensitivity,
  responsiveness (spring snappiness), invert direction, dim-when-still
- Live preview inside the app; changes apply instantly to the running overlay
- Quick Settings tile for one-swipe on/off
- Sensors pause while the screen is off to save battery
- Handles screen rotation

## Build
Open in Android Studio (Hedgehog or newer) and run, or:

    gradle :app:assembleDebug     # APK in app/build/outputs/apk/debug/

Every push also builds a debug APK in GitHub Actions (see the "Build APK" workflow artifacts).

## Use
1. Install, open the app, tap **Start dots**.
2. Grant "Display over other apps" when prompted (and notifications on Android 13+).
3. Add the **Motion Dots** tile in the Quick Settings panel for fast toggling.
