package ar.terracity.iurixmonitor;

import android.content.Context;

import java.util.List;

final class MonitorEngine {
    private static final int MAX_ENDPOINTS_PER_RUN = 30;

    static void run(Context context, boolean notifyChanges) {
        if (!Prefs.monitorEnabled(context) && notifyChanges) return;

        List<String> endpoints = RequestLog.urls(context);
        int checked = 0;
        int changed = 0;
        int errors = 0;
        boolean loginRequired = false;
        int notificationId = 4100;

        for (String url : endpoints) {
            if (checked >= MAX_ENDPOINTS_PER_RUN) break;
            IurixMonitor.Result r = IurixMonitor.check(context, url);
            checked++;
            loginRequired |= r.loginRequired;
            if (!r.ok && !r.loginRequired) errors++;

            if (r.changed) {
                changed++;
                if (notifyChanges) {
                    String text = "Se detectó una modificación en " + r.category + ". Abrí IURIX para verificar la novedad. La aplicación no realiza cambios ni presentaciones.";
                    NotificationHelper.show(context, notificationId++, "IURIX Monitor SL", text, r.url);
                }
            }
        }

        if (notifyChanges && loginRequired) {
            long now = System.currentTimeMillis();
            long last = Prefs.getLong(context, "last_login_notice", 0L);
            if (now - last > 6L * 60L * 60L * 1000L) {
                NotificationHelper.show(context, 4199, "IURIX requiere ingreso",
                        "Una o más consultas dejaron de estar autorizadas. Abrí el visor e iniciá sesión nuevamente para continuar el monitoreo.",
                        IurixMonitor.BASE + "/iol-ui/");
                Prefs.setLong(context, "last_login_notice", now);
            }
        }

        Prefs.setLong(context, "last_check", System.currentTimeMillis());
        Prefs.setInt(context, "last_checked_count", checked);
        Prefs.setInt(context, "last_changed_count", changed);
        Prefs.setInt(context, "last_error_count", errors);
    }

    private MonitorEngine() {}
}
