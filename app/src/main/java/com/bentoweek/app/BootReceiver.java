package com.bentoweek.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Puts the next alarm back after a restart, an app update or a clock change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Scheduler.scheduleNext(context);
    }
}
