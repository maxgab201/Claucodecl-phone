package app.claucode.phone;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private LinearLayout root;
    private TextView log;
    private String configUrl;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(RuntimeConfig.EXTRA_MESSAGE);
            if (message == null) return;
            if (message.startsWith("CONFIG_URL=")) {
                configUrl = message.substring("CONFIG_URL=".length());
                showSetup();
            } else append(message);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        showLoading();
        startRuntime(RuntimeService.ACTION_START_GATEWAY);
        try {
            if (SecretStore.load(this) != null) showTerminal();
        } catch (Exception ignored) {
            showSetup();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(RuntimeConfig.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(statusReceiver, filter);
    }

    @Override protected void onStop() {
        unregisterReceiver(statusReceiver);
        super.onStop();
    }

    private void showLoading() {
        baseLayout();
        title("Iniciando OmniRoute…");
        paragraph("El gateway local seguirá activo mediante un servicio visible. Cuando publique la URL de configuración, podrás abrirla desde acá.");
        log = paragraph("Esperando al runtime…");
    }

    private void showSetup() {
        baseLayout();
        title("Configurá tu ruta");
        paragraph("1. Abrí OmniRoute.\n2. Creá el combo " + RuntimeConfig.MODEL + ".\n3. Generá y copiá una API key.");
        Button open = button(configUrl == null ? "Esperando URL de OmniRoute" : "Abrir OmniRoute");
        open.setEnabled(configUrl != null);
        open.setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(configUrl))));
        Button ready = button("Ya está todo configurado");
        ready.setOnClickListener(view -> showKeyEntry());
    }

    private void showKeyEntry() {
        baseLayout();
        title("Conectá Claude Code");
        paragraph("La clave se cifra con Android Keystore y nunca se muestra de nuevo.");
        EditText key = new EditText(this);
        key.setHint("API key de OmniRoute");
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(key, matchWrap());
        Button save = button("Guardar e iniciar");
        save.setOnClickListener(view -> {
            String value = key.getText().toString().trim();
            if (value.isEmpty()) return;
            try {
                SecretStore.save(this, value);
                showTerminal();
            } catch (Exception error) {
                Toast.makeText(this, "No se pudo proteger la clave", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showTerminal() {
        baseLayout();
        title("Claude Code");
        paragraph(RuntimeConfig.MODEL + " · OmniRoute local");
        ScrollView scroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextColor(Color.rgb(218, 216, 229));
        log.setTextSize(14);
        log.setTypeface(android.graphics.Typeface.MONOSPACE);
        log.setText("Iniciando sesión…\n");
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        EditText input = new EditText(this);
        input.setHint("Escribí un mensaje y presioná enviar");
        input.setSingleLine(false);
        root.addView(input, matchWrap());
        Button send = button("Enviar");
        send.setOnClickListener(view -> {
            String value = input.getText().toString();
            if (value.trim().isEmpty()) return;
            Intent intent = new Intent(this, RuntimeService.class)
                    .setAction(RuntimeService.ACTION_INPUT)
                    .putExtra(RuntimeService.EXTRA_INPUT, value);
            startService(intent);
            append("> " + value);
            input.setText("");
        });
        startRuntime(RuntimeService.ACTION_START_GATEWAY);
        startRuntime(RuntimeService.ACTION_START_CLAUDE);
    }

    private void startRuntime(String action) {
        Intent intent = new Intent(this, RuntimeService.class).setAction(action);
        startForegroundService(intent);
    }

    private void baseLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(42, 64, 42, 36);
        root.setBackgroundColor(Color.rgb(17, 16, 25));
        setContentView(root);
    }

    private void title(String value) {
        TextView view = paragraph(value);
        view.setTextSize(28);
        view.setTextColor(Color.WHITE);
        view.setPadding(0, 0, 0, 24);
    }

    private TextView paragraph(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(16);
        view.setTextColor(Color.rgb(190, 187, 204));
        view.setPadding(0, 0, 0, 20);
        root.addView(view, matchWrap());
        return view;
    }

    private Button button(String value) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        root.addView(view, matchWrap());
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void append(String message) {
        if (log != null) log.append(message + "\n");
    }
}
