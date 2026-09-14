package ar.terracity.iurixmonitor;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

final class ReminderNotification {
    private static final String CHANNEL = "iurix_recordatorio";

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL,
                    "Recordatorios IURIX",
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Avisos para revisar IURIX a las 11:00 y 16:00");
            nm.createNotificationChannel(ch);
        }
    }

    static void showReminder(Context context, int id, String label) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        ensureChannel(context);

        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.IURIX_URL));
        PendingIntent openPi = PendingIntent.getActivity(
                context,
                id + 10,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent snooze = new Intent(context, SnoozeReceiver.class);
        PendingIntent snoozePi = PendingIntent.getBroadcast(
                context,
                id + 20,
                snooze,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);

        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("REVISAR IURIX")
                .setContentText(label)
                .setStyle(new Notification.BigTextStyle().bigText(label + "\nIngresá a IURIX y revisá Novedades y Cédulas."))
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "Abrir IURIX", openPi).build())
                .addAction(new Notification.Action.Builder(null, "Recordar en 30 min", snoozePi).build());

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(id, b.build());
    }

    private ReminderNotification() {}
}
