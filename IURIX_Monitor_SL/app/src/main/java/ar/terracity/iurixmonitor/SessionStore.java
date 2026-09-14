package ar.terracity.iurixmonitor;

import android.content.Context;
import android.content.SharedPreferences;

final class SessionStore {
    private static final String NAME = "iurix_session_private";

    static void saveCookie(Context context, String cookie) {
        if (cookie == null || cookie.trim().isEmpty()) return;
        prefs(context).edit().putString("cookie", cookie).apply();
    }

    static String cookie(Context context) {
        return prefs(context).getString("cookie", "");
    }

    static void saveAuthorization(Context context, String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) return;
        prefs(context).edit().putString("authorization", authorization).apply();
    }

    static String authorization(Context context) {
        return prefs(context).getString("authorization", "");
    }

    static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private SessionStore() {}
}
