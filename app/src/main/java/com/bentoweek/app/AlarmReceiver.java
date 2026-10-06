package com.bentoweek.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Runs when the next step comes due, or when Dismiss is tapped on a ringing alarm. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Scheduler.ACTION_DISMISS.equals(intent.getAction())) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(intent.getIntExtra("id", 0));
            return;
        }
        Scheduler.fireDue(context);
    }
}
