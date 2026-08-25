package app.claucode.phone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuntimeService extends Service {
    static final String ACTION_START_GATEWAY = "start_gateway";
    static final String ACTION_START_CLAUDE = "start_claude";
    static final String ACTION_INPUT = "input";
    static final String EXTRA_INPUT = "input";
    private static final String CHANNEL = "runtime";
    private static final Pattern URL = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private Process gateway;
    private Process claude;
    private OutputStream claudeInput;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Local AI runtime", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setContentTitle("ClauCode Phone")
                .setContentText("OmniRoute y Claude Code pueden seguir activos")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
        startForeground(42, notification);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_START_GATEWAY.equals(intent.getAction())) startGateway();
        else if (ACTION_START_CLAUDE.equals(intent.getAction())) startClaude();
        else if (ACTION_INPUT.equals(intent.getAction())) writeInput(intent.getStringExtra(EXTRA_INPUT));
        return START_STICKY;
    }

    private synchronized void startGateway() {
        if (gateway != null && gateway.isAlive()) {
            status("OmniRoute ya está activo");
            return;
        }
        File executable = runtimeFile("omniroute");
        if (!executable.canExecute()) {
            status("Falta el runtime: " + executable.getAbsolutePath());
            return;
        }
        executor.execute(() -> {
            try {
                ProcessBuilder builder = process(executable.getAbsolutePath());
                gateway = builder.start();
                stream(gateway, "OmniRoute: ");
            } catch (Exception error) {
                status("No se pudo iniciar OmniRoute: " + error.getMessage());
            }
        });
    }

    private synchronized void startClaude() {
        if (claude != null && claude.isAlive()) return;
        executor.execute(() -> {
            try {
                String key = SecretStore.load(this);
                if (key == null || key.trim().isEmpty()) {
                    status("Pegá tu API key antes de iniciar Claude Code");
                    return;
                }
                File executable = runtimeFile("claude");
                if (!executable.canExecute()) {
                    status("Falta el runtime: " + executable.getAbsolutePath());
                    return;
                }
                ProcessBuilder builder = process(executable.getAbsolutePath(), "--model", RuntimeConfig.MODEL);
                Map<String, String> env = builder.environment();
                env.put("ANTHROPIC_BASE_URL", RuntimeConfig.DEFAULT_BASE_URL);
                env.put("ANTHROPIC_AUTH_TOKEN", key);
                env.put("ANTHROPIC_MODEL", RuntimeConfig.MODEL);
                claude = builder.start();
                claudeInput = claude.getOutputStream();
                stream(claude, "");
            } catch (Exception error) {
                status("No se pudo iniciar Claude Code: " + error.getMessage());
            }
        });
    }

    private ProcessBuilder process(String... command) {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        File home = new File(getFilesDir(), "home");
        File tmp = new File(getCacheDir(), "tmp");
        //noinspection ResultOfMethodCallIgnored
        home.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        tmp.mkdirs();
        builder.directory(home);
        builder.environment().put("HOME", home.getAbsolutePath());
        builder.environment().put("TMPDIR", tmp.getAbsolutePath());
        builder.environment().put("PATH", new File(getFilesDir(), "usr/bin").getAbsolutePath());
        return builder;
    }

    private File runtimeFile(String name) {
        return new File(new File(getFilesDir(), "usr/bin"), name);
    }

    private void stream(Process process, String prefix) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                status(prefix + line);
                Matcher matcher = URL.matcher(line);
                if (matcher.find()) status("CONFIG_URL=" + matcher.group());
            }
            status(prefix + "proceso finalizado (código " + process.waitFor() + ")");
        } catch (Exception error) {
            status(prefix + error.getMessage());
        }
    }

    private synchronized void writeInput(String value) {
        if (claudeInput == null || value == null) return;
        try {
            claudeInput.write((value + "\n").getBytes(StandardCharsets.UTF_8));
            claudeInput.flush();
        } catch (Exception error) {
            status("Error de entrada: " + error.getMessage());
        }
    }

    private void status(String message) {
        Intent intent = new Intent(RuntimeConfig.ACTION_STATUS)
                .setPackage(getPackageName())
                .putExtra(RuntimeConfig.EXTRA_MESSAGE, message);
        sendBroadcast(intent);
    }

    @Override public void onDestroy() {
        if (claude != null) claude.destroy();
        if (gateway != null) gateway.destroy();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
