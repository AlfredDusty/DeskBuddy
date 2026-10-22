# Desk Buddy Android + ESP32 project

This Android Studio project contains the Desk Buddy phone app and Bluetooth-only ESP32 firmware.

## Android app screens
- Home: connection status, expression buttons, Next Face, and quick-action cards.
- Settings: About, Theme, Notification Settings, Weather, and Acknowledgments.
- Themes: Light, Dark, and System Default (saved on the device).
- Notification bridge: Android notification-listener access and optional ongoing notifications.
- Weather: fetched on the phone with Open-Meteo and sent to the ESP32 over Bluetooth Classic SPP.

## Open in Android Studio
1. Extract this archive.
2. In Android Studio, choose **Open** and select the `deskbuddy_pixel_project` folder (the one containing `settings.gradle`).
3. Allow Gradle sync to finish.
4. Build and run on an Android phone. Grant Nearby devices/Bluetooth permissions and pair the ESP32 as `DeskBuddy` first.
5. For phone notifications, open Settings > Notification Settings > Enable Notification Access and grant access.

## ESP32 firmware
The `Desk_Buddy_Bluetooth_Only.ino` sketch is included. Open it in Arduino IDE, select the matching ESP32 board and port, and upload it.

## Before public release
- Add your actual GitHub repository URL to the About page handler in `app/src/main/java/com/example/deskbuddy/MainActivity.java` (search for `Add your GitHub repository URL`).
- Verify all acknowledgments and licenses against the exact libraries used.
- Test Bluetooth, weather, notification access, theme switching, and screen navigation on your own phone.
- Create a signed Android App Bundle from Android Studio for Play Console.

Note: this source archive has been updated, but it has not been compiled in this environment. Please run **Build > Make Project** in Android Studio and share any errors so they can be corrected before publishing.
