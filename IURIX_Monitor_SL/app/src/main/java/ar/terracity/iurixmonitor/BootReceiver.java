package ar.terracity.iurixmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ReminderPrefs.isEnabled(context)) AlarmScheduler.scheduleAll(context);
    }
}
