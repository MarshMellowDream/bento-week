package com.bentoweek.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Holds the list of upcoming steps (handed over by the page) and keeps exactly one system alarm
 * set for the next step. When it fires, the step is shown and the following one is set.
 */
final class Scheduler {
    static final String PREFS = "bento";
    static final String KEY_SCHEDULE = "schedule";
    static final String KEY_LAST = "lastFired";
    static final String CH_STEP = "steps";
    static final String CH_ALARM = "alarms";
    static final String ACTION_FIRE = "com.bentoweek.app.FIRE";
    static final String ACTION_DISMISS = "com.bentoweek.app.DISMISS";
    static final int PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    /** A step that is this late (after a reboot, say) is skipped instead of shown. */
    static final long TOO_LATE_MS = 20L * 60L * 1000L;
    /** A ringing alarm stops by itself after this long. */
    static final long ALARM_TIMEOUT_MS = 10L * 60L * 1000L;

    private Scheduler() {}

    static JSONArray load(Context c) {
        try {
            return new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SCHEDULE, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void save(Context c, String json) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit().putString(KEY_SCHEDULE, json);
        if (!p.contains(KEY_LAST)) {
            e.putLong(KEY_LAST, System.currentTimeMillis());
        }
        e.commit();
        scheduleNext(c);
    }

    /** Sets the one system alarm for the next step that is still in the future. */
    static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent fire = new Intent(c, AlarmReceiver.class).setAction(ACTION_FIRE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, fire, PI_FLAGS);
        am.cancel(pi);

        JSONArray a = load(c);
        long now = System.currentTimeMillis();
        long next = -1;
        boolean nextIsAlarm = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            long t = o.optLong("t", 0);
            if (t > now) {
                next = t;
                nextIsAlarm = o.optInt("alarm", 0) == 1;
                break;
            }
        }
        if (next < 0) return;

        try {
            boolean exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (!exactAllowed) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else if (nextIsAlarm) {
                PendingIntent show = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class), PI_FLAGS);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(next, show), pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    /** Shows every step that has come due since the last time, then sets the next alarm. */
    static void fireDue(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray a = load(c);
        long now = System.currentTimeMillis();
        long last = p.getLong(KEY_LAST, now - 60000L);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            long t = o.optLong("t", 0);
            if (t > now + 1500L) break;
            if (t <= last) continue;
            if (now - t > TOO_LATE_MS) continue;
            show(c, o, t, i);
        }
        p.edit().putLong(KEY_LAST, now).commit();
        scheduleNext(c);
    }

    static void ensureChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel steps = new NotificationChannel(CH_STEP, "Day plan steps", NotificationManager.IMPORTANCE_HIGH);
        steps.setDescription("A sound and a banner when each step of the day starts.");
        nm.createNotificationChannel(steps);

        NotificationChannel alarms = new NotificationChannel(CH_ALARM, "Alarms", NotificationManager.IMPORTANCE_HIGH);
        alarms.setDescription("Wake up, leave for work and wind down. These ring until you dismiss them.");
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        alarms.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attrs);
        alarms.enableVibration(true);
        alarms.setVibrationPattern(new long[] {0L, 600L, 400L, 600L, 400L, 600L});
        alarms.setBypassDnd(true);
        alarms.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(alarms);
    }

    static void show(Context c, JSONObject o, long t, int index) {
        ensureChannels(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        boolean alarm = o.optInt("alarm", 0) == 1;
        String title = o.optString("title", "Bento Week");
        String text = o.optString("text", "");
        int id = (int) ((t / 60000L) % 1000000L) * 10 + (index % 10);

        Notification.Builder b = new Notification.Builder(c, alarm ? CH_ALARM : CH_STEP)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setWhen(t)
                .setShowWhen(true);

        if (alarm) {
            Intent ai = new Intent(c, AlarmActivity.class)
                    .putExtra("id", id)
                    .putExtra("title", title)
                    .putExtra("text", text)
                    .putExtra("time", t)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            PendingIntent full = PendingIntent.getActivity(c, id, ai, PI_FLAGS);
            Intent di = new Intent(c, AlarmReceiver.class).setAction(ACTION_DISMISS).putExtra("id", id);
            PendingIntent dismiss = PendingIntent.getBroadcast(c, id, di, PI_FLAGS);
            b.setCategory(Notification.CATEGORY_ALARM)
                    .setOngoing(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(full, true)
                    .setContentIntent(full)
                    .setTimeoutAfter(ALARM_TIMEOUT_MS)
                    .addAction(new Notification.Action.Builder((Icon) null, "Dismiss", dismiss).build());
            Notification n = b.build();
            n.flags |= Notification.FLAG_INSISTENT;
            nm.notify(id, n);
        } else {
            PendingIntent open = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class), PI_FLAGS);
            b.setCategory(Notification.CATEGORY_REMINDER)
                    .setContentIntent(open)
                    .setAutoCancel(true);
            nm.notify(id, b.build());
        }
    }

    /** Text for the page: what alerts next and when, or an empty string. */
    static String describeNext(Context c) {
        JSONArray a = load(c);
        long now = System.currentTimeMillis();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optLong("t", 0) > now) {
                return o.optString("title", "") + "|" + o.optLong("t", 0);
            }
        }
        return "";
    }
}
