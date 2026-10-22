/*
  DESK BUDDY - Bluetooth-only internet architecture
  ESP32 classic ESP32-WROOM + SSD1306 128x64 I2C OLED + TTP223 touch sensor.
  Android companion app fetches weather and sends current epoch time via Bluetooth SPP.
  Commands: MOOD <name>, NEXT, SCREEN face|weather|time, TIME <unix-seconds>,
            WEATHER <temp_C>|<humidity>|<weather_code>|<place>, NOTIFY <package>|<app label>, NOTIFYMSG|<sender>|<message>
  Libraries: Adafruit GFX, Adafruit SSD1306. Board: ESP32 by Espressif Systems.
*/

#include <BluetoothSerial.h>
#include <Wire.h>
#include <time.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

#define OLED_W 128
#define OLED_H 64
#define OLED_ADDR 0x3C
#define TOUCH_PIN 14
#define SDA_PIN 21
#define SCL_PIN 22

// Bluetooth-only architecture: the Android app fetches internet data and sends it over Bluetooth.

// Open-Meteo location defaults: Aligarh, India. Change if desired.
float WEATHER_LAT = 27.8974;
float WEATHER_LON = 78.0880;
const char* WEATHER_LABEL = "Aligarh";
const char* TZ_INFO = "IST-5:30"; // India Standard Time (POSIX TZ)

Adafruit_SSD1306 display(OLED_W, OLED_H, &Wire, -1);
BluetoothSerial SerialBT;
String btLine = "";

enum Mood : uint8_t { MOOD_AUTO, MOOD_HAPPY, MOOD_LOVE, MOOD_SURPRISED,
                      MOOD_SAD, MOOD_SLEEPY, MOOD_ANGRY, MOOD_COOL };
Mood mood = MOOD_AUTO;
enum ScreenMode : uint8_t { SCREEN_FACE, SCREEN_WEATHER, SCREEN_TIME };
ScreenMode screenMode = SCREEN_FACE; // Default: animated face only
const char* moodNames[] = {"Auto", "Happy", "Love", "Surprised", "Sad", "Sleepy", "Angry", "Cool"};

bool oledOK = false;
String notificationApp = "";
String notificationSender = "";
String notificationMessage = "";
bool notificationHasContent = false;
unsigned long notificationUntil = 0;
unsigned long notificationStageStarted = 0;
int notificationStage = 0;
String notificationIconHex = "";
bool touchPrev = false;
unsigned long touchDownAt = 0, lastTouchEdge = 0;
unsigned long moodUntil = 0, lastFrameAt = 0, lastClockAt = 0;
int eyeX = 0, eyeY = 0;
String weatherText = "Weather: waiting";
bool autoMood = true;
bool forceFace = false;
int faceIndex = -1;
const Mood cycleMoods[] = {MOOD_HAPPY, MOOD_LOVE, MOOD_SURPRISED, MOOD_SAD, MOOD_ANGRY, MOOD_SLEEPY, MOOD_COOL};
const int cycleCount = sizeof(cycleMoods) / sizeof(cycleMoods[0]);

String htmlEscape(const String& s) {
  String out = s;
  out.replace("&", "&amp;"); out.replace("<", "&lt;");
  out.replace(">", "&gt;"); out.replace("\"", "&quot;");
  return out;
}

String moodName(Mood m) {
  if (m <= MOOD_COOL) return String(moodNames[(int)m]);
  return "Auto";
}

void setMood(Mood m, unsigned long duration = 0) {
  mood = m;
  forceFace = (m != MOOD_AUTO);
  moodUntil = duration ? millis() + duration : 0;
}

void drawInfoScreen() {
  if (!oledOK) return;
  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);
  display.setTextSize(1);

  if (screenMode == SCREEN_WEATHER) {
    display.setCursor(0, 0);
    display.println("WEATHER");
    display.drawLine(0, 10, 127, 10, SSD1306_WHITE);
    int colon = weatherText.indexOf(": ");
    int cpos = weatherText.indexOf("C", colon >= 0 ? colon + 2 : 0);
    if (colon >= 0 && cpos > colon) {
      display.setTextSize(2);
      display.setCursor(0, 18);
      display.print(weatherText.substring(colon + 2, cpos + 1));
    } else {
      display.setCursor(0, 22);
      display.println("Waiting for weather");
    }
    display.setTextSize(1);
    display.setCursor(0, 48);
    display.println(weatherText.substring(0, 40));
  } else if (screenMode == SCREEN_TIME) {
    display.setCursor(0, 0);
    display.println("CURRENT TIME");
    display.drawLine(0, 10, 127, 10, SSD1306_WHITE);
    struct tm ti;
    if (getLocalTime(&ti, 50)) {
      char timeBuf[10], dateBuf[16];
      strftime(timeBuf, sizeof(timeBuf), "%H:%M:%S", &ti);
      strftime(dateBuf, sizeof(dateBuf), "%d-%m-%Y", &ti);
      display.setTextSize(2);
      display.setCursor(8, 24);
      display.println(timeBuf);
      display.setTextSize(1);
      display.setCursor(24, 50);
      display.println(dateBuf);
    } else {
      display.setCursor(0, 25);
      display.println("Waiting for phone time");
      display.println("Connect Bluetooth app");
    }
  }
  display.display();
}

void drawEyes(Mood m, int dx, int dy, bool blink) {
  if (!oledOK) return;
  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);

  // Full-screen face: no status bar, so the eyes can be larger.
  int cx1 = 34 + dx, cx2 = 94 + dx, cy = 31 + dy;
  cx1 = constrain(cx1, 18, 50); cx2 = constrain(cx2, 78, 110);
  cy = constrain(cy, 22, 39);

  switch (m) {
    case MOOD_ANGRY:
      display.fillRoundRect(cx1-16, cy-13, 32, blink?4:27, 7, SSD1306_WHITE);
      display.fillRoundRect(cx2-16, cy-13, 32, blink?4:27, 7, SSD1306_WHITE);
      // Angry brows slope down toward the centre.
      display.drawLine(cx1-17, cy-16, cx1+10, cy-8, SSD1306_BLACK);
      display.drawLine(cx2-10, cy-8, cx2+17, cy-16, SSD1306_BLACK);
      break;
    case MOOD_SLEEPY:
      display.fillRoundRect(cx1-14, cy-3, 28, blink?3:9, 5, SSD1306_WHITE);
      display.fillRoundRect(cx2-14, cy-3, 28, blink?3:9, 5, SSD1306_WHITE);
      break;
    case MOOD_SAD:
  // Droopy eyes
  display.fillRoundRect(cx1-15, cy-12, 30, blink?3:25, 7, SSD1306_WHITE);
  display.fillRoundRect(cx2-15, cy-12, 30, blink?3:25, 7, SSD1306_WHITE);

  // Worried eyebrows: raised toward the centre
  display.drawLine(cx1-16, cy-17, cx1+9, cy-10, SSD1306_BLACK);
  display.drawLine(cx2-9, cy-10, cx2+16, cy-17, SSD1306_BLACK);

  // Small tears below the outer corners
  if (!blink) {
    display.fillCircle(cx1-11, cy+17, 2, SSD1306_WHITE);
    display.fillCircle(cx2+11, cy+17, 2, SSD1306_WHITE);
  }

  // Clear sad frown: corners down, centre up
  display.drawLine(57, 55, 61, 51, SSD1306_WHITE);
  display.drawLine(61, 51, 67, 51, SSD1306_WHITE);
  display.drawLine(67, 51, 71, 55, SSD1306_WHITE);
  break;
    case MOOD_SURPRISED:
      if (blink) {
        display.drawLine(cx1-10, cy, cx1+10, cy, SSD1306_WHITE);
        display.drawLine(cx2-10, cy, cx2+10, cy, SSD1306_WHITE);
      } else {
        display.drawCircle(cx1, cy, 12, SSD1306_WHITE);
        display.drawCircle(cx2, cy, 12, SSD1306_WHITE);
        display.fillCircle(cx1+dx/2, cy+dy/2, 4, SSD1306_WHITE);
        display.fillCircle(cx2+dx/2, cy+dy/2, 4, SSD1306_WHITE);
      }
      break;
    case MOOD_LOVE:
      // Pixel-heart eyes
      for (int c : {cx1, cx2}) {
        display.fillCircle(c-4, cy-3, 5, SSD1306_WHITE);
        display.fillCircle(c+4, cy-3, 5, SSD1306_WHITE);
        display.fillTriangle(c-9, cy-1, c+9, cy-1, c, cy+11, SSD1306_WHITE);
        display.drawLine(57, 52, 60, 55, SSD1306_WHITE);
    display.drawLine(60, 55, 64, 56, SSD1306_WHITE);
    display.drawLine(64, 56, 68, 55, SSD1306_WHITE);
    display.drawLine(68, 55, 71, 52, SSD1306_WHITE);
      }
      break;
    case MOOD_COOL:
      display.fillRoundRect(cx1-15, cy-7, 30, 17, 4, SSD1306_WHITE);
      display.fillRoundRect(cx2-15, cy-7, 30, 17, 4, SSD1306_WHITE);
      display.drawLine(cx1+14, cy-2, cx2-14, cy-2, SSD1306_WHITE);
      break;
    case MOOD_HAPPY:
    case MOOD_AUTO:
    default:
      if (blink) {
        display.fillRoundRect(cx1-13, cy-2, 26, 4, 2, SSD1306_WHITE);
        display.fillRoundRect(cx2-13, cy-2, 26, 4, 2, SSD1306_WHITE);
      } else {
        display.fillRoundRect(cx1-16, cy-17, 32, 34, 9, SSD1306_WHITE);
        display.fillRoundRect(cx2-16, cy-17, 32, 34, 9, SSD1306_WHITE);
        display.fillCircle(cx1+dx/2, cy+dy/2, 4, SSD1306_BLACK);
        display.fillCircle(cx2+dx/2, cy+dy/2, 4, SSD1306_BLACK);
      }
      break;
  }

  // Tiny mouth
  if (m == MOOD_ANGRY) {
    display.drawLine(56, 55, 72, 55, SSD1306_WHITE);
    display.drawLine(56, 55, 60, 52, SSD1306_WHITE);
    display.drawLine(68, 52, 72, 55, SSD1306_WHITE);
  } else if (m == MOOD_HAPPY || m == MOOD_AUTO) {
    display.drawLine(57, 52, 60, 55, SSD1306_WHITE);
    display.drawLine(60, 55, 64, 56, SSD1306_WHITE);
    display.drawLine(64, 56, 68, 55, SSD1306_WHITE);
    display.drawLine(68, 55, 71, 52, SSD1306_WHITE);
  } else if (m == MOOD_SURPRISED) {
    display.drawCircle(64, 54, 3, SSD1306_WHITE);
  } else if (m == MOOD_SAD) {
    // Downturned mouth makes sadness visually distinct from anger.
   
  } else {
    display.drawLine(59, 54, 69, 54, SSD1306_WHITE);
  }

  display.display();
}

uint8_t hexNibble(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  return 0;
}

void drawNotification() {
  if (!oledOK) return;
  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);
  display.setTextSize(1);
  if (notificationStage == 0) {
    // Stage 1: actual 24x24 monochrome launcher icon + app label (1 second).
    if (notificationIconHex.length() == 144) {
      uint8_t bits[72];
      for (int i=0; i<72; i++)
        bits[i] = (hexNibble(notificationIconHex[i*2]) << 4) | hexNibble(notificationIconHex[i*2+1]);
      display.drawBitmap(52, 5, bits, 24, 24, SSD1306_WHITE);
    } else {
      display.drawRoundRect(52, 5, 24, 24, 5, SSD1306_WHITE);
      display.setCursor(56, 13); display.print("APP");
    }
    String app = notificationApp;
    if (app.length() > 20) app = app.substring(0,20);
    int16_t x1,y1; uint16_t w,h;
    display.getTextBounds(app,0,0,&x1,&y1,&w,&h);
    display.setCursor((128-(int)w)/2, 42);
    display.print(app);
    display.display();
    return;
  }
  if (notificationStage == 1) {
    // Stage 2: sender name for 2 seconds.
    display.setCursor(0, 0); display.print("MESSAGE FROM");
    display.drawLine(0, 10, 127, 10, SSD1306_WHITE);
    String sender = notificationSender;
    if (sender.length() > 21) sender = sender.substring(0,21);
    display.setTextSize(2);
    display.setCursor(0, 25); display.print(sender);
    display.display();
    return;
  }

  // Stage 3: message for 5 seconds; pages every 1.5s if needed.
  display.setTextSize(1);
  display.setCursor(0, 0); display.print(notificationSender.substring(0, 20));
  display.drawLine(0, 10, 127, 10, SSD1306_WHITE);
  const int charsPerLine = 21, rows = 6;
  String msg = notificationMessage;
  int totalLines = (msg.length() + charsPerLine - 1) / charsPerLine;
  if (totalLines < 1) totalLines = 1;
  int totalPages = (totalLines + rows - 1) / rows;
  int page = totalPages > 1 ? ((millis() - notificationStageStarted) / 1500UL) % totalPages : 0;
  int startChar = page * rows * charsPerLine;
  int y = 14;
  for (int row = 0; row < rows; row++) {
    int pos = startChar + row * charsPerLine;
    if (pos >= (int)msg.length()) break;
    String line = msg.substring(pos, min(pos + charsPerLine, (int)msg.length()));
    display.setCursor(0, y); display.print(line); y += 8;
  }
  if (totalPages > 1) { display.setCursor(116, 56); display.print(page+1); display.print("/"); display.print(totalPages); }
  display.display();
}

void applyPhoneTime(unsigned long long epochSeconds) {
  if (epochSeconds < 1700000000ULL) return;
  struct timeval tv;
  tv.tv_sec = (time_t)epochSeconds;
  tv.tv_usec = 0;
  settimeofday(&tv, nullptr);
  setenv("TZ", TZ_INFO, 1);
  tzset();
}

void handleBluetoothCommands() {
 while(SerialBT.available()) {
   char ch=(char)SerialBT.read();
   if(ch=='\r') continue;
   if(ch!='\n') { if(btLine.length()<480) btLine+=ch; else btLine=""; continue; }
   btLine.trim();
   if(btLine.startsWith("MOOD ")) {
     String m=btLine.substring(5); m.toLowerCase();
     if(m=="auto")setMood(MOOD_AUTO);else if(m=="happy")setMood(MOOD_HAPPY);else if(m=="love")setMood(MOOD_LOVE);else if(m=="surprised")setMood(MOOD_SURPRISED);else if(m=="sad")setMood(MOOD_SAD);else if(m=="sleepy")setMood(MOOD_SLEEPY);else if(m=="angry")setMood(MOOD_ANGRY);else if(m=="cool")setMood(MOOD_COOL);
     screenMode=SCREEN_FACE;
   } else if(btLine=="NEXT") { faceIndex=(faceIndex+1)%cycleCount; setMood(cycleMoods[faceIndex]); screenMode=SCREEN_FACE; }
   else if(btLine.startsWith("SCREEN ")) { String s=btLine.substring(7);s.toLowerCase();if(s=="face")screenMode=SCREEN_FACE;else if(s=="weather")screenMode=SCREEN_WEATHER;else if(s=="time")screenMode=SCREEN_TIME; }
   else if(btLine.startsWith("TIME ")) { String v=btLine.substring(5); applyPhoneTime(strtoull(v.c_str(),nullptr,10)); }
   else if(btLine.startsWith("WEATHER ")) {
     // Format: WEATHER <temperature_C>|<humidity_percent>|<weather_code>|<place>
     String p=btLine.substring(8); int a=p.indexOf('|'); int b=(a>=0)?p.indexOf('|',a+1):-1; int c=(b>=0)?p.indexOf('|',b+1):-1;
     if(a>0 && b>a && c>b) {
       String temp=p.substring(0,a), hum=p.substring(a+1,b), code=p.substring(b+1,c), place=p.substring(c+1);
       weatherText=place+": "+temp+"C, RH "+hum+"%, code "+code;
     }
   }
   else if(btLine.startsWith("NOTIFYSEQ|")) {
     String p = btLine.substring(10);
     int a=p.indexOf('|'), b=(a>=0)?p.indexOf('|',a+1):-1, c=(b>=0)?p.indexOf('|',b+1):-1;
     if(a>=0 && b>a && c>b) {
       notificationApp=p.substring(0,a);
       notificationIconHex=p.substring(a+1,b);
       notificationSender=p.substring(b+1,c);
       notificationMessage=p.substring(c+1);
       notificationApp.trim(); notificationSender.trim(); notificationMessage.trim();
       notificationHasContent=true; notificationStage=0;
       notificationStageStarted=millis(); notificationUntil=millis()+8000;
       screenMode=SCREEN_FACE;
     }
   }
   else if(btLine.startsWith("NOTIFYAPP|")) {
     String p=btLine.substring(10); int a=p.indexOf('|');
     if(a>=0) { notificationApp=p.substring(0,a); notificationIconHex=p.substring(a+1);
       notificationSender=notificationApp; notificationMessage="";
       notificationHasContent=true; notificationStage=0; notificationStageStarted=millis();
       notificationUntil=millis()+1000; screenMode=SCREEN_FACE; }
   }
   else if(btLine.startsWith("NOTIFYMSG|")) {
     String p = btLine.substring(10); int sep = p.indexOf('|');
     if (sep >= 0) {
       notificationApp="Notification"; notificationIconHex="";
       notificationSender=p.substring(0,sep); notificationMessage=p.substring(sep+1);
       notificationHasContent=true; notificationStage=1; notificationStageStarted=millis();
       notificationUntil=millis()+7000; screenMode=SCREEN_FACE;
     }
   }
   else if(btLine.startsWith("NOTIFY ")) {
     String p=btLine.substring(7); int sep=p.indexOf('|');
     String app=sep>=0?p.substring(sep+1):p;
     app.replace("\n"," "); app.replace("\r"," ");
     notificationApp=app.substring(0,22); notificationHasContent=false;
     notificationUntil=millis()+2800; screenMode=SCREEN_FACE;
   }
   btLine="";
 }
}

Mood currentMood() {
  if (moodUntil && millis() > moodUntil) {
    moodUntil = 0;
    setMood(MOOD_AUTO);
  }
  if (mood == MOOD_AUTO) return MOOD_HAPPY;
  return mood;
}

void handleTouch() {
  unsigned long now = millis();
  bool touched = digitalRead(TOUCH_PIN) == HIGH;

  if (touched && !touchPrev && now - lastTouchEdge > 120) {
    lastTouchEdge = now;
    touchDownAt = now;
  }

  // Long press (>= 700 ms): Face -> Weather -> Time -> Face.
  if (!touched && touchPrev && now - lastTouchEdge > 120) {
    unsigned long heldFor = now - touchDownAt;
    lastTouchEdge = now;
    if (heldFor >= 700) {
      // Long press cycles Face -> Weather -> Time -> Face.
      if (screenMode == SCREEN_FACE) {
        screenMode = SCREEN_WEATHER;
        
      } else if (screenMode == SCREEN_WEATHER) {
        screenMode = SCREEN_TIME;
      } else {
        screenMode = SCREEN_FACE;
      }
    } else {
      // Short tap cycles through all available expressions.
      if (screenMode == SCREEN_FACE) {
        faceIndex = (faceIndex + 1) % cycleCount;
        setMood(cycleMoods[faceIndex]);
      }
    }
  }
  touchPrev = touched;
}


void setup() {
  Serial.begin(115200);
  pinMode(TOUCH_PIN, INPUT);
  Wire.begin(SDA_PIN, SCL_PIN);

  oledOK = display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR);
  if (oledOK) {
    display.clearDisplay(); display.setTextColor(SSD1306_WHITE);
    display.setTextSize(1); display.setCursor(0, 0);
    display.println("Desk Buddy booting...");
    display.display();
  } else Serial.println("OLED not found: check address and wiring.");



  setenv("TZ", TZ_INFO, 1);
  tzset();
  SerialBT.begin("DeskBuddy");
  Serial.println("Bluetooth SPP ready: pair with DeskBuddy from Android settings.");

  lastFrameAt = 0;
  Serial.println("Desk Buddy ready: Bluetooth-only internet bridge; Android app supplies time/weather.");
  Serial.println("Short touch: next face. Long touch: Face -> Weather -> Time -> Face.");
}

void loop() {
  handleBluetoothCommands();
  handleTouch();

  unsigned long now = millis();
  if (now - lastFrameAt >= 90) {
    lastFrameAt = now;
    if (screenMode == SCREEN_FACE) {
      if (notificationUntil && (long)(notificationUntil - now) > 0) {
        if (notificationHasContent) {
          unsigned long elapsed = now - notificationStageStarted;
          if (notificationStage == 0 && elapsed >= 1000UL) { notificationStage = 1; notificationStageStarted = now; }
          else if (notificationStage == 1 && elapsed >= 2000UL) { notificationStage = 2; notificationStageStarted = now; }
          drawNotification();
        } else drawNotification();
      } else {
        notificationUntil = 0;
        bool blink = ((now % 4700) < 140);
        drawEyes(currentMood(), eyeX, eyeY, blink);
      }
    } else {
      drawInfoScreen();
    }
  }
  delay(4);
}
