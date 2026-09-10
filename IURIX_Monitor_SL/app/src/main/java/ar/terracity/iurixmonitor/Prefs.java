package ar.terracity.iurixmonitor;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String NAME = "iurix_monitor";

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static boolean monitorEnabled(Context context) {
        return get(context).getBoolean("monitor_enabled", true);
    }

    static void setMonitorEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean("monitor_enabled", enabled).apply();
    }

    static String getHash(Context context, String key) {
        return get(context).getString("hash_" + key, null);
    }

    static void setHash(Context context, String key, String value) {
        get(context).edit().putString("hash_" + key, value).apply();
    }

    static long getLong(Context context, String key, long def) {
        return get(context).getLong(key, def);
    }

    static void setLong(Context context, String key, long value) {
        get(context).edit().putLong(key, value).apply();
    }

    static int getInt(Context context, String key, int def) {
        return get(context).getInt(key, def);
    }

    static void setInt(Context context, String key, int value) {
        get(context).edit().putInt(key, value).apply();
    }

    private Prefs() {}
}
