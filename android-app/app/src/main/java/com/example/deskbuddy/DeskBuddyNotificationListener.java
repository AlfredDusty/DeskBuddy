package com.example.deskbuddy;

import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DeskBuddyNotificationListener extends NotificationListenerService {
    // Avoid repeatedly showing progress updates for the same ongoing notification.
    private final Set<String> shownOngoingKeys = ConcurrentHashMap.newKeySet();

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) shownOngoingKeys.remove(sbn.getKey());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName() == null) return;

        String packageName = sbn.getPackageName();
        if (packageName.equals(getPackageName()) ||
                packageName.equals("com.android.systemui")) return;

        Notification notification = sbn.getNotification();
        if (notification == null ||
                (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        boolean ongoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        boolean allowOngoing = getSharedPreferences(
                "deskbuddy_notifications", MODE_PRIVATE)
                .getBoolean("include_ongoing", false);

        if (ongoing) {
            // Off by default. If enabled, show only once while this notification is active.
            if (!allowOngoing || !shownOngoingKeys.add(sbn.getKey())) return;
        }

        Bundle extras = notification.extras;
        if (extras == null) return;

        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (TextUtils.isEmpty(text)) {
            text = extras.getCharSequence(Notification.EXTRA_TEXT);
        }

        // Read MessagingStyle message bundles directly to avoid SDK-incompatible APIs
        // extractMessagingStyleFromNotification() and Message.getPerson().
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Object rawMessages = extras.get(Notification.EXTRA_MESSAGES);
                if (rawMessages instanceof android.os.Parcelable[]) {
                    android.os.Parcelable[] messages = (android.os.Parcelable[]) rawMessages;
                    if (messages.length > 0 &&
                            messages[messages.length - 1] instanceof Bundle) {
                        Bundle latest = (Bundle) messages[messages.length - 1];

                        CharSequence latestText = latest.getCharSequence("text");
                        if (!TextUtils.isEmpty(latestText)) text = latestText;

                        CharSequence senderName = latest.getCharSequence("sender");
                        if (!TextUtils.isEmpty(senderName)) title = senderName;
                    }
                }
            } catch (Exception ignored) {
                // Fall back to standard notification title and text.
            }
        }

        String app = clean(appLabel(packageName), 22);
        String sender = clean(title == null ? app : title.toString(), 30);
        String message = clean(text == null ? "" : text.toString(), 175);
        if (sender.isEmpty()) sender = app;

        // Encode the app launcher icon as a 24x24 monochrome bitmap (144 hex chars).
        String iconHex = appIconHex(packageName);

        if (message.isEmpty()) {
            boolean ok = MainActivity.send("NOTIFYAPP|" + app + "|" + iconHex);
            Log.d("DeskBuddyBridge",
                    ok ? "Forwarded app icon" : "Not connected; skipped app icon");
            return;
        }

        // One line per notification: app + icon + sender + message.
        boolean ok = MainActivity.send(
                "NOTIFYSEQ|" + app + "|" + iconHex + "|" + sender + "|" + message);
        Log.d("DeskBuddyBridge",
                ok ? "Forwarded staged notification" : "Not connected; skipped notification");
    }

    private String appIconHex(String packageName) {
        try {
            Drawable drawable = getPackageManager().getApplicationIcon(packageName);
            Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.BLACK);
            drawable.setBounds(0, 0, 24, 24);
            drawable.draw(canvas);

            StringBuilder bits = new StringBuilder(576);
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < 24; x++) {
                    int pixel = bitmap.getPixel(x, y);
                    int gray = (Color.red(pixel) * 30
                            + Color.green(pixel) * 59
                            + Color.blue(pixel) * 11) / 100;
                    bits.append(gray >= 115 ? '1' : '0');
                }
            }

            // Pack 8 pixels per byte, most-significant bit first.
            StringBuilder packed = new StringBuilder(144);
            for (int i = 0; i < bits.length(); i += 8) {
                int value = 0;
                for (int j = 0; j < 8; j++) {
                    value = (value << 1) | (bits.charAt(i + j) == '1' ? 1 : 0);
                }
                packed.append("0123456789ABCDEF".charAt((value >> 4) & 15));
                packed.append("0123456789ABCDEF".charAt(value & 15));
            }
            bitmap.recycle();
            return packed.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String appLabel(String packageName) {
        try {
            CharSequence label = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0));
            if (label != null) return label.toString();
        } catch (Exception ignored) {
        }
        return packageName;
    }

    private String clean(String input, int maxLength) {
        if (input == null) return "";
        String value = input.replace('|', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');
        value = value.replaceAll("\\s+", " ").trim();
        if (value.length() > maxLength) value = value.substring(0, maxLength);
        return value;
    }
}
