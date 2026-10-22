# Hardware and wiring

## Components
- Classic Bluetooth-capable ESP32 (ESP32-WROOM recommended)
- SSD1306 128×64 I²C OLED, address `0x3C`
- TTP223 touch sensor
- Android phone running the Desk Buddy companion app

## Wiring
| Module | Pin | ESP32 pin |
|---|---|---|
| OLED | VCC | 3V3 (check your module specification) |
| OLED | GND | GND |
| OLED | SDA | GPIO 21 |
| OLED | SCL | GPIO 22 |
| TTP223 | VCC | 3V3 (check module specification) |
| TTP223 | GND | GND |
| TTP223 | OUT | GPIO 14 |

## Firmware libraries
Install **Adafruit GFX Library** and **Adafruit SSD1306** using Arduino IDE Library Manager. Install Espressif's ESP32 board package and select a compatible classic ESP32 board.

> Check the voltage requirements printed on your specific modules before wiring. This document is a guide; verify the actual board pinout before powering on.
