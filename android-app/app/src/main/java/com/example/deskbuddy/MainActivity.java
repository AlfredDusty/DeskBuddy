package com.example.deskbuddy;

import android.app.Activity;
import android.bluetooth.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import android.content.Intent;
import android.net.Uri;

public class MainActivity extends Activity {
    public static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    static volatile OutputStream output;
    static volatile boolean connected = false;

    private static final int BG = Color.rgb(244, 246, 255);
    private static final int INK = Color.rgb(29, 35, 66);
    private static final int MUTED = Color.rgb(105, 112, 140);
    private static final int PURPLE = Color.rgb(101, 77, 230);
    private static final int TEAL = Color.rgb(15, 157, 154);
    private static final int CARD = Color.WHITE;

    TextView status, weatherStatus, homeWeatherTitle, homeWeatherDetail;
    EditText deviceName, location;
    Handler handler = new Handler(Looper.getMainLooper());
    long lastWeather = 0;
    boolean running = true;

    Runnable syncTask = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (connected) {
                send("TIME " + (System.currentTimeMillis() / 1000L));
                if (System.currentTimeMillis() - lastWeather > 15 * 60 * 1000L) fetchWeather();
            }
            handler.postDelayed(this, 60_000L);
        }
    };

    android.content.SharedPreferences prefs;
    LinearLayout root, content, bottomNav;
    String currentPage = "home";
    boolean dark = false;
    int pageBg, ink, muted, cardColor, soft, purple, teal, orange, pink;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("deskbuddy_ui", MODE_PRIVATE);
        dark = prefs.getString("theme", "system").equals("dark") ||
                (prefs.getString("theme", "system").equals("system") && (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        applyPalette();
        getWindow().setStatusBarColor(dark ? Color.rgb(18,20,42) : Color.rgb(39,35,92));
        getWindow().setNavigationBarColor(dark ? Color.rgb(18,20,42) : Color.rgb(39,35,92));
        if (Build.VERSION.SDK_INT >= 31 &&
                (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN}, 42);
        }
        showPage("home");
        handler.postDelayed(() -> fetchWeather(), 900L);
        handler.postDelayed(syncTask, 1500L);
    }

    private void applyPalette() {
        pageBg = dark ? Color.rgb(18,20,42) : Color.rgb(242,245,255);
        ink = dark ? Color.rgb(245,245,255) : Color.rgb(29,35,66);
        muted = dark ? Color.rgb(174,180,207) : Color.rgb(105,112,140);
        cardColor = dark ? Color.rgb(34,38,65) : Color.WHITE;
        soft = dark ? Color.rgb(49,48,83) : Color.rgb(242,240,255);
        purple = Color.rgb(101,77,230); teal = Color.rgb(15,157,154);
        orange = Color.rgb(222,126,49); pink = Color.rgb(201,75,125);
    }

    private void showPage(String which) {
        currentPage = which; applyPalette();
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(pageBg);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(18),dp(14),dp(18),dp(14));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(pageBg); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        if (which.equals("home")) buildHome(); else if (which.equals("settings")) buildSettings(); else if (which.equals("about")) buildAbout(); else if (which.equals("theme")) buildTheme(); else if (which.equals("notifications")) buildNotifications(); else if (which.equals("weather")) buildWeather(); else if (which.equals("credits")) buildCredits();
        bottomNav = new LinearLayout(this); bottomNav.setOrientation(LinearLayout.HORIZONTAL); bottomNav.setGravity(Gravity.CENTER); bottomNav.setPadding(dp(8),dp(8),dp(8),dp(8)); bottomNav.setBackground(rounded(cardColor,18)); bottomNav.setElevation(dp(4));
        Button home = button("⌂\nHome", which.equals("home")?purple:cardColor, which.equals("home")?Color.WHITE:muted); Button settings = button("⚙\nSettings", which.equals("settings")?purple:cardColor, which.equals("settings")?Color.WHITE:muted);
        bottomNav.addView(home,new LinearLayout.LayoutParams(0,dp(48),1)); bottomNav.addView(settings,new LinearLayout.LayoutParams(0,dp(48),1));
        home.setOnClickListener(v->showPage("home")); settings.setOnClickListener(v->showPage("settings"));
        root.addView(bottomNav,params(-1,-2,12,0,12,8)); setContentView(root);
    }

    private void buildHome() {
        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(20),dp(20),dp(20),dp(20)); hero.setBackground(gradient(new int[]{Color.rgb(55,48,130),purple},26));
        LinearLayout titleRow = new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("PIXEL BUDDY  •  DESK COMPANION",11,Color.rgb(220,216,255),true)); titles.addView(text("Desk Buddy",31,Color.WHITE,true)); titles.addView(text("Your tiny desktop companion.",14,Color.rgb(233,230,255),false));
        titleRow.addView(titles,new LinearLayout.LayoutParams(0,-2,1)); Button gear=button("⚙",Color.WHITE,purple); titleRow.addView(gear,new LinearLayout.LayoutParams(dp(48),dp(48))); gear.setOnClickListener(v->showPage("settings")); hero.addView(titleRow);
        LinearLayout statusPill=new LinearLayout(this); statusPill.setGravity(Gravity.CENTER_VERTICAL); statusPill.setPadding(dp(12),dp(9),dp(12),dp(9)); statusPill.setBackground(rounded(Color.rgb(67,58,143),18)); status=text(connected?"●  Connected to DeskBuddy":"●  Not connected · pair DeskBuddy first",13,Color.WHITE,true); statusPill.addView(status); hero.addView(statusPill,params(-1,-2,0,14,0,0)); content.addView(hero,params(-1,-2,0,0,0,14));
        LinearLayout connectRow=new LinearLayout(this); deviceName=new EditText(this); deviceName.setSingleLine(true); deviceName.setText(prefs.getString("device","DeskBuddy")); deviceName.setHint("Paired device name"); styleEdit(deviceName); deviceName.setBackground(rounded(cardColor,14)); connectRow.addView(deviceName,new LinearLayout.LayoutParams(0,dp(50),1)); Button connect=button("CONNECT",purple,Color.WHITE); connectRow.addView(connect,params(dp(116),dp(50),8,0,0,0)); connect.setOnClickListener(v->{prefs.edit().putString("device",deviceName.getText().toString()).apply();connectDevice();}); content.addView(connectRow,params(-1,-2,0,0,0,14));
        LinearLayout face=card(content,"Expressions","Tap a mood to change your Desk Buddy's face."); String[][] moods={{"😊  Happy","happy"},{"💜  Love","love"},{"😮  Surprised","surprised"},{"🥺  Sad","sad"},{"😴  Sleepy","sleepy"},{"😠  Angry","angry"},{"😎  Cool","cool"},{"✨  Auto","auto"}};
        for(int i=0;i<moods.length;i+=2){LinearLayout row=new LinearLayout(this); for(int j=i;j<i+2;j++){final String mood=moods[j][1]; Button b=button(moods[j][0],soft,ink); row.addView(b,new LinearLayout.LayoutParams(0,dp(46),1)); if(j==i)row.addView(new View(this),new LinearLayout.LayoutParams(dp(8),1)); b.setOnClickListener(v->send("MOOD "+mood));} face.addView(row,params(-1,-2,0,0,0,7));}
        Button next=button("↻   NEXT FACE",ink,Color.WHITE); next.setOnClickListener(v->send("NEXT")); face.addView(next,params(-1,dp(46),0,2,0,0));
        LinearLayout quick=card(content,"Quick Actions","Your most-used controls, all in one place."); LinearLayout r1=new LinearLayout(this),r2=new LinearLayout(this); addQuick(r1,"☁","Weather","Show on Desk Buddy","weather_device",Color.rgb(232,228,255),purple); addQuick(r1,"♧","Notifications","Manage alerts","notifications",Color.rgb(220,246,241),teal); addQuick(r2,"◷","Time & Date","Show time on device","time",Color.rgb(255,239,220),orange); addQuick(r2,"♡","Expressions","Choose a face","home",Color.rgb(252,229,237),pink); quick.addView(r1,params(-1,-2,0,0,0,7)); quick.addView(r2);
        TextView foot=text("BLUETOOTH ONLY  ·  NO ESP32 WI-FI REQUIRED",10,muted,true); foot.setGravity(Gravity.CENTER); foot.setPadding(0,dp(15),0,0); content.addView(foot);
    }
    private void addQuick(LinearLayout row,String icon,String title,String sub,String target,int bg,int fg){ LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(11),dp(10),dp(7),dp(10)); box.setBackground(rounded(dark?soft:bg,14)); box.addView(text(icon+"  "+title,14,dark?ink:fg,true)); box.addView(text(sub,10,muted,false)); row.addView(box,new LinearLayout.LayoutParams(0,dp(68),1)); if(row.getChildCount()==1)row.addView(new View(this),new LinearLayout.LayoutParams(dp(7),1)); box.setOnClickListener(v->{if(target.equals("time")){if(!connected){Toast.makeText(this,"Connect to Desk Buddy first",Toast.LENGTH_SHORT).show();return;}send("SCREEN time");Toast.makeText(this,"Time display sent to Desk Buddy",Toast.LENGTH_SHORT).show();}else if(target.equals("weather_device")){if(!connected){Toast.makeText(this,"Connect to Desk Buddy first",Toast.LENGTH_SHORT).show();return;}send("SCREEN weather");fetchWeather();Toast.makeText(this,"Showing weather on Desk Buddy",Toast.LENGTH_SHORT).show();}else showPage(target);}); }

    private void buildSettings(){ pageTitle("Settings","Customize your Desk Buddy experience."); addSetting("ⓘ","About","Project info, setup guide, GitHub link","about",purple); addSetting("☼","Theme","Light, Dark or System theme","theme",orange); addSetting("♟","Notification Settings","Manage notification access and options","notifications",teal); addSetting("☁","Weather","City, refresh, and display options","weather",orange); addSetting("♥","Acknowledgments","Credits and open-source libraries","credits",pink); }
    private void pageTitle(String title,String subtitle){ LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL); Button back=button("‹   Back",cardColor,ink);back.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);back.setOnClickListener(v->showPage("home"));head.addView(back,params(-1,dp(42),0,0,0,10));head.addView(text(title,28,ink,true));TextView sub=text(subtitle,13,muted,false);sub.setPadding(0,dp(4),0,dp(16));head.addView(sub);content.addView(head); }
    private void addSetting(String icon,String title,String sub,String target,int color){ LinearLayout item=new LinearLayout(this);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(14),dp(13),dp(12),dp(13));item.setBackground(rounded(cardColor,17));TextView badge=text(icon,21,Color.WHITE,true);badge.setGravity(Gravity.CENTER);badge.setBackground(rounded(color,40));item.addView(badge,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(12),0,dp(4),0);labels.addView(text(title,15,ink,true));labels.addView(text(sub,11,muted,false));item.addView(labels,new LinearLayout.LayoutParams(0,-2,1));item.addView(text("›",26,muted,false));content.addView(item,params(-1,-2,0,0,0,10));item.setOnClickListener(v->showPage(target)); }
    private void buildAbout(){pageTitle("About","Everything you need to know about Desk Buddy.");LinearLayout c=card(content,"Desk Buddy · v2.0","Your tiny desktop companion.");TextView desc=text("Desk Buddy is a DIY desktop companion powered by an ESP32. It uses an OLED display to show a customizable animated face, time and weather information, and phone notifications received through this Android app over Bluetooth.",13,ink,false);c.addView(desc,params(-1,-2,0,4,0,12));Button gh=button("◉  View on GitHub  ↗",purple,Color.WHITE);gh.setOnClickListener(v -> {Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AlfredDusty/DeskBuddy"));startActivity(intent);
        });c.addView(gh,params(-1,dp(46),0,0,0,12)); c.addView(text("FEATURES",16,ink,true));for(String x:new String[]{"☺  Customizable buddy faces","☁  Time and weather updates","♧  Phone notification display","ᛒ  Bluetooth communication","☝  Touch sensor interaction"})c.addView(text(x,13,muted,false),params(-1,-2,0,9,0,0));c.addView(text("HARDWARE REQUIREMENTS",16,ink,true),params(-1,-2,0,18,0,5));for(String x:new String[]{"ESP32 development board","SSD1306 128 × 64 OLED display","TTP223 touch sensor","Android phone with Bluetooth"})c.addView(text("•  "+x,13,muted,false),params(-1,-2,0,5,0,0)); }
    private void buildTheme(){pageTitle("Theme","Choose how Desk Buddy looks.");LinearLayout c=card(content,"Choose App Theme","Your choice is saved on this phone.");String chosen=prefs.getString("theme","system");RadioGroup group=new RadioGroup(this);for(String[] opt:new String[][]{{"☀  Light","light","Use light theme"},{"☾  Dark","dark","Use dark theme"},{"⚙  System Default","system","Follow your phone's setting"}}){RadioButton rb=new RadioButton(this);rb.setText(opt[0]+"\n"+opt[2]);rb.setTextColor(ink);rb.setPadding(dp(10),dp(8),dp(8),dp(8));rb.setButtonTintList(android.content.res.ColorStateList.valueOf(purple));rb.setTag(opt[1]);rb.setChecked(chosen.equals(opt[1]));group.addView(rb,params(-1,dp(65),0,0,0,7));}c.addView(group);group.setOnCheckedChangeListener((g,id)->{RadioButton rb=g.findViewById(id);if(rb!=null){prefs.edit().putString("theme",String.valueOf(rb.getTag())).apply();showPage("theme");}});LinearLayout preview=card(content,"Preview","The selected theme applies across all app screens.");preview.addView(text("Desk Buddy  ·  Your tiny desktop companion.",15,ink,true));preview.addView(text("Cards, buttons, and navigation update together.",12,muted,false),params(-1,-2,0,6,0,0)); }
    private void buildNotifications(){pageTitle("Notification Settings","Choose what appears on your Desk Buddy OLED.");LinearLayout c=card(content,"Notification Access","Allow the app to read notifications and send them to Desk Buddy.");Button access=button("ENABLE NOTIFICATION ACCESS",soft,purple);access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));c.addView(access,params(-1,dp(46),0,0,0,10));LinearLayout test=card(content,"Test Notification","Send a test message to your Desk Buddy.");Button tb=button("SEND TEST MESSAGE",Color.rgb(255,239,220),Color.rgb(143,78,20));tb.setOnClickListener(v->send("NOTIFYMSG|Desk Buddy test|Hello! This is a test message. Longer messages will be shown in pages."));test.addView(tb,params(-1,dp(46),0,0,0,0));android.content.SharedPreferences np=getSharedPreferences("deskbuddy_notifications",MODE_PRIVATE);LinearLayout options=card(content,"Display Options","Customize the notification bridge.");CheckBox ongoing=new CheckBox(this);ongoing.setText("Allow ongoing notifications (downloads, playback, etc.)");ongoing.setTextColor(ink);ongoing.setButtonTintList(android.content.res.ColorStateList.valueOf(purple));ongoing.setChecked(np.getBoolean("include_ongoing",false));ongoing.setOnCheckedChangeListener((b,v)->np.edit().putBoolean("include_ongoing",v).apply());options.addView(ongoing);options.addView(text("Ongoing notifications are ignored by default. Each is shown once, not repeatedly for progress updates. Notifications disappear from the OLED after about 6.5 seconds. Android must expose the text for it to be shown.",12,muted,false),params(-1,-2,0,4,0,8)); }
    private void buildWeather(){pageTitle("Weather","Fetch a city forecast on your phone and send it over Bluetooth.");LinearLayout c=card(content,"City","Enter a city to get the latest forecast.");location=new EditText(this);location.setSingleLine(true);location.setText(prefs.getString("city","Aligarh"));styleEdit(location);location.setBackground(rounded(soft,14));c.addView(location,params(-1,dp(48),0,0,0,10));Button refresh=button("↻  REFRESH WEATHER",teal,Color.WHITE);refresh.setOnClickListener(v->{prefs.edit().putString("city",location.getText().toString()).apply();fetchWeather();});c.addView(refresh,params(-1,dp(46),0,0,0,10));weatherStatus=text("Weather is fetched on your phone.",13,muted,false);c.addView(weatherStatus); }
    private void buildCredits(){pageTitle("Acknowledgments","Thanks to the open-source community.");LinearLayout c=card(content,"Open-source resources","Desk Buddy builds on the work of these projects.");for(String x:new String[]{"ESP32 Arduino Core — ESP32 platform support","Adafruit SSD1306 — OLED display library","Adafruit GFX — graphics primitives","Open-Meteo — weather and geocoding API","Android Bluetooth and Notification APIs","All contributors and the open-source community"})c.addView(text("♥  "+x,13,ink,false),params(-1,-2,0,8,0,7));c.addView(text("Add or update credits to match the exact libraries and licenses included in your final release.",11,muted,false),params(-1,-2,0,8,0,0)); }
    private void styleEdit(EditText e){e.setTextColor(ink);e.setHintTextColor(muted);e.setPadding(dp(13),0,dp(13),0);e.setSingleLine(true);}
    private GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}

    private LinearLayout card(LinearLayout page, String title, String subtitle) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(15), dp(15), dp(15), dp(15));
        c.setBackground(rounded(cardColor, 22));
        c.setElevation(dp(2));
        page.addView(c, params(-1, -2, 0, 0, 0, 15));
        TextView h = text(title, 20, ink, true);
        c.addView(h);
        TextView s = text(subtitle, 12, muted, false);
        s.setPadding(0, dp(4), 0, dp(13));
        c.addView(s);
        return c;
    }

    private void addScreenButton(LinearLayout row, String label, String command, int color) {
        Button b = button(label, color, Color.WHITE);
        row.addView(b, new LinearLayout.LayoutParams(0, dp(43), 1));
        if (row.getChildCount() < 3) row.addView(new View(this), new LinearLayout.LayoutParams(dp(7), 1));
        b.setOnClickListener(v -> send(command));
    }

    private Button button(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(fg);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(gradient(new int[]{bg, bg}, 14));
        b.setElevation(dp(1));
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable gradient(int[] colors, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout.LayoutParams params(int w, int h, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    void connectDevice() {
        if (Build.VERSION.SDK_INT >= 31 &&
                (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN}, 42);
            status.setText("Allow Nearby devices, then tap Connect again.");
            return;
        }
        status.setText("  Connecting…");
        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) throw new Exception("This phone has no Bluetooth adapter");
                if (!adapter.isEnabled()) throw new Exception("Turn Bluetooth on first");
                String wanted = deviceName.getText().toString().trim();
                BluetoothDevice found = null;
                for (BluetoothDevice d : adapter.getBondedDevices()) {
                    String name = d.getName();
                    if (name != null && name.equalsIgnoreCase(wanted)) { found = d; break; }
                }
                if (found == null) {
                    StringBuilder names = new StringBuilder();
                    for (BluetoothDevice d : adapter.getBondedDevices()) {
                        if (names.length() > 0) names.append(", ");
                        names.append(d.getName() == null ? "(unnamed)" : d.getName());
                    }
                    throw new Exception("DeskBuddy not found. Paired devices: " + (names.length()==0 ? "none" : names));
                }
                adapter.cancelDiscovery();
                socket = found.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                output = socket.getOutputStream();
                connected = true;
                String connectedName = found.getName();
                runOnUiThread(() -> {
                    status.setText("  Connected to " + connectedName);
                    send("TIME " + (System.currentTimeMillis()/1000L));
                    send("SCREEN face");
                    fetchWeather();
                });
            } catch (Exception e) {
                connected = false;
                output = null;
                if (socket != null) try { socket.close(); } catch (Exception ignored) {}
                String detail = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
                runOnUiThread(() -> status.setText("  Connection failed · " + detail));
            }
        }).start();
    }

    public static synchronized boolean send(String s) {
        try {
            OutputStream o = output;
            if (o == null) return false;
            o.write((s + "\n").getBytes("UTF-8"));
            o.flush();
            return true;
        } catch (Exception e) {
            connected = false;
            output = null;
            return false;
        }
    }

    void fetchWeather() {
        String place = location == null ? prefs.getString("city", "Aligarh") : location.getText().toString().trim();
        if (place.isEmpty()) place = "Aligarh";
        final String requested = place;
        if (weatherStatus != null) weatherStatus.setText("Fetching weather for " + requested + "…");
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                String q = java.net.URLEncoder.encode(requested, "UTF-8");
                c = (HttpURLConnection)new URL("https://geocoding-api.open-meteo.com/v1/search?name=" + q + "&count=1&language=en&format=json").openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                JSONObject geo = new JSONObject(readAll(c)); c.disconnect(); c = null;
                if (!geo.has("results") || geo.getJSONArray("results").length() == 0) throw new Exception("City not found");
                JSONObject city = geo.getJSONArray("results").getJSONObject(0);
                double lat = city.getDouble("latitude"), lon = city.getDouble("longitude");
                String label = city.optString("name", requested);
                c = (HttpURLConnection)new URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,relative_humidity_2m,weather_code&timezone=auto").openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                JSONObject data = new JSONObject(readAll(c));
                JSONObject cur = data.getJSONObject("current");
                String temp = String.format(java.util.Locale.US, "%.1f", cur.getDouble("temperature_2m"));
                String hum = String.valueOf(cur.optInt("relative_humidity_2m", 0));
                String code = String.valueOf(cur.optInt("weather_code", -1));
                String cmd = "WEATHER " + temp + "|" + hum + "|" + code + "|" + label.replace("|", " ").replace("\n", " ");
                boolean sent = send(cmd);
                lastWeather = System.currentTimeMillis();
                String condition = weatherDescription(Integer.parseInt(code));
                String headline = temp + "°C  ·  " + condition;
                String msg = label + " · Humidity " + hum + "%" + (sent ? " · sent to Desk Buddy" : " · Bluetooth not connected");
                prefs.edit().putString("weather_title", headline).putString("weather_detail", msg).apply();
                runOnUiThread(() -> {
                    if (weatherStatus != null) weatherStatus.setText(msg);
                });
            } catch (Exception e) {
                String error = "Could not load weather · " + e.getMessage();
                runOnUiThread(() -> {
                    if (weatherStatus != null) weatherStatus.setText(error);
                });
            } finally { if (c != null) c.disconnect(); }
        }).start();
    }

    private String weatherDescription(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Foggy";
        if (code == 51 || code == 53 || code == 55) return "Drizzle";
        if (code == 56 || code == 57) return "Freezing drizzle";
        if (code == 61 || code == 63 || code == 65) return "Rain";
        if (code == 66 || code == 67) return "Freezing rain";
        if (code == 71 || code == 73 || code == 75 || code == 77) return "Snow";
        if (code == 80 || code == 81 || code == 82) return "Rain showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code == 95 || code == 96 || code == 99) return "Thunderstorm";
        return "Current conditions";
    }

    String readAll(HttpURLConnection c) throws Exception {
        java.io.InputStream in = c.getInputStream();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[2048]; int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    @Override protected void onDestroy() {
        running = false;
        handler.removeCallbacks(syncTask);
        super.onDestroy();
    }
}
