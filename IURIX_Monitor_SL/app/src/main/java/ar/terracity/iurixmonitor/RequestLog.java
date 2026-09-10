package ar.terracity.iurixmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class RequestLog {
    private static final String KEY = "captured_get_urls_v3";
    private static final int MAX_URLS = 60;

    static void add(Context context, String method, String url) {
        if (method == null || url == null || !"GET".equalsIgnoreCase(method)) return;
        String cleaned = normalizeUrl(url);
        if (!isMonitorable(cleaned)) return;

        SharedPreferences p = Prefs.get(context);
        Set<String> stored = p.getStringSet(KEY, Collections.emptySet());
        LinkedHashSet<String> current = new LinkedHashSet<>();
        if (stored != null) current.addAll(stored);

        if (current.size() >= MAX_URLS && !current.contains(cleaned)) {
            String first = current.iterator().next();
            current.remove(first);
        }
        current.add(cleaned);
        p.edit().putStringSet(KEY, current).apply();
    }

    static List<String> urls(Context context) {
        Set<String> set = Prefs.get(context).getStringSet(KEY, Collections.emptySet());
        if (set == null || set.isEmpty()) return new ArrayList<>();
        List<String> out = new ArrayList<>(set);
        Collections.sort(out);
        return out;
    }

    static int count(Context context) {
        return urls(context).size();
    }

    static String asText(Context context) {
        List<String> set = urls(context);
        if (set.isEmpty()) {
            return "Todavía no se capturaron consultas de lectura. Iniciá sesión y abrí Novedades, Cédulas, Favoritos y los expedientes que quieras controlar.";
        }
        StringBuilder sb = new StringBuilder();
        for (String s : set) sb.append(s).append("\n\n");
        return sb.toString().trim();
    }

    private static boolean isMonitorable(String url) {
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost();
            if (host == null || !(host.equals("justiciasanluis.gov.ar") || host.endsWith(".justiciasanluis.gov.ar"))) return false;
            String lower = url.toLowerCase(Locale.ROOT);
            if (!(lower.contains("/iol-api/") || lower.contains("/api/"))) return false;
            if (lower.contains("openid-connect") || lower.contains("/auth/") || lower.contains("/logout") || lower.contains("/token")) return false;
            if (lower.matches(".*\\.(js|css|png|jpg|jpeg|gif|svg|woff|woff2|ttf|ico)(\\?.*)?$")) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalizeUrl(String url) {
        try {
            Uri u = Uri.parse(url);
            Uri.Builder b = u.buildUpon().clearQuery();
            for (String name : u.getQueryParameterNames()) {
                String n = name.toLowerCase(Locale.ROOT);
                if (n.equals("_") || n.equals("ts") || n.equals("timestamp") || n.equals("cache") || n.equals("cachebust")) continue;
                List<String> values = u.getQueryParameters(name);
                if (values.isEmpty()) b.appendQueryParameter(name, "");
                else for (String value : values) b.appendQueryParameter(name, value);
            }
            return b.build().toString();
        } catch (Exception e) {
            return url;
        }
    }

    private RequestLog() {}
}
