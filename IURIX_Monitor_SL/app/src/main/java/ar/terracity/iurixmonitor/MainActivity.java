package ar.terracity.iurixmonitor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 9001;
    private static final String NOVEDADES_URL = "https://iolcn.justiciasanluis.gov.ar/iol-ui/novedades";
    private static final long REFRESH_MS = 5L * 60L * 1000L;

    private WebView webView;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            if (webView != null && webView.getUrl() != null && webView.getUrl().contains("/iol-ui/novedades")) {
                status.setText("Actualizando Novedades…");
                webView.reload();
            }
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationHelper.ensureChannel(this);
        requestNotificationPermission();
        buildViewer();
        webView.loadUrl(NOVEDADES_URL);
        handler.postDelayed(refresher, REFRESH_MS);
    }

    private void buildViewer() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        status = new TextView(this);
        status.setText("IURIX Monitor · abriendo Novedades…");
        status.setTextSize(15);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(14), 0, dp(14), 0);
        status.setBackgroundColor(Color.rgb(31, 94, 150));
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new NewsBridge(this), "IURIXBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                if (u.contains("/iol-ui/") && !u.contains("/iol-ui/novedades")) {
                    Toast.makeText(MainActivity.this, "Este visor está limitado a la sección Novedades.", Toast.LENGTH_SHORT).show();
                    view.loadUrl(NOVEDADES_URL);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("/iol-ui/novedades")) {
                    status.setText("Novedades cargadas · comprobando fechas…");
                    handler.postDelayed(MainActivity.this::scanNews, 1200);
                    handler.postDelayed(MainActivity.this::scanNews, 3500);
                } else if (url != null && (url.contains("/auth/") || url.contains("openid-connect"))) {
                    status.setText("Iniciá sesión en IURIX. Al ingresar volverá a Novedades.");
                }
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void scanNews() {
        if (webView == null || webView.getUrl() == null || !webView.getUrl().contains("/iol-ui/novedades")) return;

        String js = "(function(){" +
                "var re=/\\b\\d{2}\\/\\d{2}\\/\\d{4}\\s+\\d{2}:\\d{2}\\b/;" +
                "var out=[],seen={};" +
                "var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);" +
                "var n;" +
                "while((n=w.nextNode())){" +
                " var raw=(n.nodeValue||'').trim(); if(!re.test(raw)) continue;" +
                " var dm=raw.match(re); if(!dm) continue; var date=dm[0];" +
                " var p=n.parentElement,block=null,txt='',i=0;" +
                " while(p&&i<9){txt=(p.innerText||'').trim(); if(/\\b(?:EXP|INR|ERE)\\s+\\d+\\/\\d+\\b/i.test(txt)&&txt.length<1800){block=p;break;} p=p.parentElement;i++;}" +
                " if(!block) continue;" +
                " var lines=(block.innerText||'').split(/\\n+/).map(function(x){return x.trim();}).filter(Boolean);" +
                " var all=lines.join(' | '); var em=all.match(/\\b(?:EXP|INR|ERE)\\s+\\d+\\/\\d+\\b/i); if(!em) continue;" +
                " var id=em[0].toUpperCase().replace(/\\s+/g,' '); if(seen[id]) continue;" +
                " var title='';" +
                " for(var j=0;j<lines.length;j++){if(lines[j].toUpperCase().indexOf(id)>=0){title=lines[j].replace(new RegExp(id.replace('/','\\/'),'i'),'').replace(/^\\s*[|\\-–—:]\\s*/,'').trim();break;}}" +
                " if(!title&&lines.length>1) title=lines[1];" +
                " title=title.replace(re,'').trim();" +
                " seen[id]=true; out.push({id:id,title:title,date:date});" +
                "}" +
                "if(window.IURIXBridge){window.IURIXBridge.report(JSON.stringify(out));}" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private class NewsBridge {
        private final Context context;
        NewsBridge(Context context) { this.context = context.getApplicationContext(); }

        @JavascriptInterface
        public void report(String json) {
            try {
                JSONArray arr = new JSONArray(json);
                if (arr.length() == 0) {
                    runOnUiThread(() -> status.setText("Novedades abiertas · todavía no pude leer los expedientes."));
                    return;
                }

                SharedPreferences sp = context.getSharedPreferences("iurix_news", MODE_PRIVATE);
                JSONObject previous = new JSONObject(sp.getString("snapshot", "{}"));
                JSONObject current = new JSONObject();
                boolean hadBaseline = previous.length() > 0;
                int changed = 0;

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    String id = item.optString("id", "").trim();
                    String title = item.optString("title", "").trim();
                    String date = item.optString("date", "").trim();
                    if (id.isEmpty() || date.isEmpty()) continue;

                    JSONObject now = new JSONObject();
                    now.put("date", date);
                    now.put("title", title);
                    current.put(id, now);

                    if (hadBaseline) {
                        JSONObject old = previous.optJSONObject(id);
                        String oldDate = old == null ? "" : old.optString("date", "");
                        if (old == null || !date.equals(oldDate)) {
                            changed++;
                            String text = title.isEmpty()
                                    ? id + " cambió a " + date
                                    : id + " · " + title + "\nNueva fecha: " + date;
                            NotificationHelper.show(context, Math.abs((id + date).hashCode()), "Cambio en IURIX", text, NOVEDADES_URL);
                        }
                    }
                }

                sp.edit()
                        .putString("snapshot", current.toString())
                        .putLong("last_check", System.currentTimeMillis())
                        .apply();

                final int count = current.length();
                final int changes = changed;
                runOnUiThread(() -> {
                    String time = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(new Date());
                    if (!hadBaseline) {
                        status.setText("Referencia inicial guardada · " + count + " expedientes · " + time);
                        Toast.makeText(MainActivity.this, "Referencia inicial guardada. Desde ahora se compararán las fechas.", Toast.LENGTH_LONG).show();
                    } else if (changes == 0) {
                        status.setText("Sin cambios · " + count + " expedientes · " + time);
                    } else {
                        status.setText("Cambios detectados: " + changes + " · " + time);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("No pude comparar las novedades. Volvé a cargar la página."));
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
