package ar.terracity.iurixmonitor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 9001;
    private static final int MONITOR_JOB_ID = 381027;
    private LinearLayout root;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationHelper.ensureChannel(this);
        requestNotificationPermission();
        if (Prefs.monitorEnabled(this)) scheduleMonitoring();

        String open = getIntent() == null ? null : getIntent().getStringExtra("open_url");
        if (open != null && !open.isBlank()) openUrl(open); else showHome();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String open = intent == null ? null : intent.getStringExtra("open_url");
        if (open != null && !open.isBlank()) openUrl(open);
    }

    private void showHome() {
        destroyWebView();

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("IURIX MONITOR SAN LUIS");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(31, 94, 150));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(7));
        root.addView(title);

        TextView readOnly = new TextView(this);
        readOnly.setText("VISOR PRIVADO · MODO SOLO LECTURA");
        readOnly.setTextSize(16);
        readOnly.setTextColor(Color.rgb(20, 110, 55));
        readOnly.setGravity(Gravity.CENTER_HORIZONTAL);
        readOnly.setPadding(0, 0, 0, dp(8));
        root.addView(readOnly);

        TextView subtitle = new TextView(this);
        subtitle.setText("Consulta IURIX y genera alertas locales. No presenta escritos, no firma, no contesta cédulas y no modifica expedientes. No es una aplicación oficial del Poder Judicial.");
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, 0, 0, dp(16));
        root.addView(subtitle);

        addButton("Abrir IURIX", () -> openUrl(IurixMonitor.BASE + "/iol-ui/"));
        addButton("Novedades", () -> openUrl(IurixMonitor.BASE + "/iol-ui/novedades"));
        addButton("Favoritos", () -> openUrl(IurixMonitor.BASE + "/iol-ui/favoritos"));
        addButton("Cédulas", () -> openUrl(IurixMonitor.BASE + "/iol-ui/cedula"));
        addButton("Despacho diario", () -> openUrl(IurixMonitor.BASE + "/iol-ui/despacho"));

        boolean enabled = Prefs.monitorEnabled(this);
        Button monitor = addButton(enabled ? "Monitor automático: ACTIVO" : "Monitor automático: PAUSADO", null);
        monitor.setOnClickListener(v -> {
            boolean next = !Prefs.monitorEnabled(this);
            Prefs.setMonitorEnabled(this, next);
            if (next) scheduleMonitoring(); else cancelMonitoring();
            monitor.setText(next ? "Monitor automático: ACTIVO" : "Monitor automático: PAUSADO");
            Toast.makeText(this, next ? "Monitoreo activado" : "Monitoreo pausado", Toast.LENGTH_SHORT).show();
        });

        addButton("Sincronizar ahora", this::runManualSync);
        addButton("Ver conexiones de lectura detectadas", this::showDiagnostics);
        addButton("Cómo preparar las alertas", this::showHelp);

        long last = Prefs.getLong(this, "last_check", 0L);
        int endpoints = RequestLog.count(this);
        int checked = Prefs.getInt(this, "last_checked_count", 0);
        int changed = Prefs.getInt(this, "last_changed_count", 0);
        int errors = Prefs.getInt(this, "last_error_count", 0);
        String lastText = last == 0 ? "Todavía no se realizó una sincronización." :
                "Último control: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(new Date(last));

        TextView status = new TextView(this);
        status.setText("\n" + lastText +
                "\nConsultas de lectura detectadas: " + endpoints +
                "\nConsultas revisadas en el último control: " + checked +
                "\nCambios detectados: " + changed +
                "\nErrores de consulta: " + errors +
                "\n\nAndroid programa controles periódicos aproximadamente cada 15 minutos, aunque el sistema puede demorarlos para ahorrar batería. La primera sincronización de cada consulta guarda una referencia y no genera una alerta.");
        status.setTextSize(14);
        root.addView(status);

        setContentView(scroll);
    }

    private Button addButton(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54));
        lp.setMargins(0, dp(5), 0, dp(5));
        root.addView(b, lp);
        if (action != null) b.setOnClickListener(v -> action.run());
        return b;
    }

    private void openUrl(String url) {
        if (isBlockedRoute(url)) {
            showReadOnlyBlocked();
            return;
        }

        destroyWebView();
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(s.getUserAgentString() + " IURIXMonitorSL/0.3");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String target = u.toString();
                if (isBlockedRoute(target)) {
                    showReadOnlyBlocked();
                    return true;
                }
                String host = u.getHost();
                if (isJusticeHost(host)) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, u));
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                RequestLog.add(MainActivity.this, request.getMethod(), request.getUrl().toString());

                String method = request.getMethod();
                String target = request.getUrl().toString();
                if (isJusticeHost(request.getUrl().getHost()) && isDangerousNonReadRequest(method, target)) {
                    byte[] data = "Modo solo lectura".getBytes(StandardCharsets.UTF_8);
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "Solo lectura", null, new ByteArrayInputStream(data));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("/iol-ui/")) injectReadOnlyGuard(view);
            }
        });

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        Button home = new Button(this);
        home.setText("Inicio del visor");
        home.setAllCaps(false);
        home.setOnClickListener(v -> showHome());
        shell.addView(home, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        shell.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(shell);
        webView.loadUrl(url);
    }

    private void injectReadOnlyGuard(WebView view) {
        String js = "(function(){" +
                "if(window.__IURIX_MONITOR_RO)return;window.__IURIX_MONITOR_RO=true;" +
                "var bad=['presentar','presentación','presentacion','firmar','enviar','guardar','eliminar','borrar','modificar','editar','contestar','responder','nueva causa','ingresar causa','ingreso masivo','pagar','confirmar','aceptar cédula','aceptar cedula'];" +
                "function txt(el){return ((el.innerText||el.value||el.title||el.getAttribute('aria-label')||'')+'').toLowerCase();}" +
                "document.addEventListener('submit',function(e){e.preventDefault();e.stopImmediatePropagation();alert('IURIX Monitor está en modo solo lectura. Esta acción fue bloqueada.');},true);" +
                "document.addEventListener('click',function(e){var el=e.target&&e.target.closest?e.target.closest('button,input[type=submit],input[type=button],[role=button],a'):null;if(!el)return;var t=txt(el);for(var i=0;i<bad.length;i++){if(t.indexOf(bad[i])>=0){e.preventDefault();e.stopImmediatePropagation();alert('IURIX Monitor está en modo solo lectura. Esta acción fue bloqueada.');return;}}},true);" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    private boolean isDangerousNonReadRequest(String method, String url) {
        if (method == null || url == null) return false;
        String m = method.toUpperCase(Locale.ROOT);
        if (m.equals("GET") || m.equals("HEAD") || m.equals("OPTIONS")) return false;
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains("openid-connect") || u.contains("/auth/") || u.contains("/login")) return false;
        if (m.equals("DELETE") || m.equals("PUT") || m.equals("PATCH")) return true;
        if (!m.equals("POST")) return false;
        String[] risky = {"present", "firma", "firmar", "guardar", "delete", "eliminar", "borrar", "modificar", "editar", "contestar", "responder", "nuevacausa", "ingresomasivo", "pago", "pagar", "aceptar"};
        for (String word : risky) if (u.contains(word)) return true;
        return false;
    }

    private boolean isBlockedRoute(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("/iol-ui/nuevacausa") ||
                u.contains("/iol-ui/ingresomasivo") ||
                u.contains("/iol-ui/tasasnoexp") ||
                u.contains("/iol-ui/present") ||
                u.contains("/iol-ui/firma");
    }

    private boolean isJusticeHost(String host) {
        return host != null && (host.equals("justiciasanluis.gov.ar") || host.endsWith(".justiciasanluis.gov.ar"));
    }

    private void showReadOnlyBlocked() {
        Toast.makeText(this, "Acción bloqueada: este programa funciona únicamente como visor.", Toast.LENGTH_LONG).show();
    }

    private void showDiagnostics() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(box);

        Button back = new Button(this);
        back.setText("Volver");
        back.setAllCaps(false);
        back.setOnClickListener(v -> showHome());
        box.addView(back);

        TextView t = new TextView(this);
        t.setText("CONSULTAS GET DETECTADAS PARA MONITOREO\n\n" + RequestLog.asText(this));
        t.setTextSize(13);
        t.setTextIsSelectable(true);
        t.setPadding(0, dp(14), 0, 0);
        box.addView(t);
        setContentView(scroll);
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Preparar alertas")
                .setMessage("Abrí IURIX desde esta aplicación e iniciá sesión normalmente. Después entrá a Novedades, Cédulas, Favoritos y a los expedientes que quieras controlar. El visor registra solamente las direcciones de consultas GET del sistema, nunca tu contraseña. Volvé al inicio y tocá Sincronizar ahora. La primera sincronización crea la referencia inicial; los controles posteriores pueden avisar si una respuesta cambia. Algunas secciones privadas de IURIX pueden exigir que vuelvas a iniciar sesión cuando venza la sesión oficial.")
                .setPositiveButton("Entendido", null)
                .show();
    }

    private void runManualSync() {
        if (RequestLog.count(this) == 0) {
            Toast.makeText(this, "Primero abrí IURIX y recorré las secciones que querés controlar.", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Control iniciado. Puede tardar unos segundos.", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            MonitorEngine.run(getApplicationContext(), true);
            runOnUiThread(() -> {
                Toast.makeText(this, "Control finalizado", Toast.LENGTH_SHORT).show();
                showHome();
            });
        }, "IURIX-Manual").start();
    }

    private void scheduleMonitoring() {
        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        ComponentName service = new ComponentName(this, IurixJobService.class);
        JobInfo job = new JobInfo.Builder(MONITOR_JOB_ID, service)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(15L * 60L * 1000L)
                .build();
        scheduler.schedule(job);
    }

    private void cancelMonitoring() {
        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        scheduler.cancel(MONITOR_JOB_ID);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void destroyWebView() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else if (webView != null) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }
}
