# Desk Buddy 🤖

A DIY desktop companion built around an ESP32, a 128×64 OLED, a touch sensor, and an Android companion app. The phone provides internet-backed weather data and sends information to the ESP32 over Bluetooth Classic; the ESP32 itself does not need Wi‑Fi for this architecture.

> **Project status:** source package prepared for community review. The Android project has **not been verified by a successful build in this environment**. Build and test it in Android Studio before relying on it or publishing it.

## Features

- Animated OLED buddy faces and mood selection
- Bluetooth Classic Serial Port Profile (SPP) communication
- Phone-side time/weather updates
- Optional Android notification forwarding (requires notification access)
- Home and Settings screens, with About, Theme, Notification Settings, Weather, and Acknowledgments sections in the current source
- Light, Dark, and System Default theme options in the current source

## Repository layout

- [`android-app/`](android-app/) — Android Studio project and Java source
- [`esp32-firmware/`](esp32-firmware/) — Arduino `.ino` firmware for the ESP32
- [`docs/HARDWARE.md`](docs/HARDWARE.md) — wiring and hardware notes
- [`assets/`](assets/) — project mascot concept artwork

## Hardware

- Classic Bluetooth-capable ESP32 (for example, ESP32-WROOM)
- SSD1306 128×64 I²C OLED (default address `0x3C`)
- TTP223 touch sensor
- Android phone with Bluetooth

See [hardware and wiring](docs/HARDWARE.md).

## Run the ESP32 firmware

1. Install Arduino IDE and the Espressif ESP32 board package.
2. Install **Adafruit GFX Library** and **Adafruit SSD1306** from Library Manager.
3. Open `esp32-firmware/Desk_Buddy_Bluetooth_Only.ino`.
4. Select your compatible ESP32 board and serial port.
5. Build/upload and open Serial Monitor if debugging is needed.

## Open the Android app

1. Open Android Studio.
2. Choose **Open** and select the `android-app/` folder containing `settings.gradle`.
3. Allow Gradle sync to complete.
4. Choose **Build → Make Project** and resolve any build errors before installing.
5. Pair your ESP32 over Bluetooth (the firmware advertises as `DeskBuddy`), then grant the Bluetooth/Nearby Devices permissions requested by Android.
6. To forward phone notifications, enable Desk Buddy under Android's **Notification access** settings.

Android versions and vendor settings vary. Grant only permissions you understand and want to use.

## Downloads and Play Store

The Google Play Store link will be added here after the app is published. Until then, build the Android app from source using the instructions above.

**Play Store:** _Coming after release._

## GitHub repository URL

After creating the repository, replace this line with its URL and update the Android app's About page link in `android-app/app/src/main/java/com/example/deskbuddy/MainActivity.java` (search for the GitHub placeholder).

## License

No license has been selected yet. Until a license is added, do not assume you have permission to redistribute or publish modified copies. See the license discussion in the setup notes before choosing one.

## Acknowledgments

This project uses open-source Android/Gradle tooling and the Adafruit GFX and SSD1306 Arduino libraries. Please check the exact versions and their respective licenses before release, and add any additional libraries actually used by your final build.
