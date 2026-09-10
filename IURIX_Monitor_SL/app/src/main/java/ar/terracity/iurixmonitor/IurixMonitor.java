package ar.terracity.iurixmonitor;

import android.content.Context;
import android.webkit.CookieManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

final class IurixMonitor {
    static final String BASE = "https://iolcn.justiciasanluis.gov.ar";

    static final class Result {
        final String url;
        final boolean ok;
        final boolean loginRequired;
        final boolean changed;
        final String category;
        final String detail;

        Result(String url, boolean ok, boolean loginRequired, boolean changed, String category, String detail) {
            this.url = url;
            this.ok = ok;
            this.loginRequired = loginRequired;
            this.changed = changed;
            this.category = category;
            this.detail = detail;
        }
    }

    static Result check(Context context, String endpointUrl) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(endpointUrl);
            conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json,text/plain,text/html;q=0.8,*/*;q=0.5");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36 IURIXMonitorSL/0.3");

            String cookies = CookieManager.getInstance().getCookie(endpointUrl);
            if (cookies != null && !cookies.isBlank()) conn.setRequestProperty("Cookie", cookies);

            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(stream);
            String finalUrl = conn.getURL().toString();

            boolean login = status == 401 || status == 403 || looksLikeLogin(finalUrl, body);
            if (login) {
                return new Result(endpointUrl, false, true, false, classify(endpointUrl), "La consulta requiere volver a iniciar sesión.");
            }
            if (status < 200 || status >= 400) {
                return new Result(endpointUrl, false, false, false, classify(endpointUrl), "HTTP " + status);
            }
            if (body == null || body.trim().length() < 2) {
                return new Result(endpointUrl, false, false, false, classify(endpointUrl), "Respuesta vacía.");
            }

            String normalized = normalize(body, conn.getContentType());
            String key = "endpoint_" + sha256(endpointUrl).substring(0, 20);
            String newHash = sha256(normalized);
            String oldHash = Prefs.getHash(context, key);
            Prefs.setHash(context, key, newHash);
            boolean changed = oldHash != null && !oldHash.equals(newHash);

            return new Result(endpointUrl, true, false, changed, classify(endpointUrl),
                    oldHash == null ? "Referencia inicial guardada." : (changed ? "Cambio detectado." : "Sin cambios."));
        } catch (Exception e) {
            return new Result(endpointUrl, false, false, false, classify(endpointUrl),
                    e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean looksLikeLogin(String finalUrl, String body) {
        String u = finalUrl == null ? "" : finalUrl.toLowerCase(Locale.ROOT);
        String b = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return u.contains("openid-connect/auth") ||
                u.contains("/auth/realms/") ||
                b.contains("openid-connect/auth") ||
                b.contains("inicie sesión mediante su cuenta organizativa") ||
                b.contains("client_id=iol-ui");
    }

    private static String classify(String url) {
        String s = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (s.contains("cedul")) return "Cédulas";
        if (s.contains("noved")) return "Novedades";
        if (s.contains("actuacion") || s.contains("expediente")) return "Expedientes";
        if (s.contains("favorit")) return "Favoritos";
        if (s.contains("despacho")) return "Despacho diario";
        return "IURIX";
    }

    private static String normalize(String body, String contentType) {
        if (body == null) return "";
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.contains("html") || body.trim().startsWith("<")) {
            String s = body;
            s = s.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
            s = s.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
            s = s.replaceAll("(?is)<!--.*?-->", " ");
            s = s.replaceAll("(?is)<[^>]+>", " ");
            return s.replaceAll("\\s+", " ").trim();
        }
        return body.trim();
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    private static String sha256(String value) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    private IurixMonitor() {}
}
