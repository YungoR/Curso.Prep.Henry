package ar.terracity.iurixmonitor;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 9101;
    static final String IURIX_URL = "https://iolcn.justiciasanluis.gov.ar/iol-ui/novedades";

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ReminderNotification.ensureChannel(this);
        requestNotificationPermission();
        buildUi();

        if (!ReminderPrefs.wasInitialized(this)) {
            ReminderPrefs.setEnabled(this, true);
            ReminderPrefs.setInitialized(this, true);
            AlarmScheduler.scheduleAll(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (ReminderPrefs.isEnabled(this)) AlarmScheduler.scheduleAll(this);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(30));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("RECORDATORIO IURIX");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(31, 94, 150));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Avisos locales en tu teléfono para revisar IURIX.\nNo accede a expedientes ni guarda usuario o contraseña.");
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, 0, 0, dp(24));
        root.addView(subtitle);

        TextView schedule = new TextView(this);
        schedule.setText("TODOS LOS DÍAS\n\n11:00  ·  Primera revisión\n16:00  ·  Segunda revisión");
        schedule.setTextSize(19);
        schedule.setGravity(Gravity.CENTER_HORIZONTAL);
        schedule.setPadding(dp(12), dp(18), dp(12), dp(18));
        schedule.setBackgroundColor(Color.rgb(241, 247, 252));
        root.addView(schedule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, dp(18), 0, dp(12));
        root.addView(status);

        Button open = button("Abrir IURIX ahora");
        open.setOnClickListener(v -> openIurix());
        root.addView(open);

        Button enable = button("Activar recordatorios 11:00 y 16:00");
        enable.setOnClickListener(v -> {
            ReminderPrefs.setEnabled(this, true);
            AlarmScheduler.scheduleAll(this);
            maybeRequestExactAlarmPermission();
            refreshStatus();
            Toast.makeText(this, "Recordatorios activados", Toast.LENGTH_SHORT).show();
        });
        root.addView(enable);

        Button disable = button("Pausar recordatorios");
        disable.setOnClickListener(v -> {
            ReminderPrefs.setEnabled(this, false);
            AlarmScheduler.cancelAll(this);
            refreshStatus();
            Toast.makeText(this, "Recordatorios pausados", Toast.LENGTH_SHORT).show();
        });
        root.addView(disable);

        Button test = button("Probar notificación ahora");
        test.setOnClickListener(v -> ReminderNotification.showReminder(this, 9999, "Prueba de recordatorio IURIX"));
        root.addView(test);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Button exact = button("Permitir horarios exactos");
            exact.setOnClickListener(v -> requestExactAlarmPermission());
            root.addView(exact);
        }

        TextView note = new TextView(this);
        note.setText("Cuando aparezca el aviso, tocá “Abrir IURIX” para ir directamente a la página oficial de Novedades. También podés elegir “Recordar en 30 min”.");
        note.setTextSize(14);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        setContentView(scroll);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean enabled = ReminderPrefs.isEnabled(this);
        String exact = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = getSystemService(AlarmManager.class);
            exact = am != null && am.canScheduleExactAlarms()
                    ? "\nHorarios exactos: permitidos"
                    : "\nHorarios exactos: sin permiso; Android puede demorar el aviso algunos minutos";
        }
        status.setText((enabled ? "ESTADO: ACTIVO" : "ESTADO: PAUSADO") + exact);
        status.setTextColor(enabled ? Color.rgb(20, 120, 60) : Color.rgb(170, 50, 45));
    }

    private void openIurix() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(IURIX_URL));
        startActivity(intent);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void maybeRequestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        AlarmManager am = getSystemService(AlarmManager.class);
        if (am != null && !am.canScheduleExactAlarms()) {
            new AlertDialog.Builder(this)
                    .setTitle("Horarios exactos")
                    .setMessage("Para que los avisos aparezcan lo más cerca posible de las 11:00 y 16:00, Android puede pedir permiso para alarmas exactas.")
                    .setPositiveButton("Permitir", (d, w) -> requestExactAlarmPermission())
                    .setNegativeButton("Ahora no", null)
                    .show();
        }
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Buscá Recordatorio IURIX en Ajustes > Alarmas y recordatorios", Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
