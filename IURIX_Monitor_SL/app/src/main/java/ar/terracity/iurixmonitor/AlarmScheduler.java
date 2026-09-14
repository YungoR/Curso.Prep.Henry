package ar.terracity.iurixmonitor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

final class AlarmScheduler {
    private static final int REQ_1100 = 1100;
    private static final int REQ_1600 = 1600;

    static void scheduleAll(Context context) {
        if (!ReminderPrefs.isEnabled(context)) return;
        scheduleDaily(context, REQ_1100, 11, 0);
        scheduleDaily(context, REQ_1600, 16, 0);
    }

    static void cancelAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pending(context, REQ_1100, 11, 0));
        am.cancel(pending(context, REQ_1600, 16, 0));
    }

    static void scheduleSnooze(Context context, int minutes) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long when = System.currentTimeMillis() + minutes * 60_000L;
        Intent i = new Intent(context, ReminderReceiver.class);
        i.setAction("IURIX_SNOOZE");
        i.putExtra("label", "Recordatorio pospuesto");
        PendingIntent pi = PendingIntent.getBroadcast(context, 3030, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
    }

    private static void scheduleDaily(Context context, int requestCode, int hour, int minute) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);

        PendingIntent pi = pending(context, requestCode, hour, minute);
        boolean exactAllowed = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) exactAllowed = am.canScheduleExactAlarms();

        if (exactAllowed) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
            else am.setExact(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        }
    }

    private static PendingIntent pending(Context context, int requestCode, int hour, int minute) {
        Intent i = new Intent(context, ReminderReceiver.class);
        i.setAction("IURIX_DAILY_" + requestCode);
        i.putExtra("label", hour == 11 ? "Primera revisión diaria" : "Segunda revisión diaria");
        i.putExtra("hour", hour);
        i.putExtra("minute", minute);
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void rescheduleTriggered(Context context, int hour, int minute) {
        if (!ReminderPrefs.isEnabled(context)) return;
        int code = hour == 11 ? REQ_1100 : REQ_1600;
        scheduleDaily(context, code, hour, minute);
    }

    private AlarmScheduler() {}
}
