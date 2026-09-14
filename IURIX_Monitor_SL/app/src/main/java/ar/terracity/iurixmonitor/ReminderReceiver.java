package ar.terracity.iurixmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if ("IURIX_SNOOZE".equals(action)) {
            ReminderNotification.showReminder(context, 3030, "Recordatorio pospuesto");
            return;
        }

        if (!ReminderPrefs.isEnabled(context)) return;

        String label = intent == null ? "Revisar IURIX" : intent.getStringExtra("label");
        int hour = intent == null ? 0 : intent.getIntExtra("hour", 0);
        int minute = intent == null ? 0 : intent.getIntExtra("minute", 0);

        ReminderNotification.showReminder(context,
                hour == 11 ? 1100 : 1600,
                label == null ? "Revisar IURIX" : label);

        if (hour > 0) AlarmScheduler.rescheduleTriggered(context, hour, minute);
    }
}
