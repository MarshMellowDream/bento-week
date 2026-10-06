package com.bentoweek.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;

/** The full-screen page a ringing alarm opens. It keeps ringing until Dismiss is tapped. */
public class AlarmActivity extends Activity {
    private TextView timeView;
    private TextView titleView;
    private TextView textView;
    private int notificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int pad = dp(28);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(0x24, 0x40, 0x7A));

        timeView = label(18, 0xCCFFFFFF);
        titleView = label(34, Color.WHITE);
        textView = label(18, 0xE6FFFFFF);

        Button dismiss = new Button(this);
        dismiss.setText("Dismiss");
        dismiss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        dismiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(notificationId);
                finish();
            }
        });

        root.addView(timeView, params(0));
        root.addView(titleView, params(dp(8)));
        root.addView(textView, params(dp(16)));
        root.addView(dismiss, params(dp(40)));
        setContentView(root);
        fill(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        fill(intent);
    }

    private void fill(Intent intent) {
        if (intent == null) return;
        notificationId = intent.getIntExtra("id", 0);
        long t = intent.getLongExtra("time", System.currentTimeMillis());
        timeView.setText(DateFormat.getTimeFormat(this).format(new Date(t)));
        titleView.setText(intent.getStringExtra("title"));
        textView.setText(intent.getStringExtra("text"));
    }

    private TextView label(int sp, int color) {
        TextView v = new TextView(this);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private LinearLayout.LayoutParams params(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
