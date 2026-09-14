package ar.terracity.iurixmonitor;

import android.content.Context;
import android.content.SharedPreferences;

final class ReminderPrefs {
    private static final String NAME = "iurix_reminder";

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean("enabled", true);
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("enabled", enabled).apply();
    }

    static boolean wasInitialized(Context context) {
        return prefs(context).getBoolean("initialized", false);
    }

    static void setInitialized(Context context, boolean initialized) {
        prefs(context).edit().putBoolean("initialized", initialized).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private ReminderPrefs() {}
}
