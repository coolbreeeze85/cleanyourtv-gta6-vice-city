package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live weather backdrop & HUD updater.
 * Reads {@code weather_city}, {@code latitude}, and {@code longitude} from {@code config.json}
 * (falling back to {@code res/values/config.xml}), polls Open-Meteo every 15 minutes on a
 * background thread, and draws subtle atmospheric weather (rain streaks, mist/fog, or cloud tint)
 * over the time-of-day wallpaper.
 */
final class LondonWeatherView extends View {

    interface Listener {
        void onWeatherUpdated(String summary);
    }

    private static final long POLL_MS = 15 * 60_000L;

    static final int MODE_CLEAR = 0;
    static final int MODE_CLOUDY = 1;
    static final int MODE_FOG = 2;
    static final int MODE_RAIN = 3;
    static final int MODE_STORM = 4;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Listener listener;
    private final float density;
    private final String cityName;
    private final String apiUrl;

    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fogPaint = new Paint();
    private final float[] dropX = new float[70];
    private final float[] dropY = new float[70];
    private final float[] dropLen = new float[70];
    private final float[] dropSpeed = new float[70];
    private final Random random = new Random(42);

    private int mode = MODE_CLEAR;
    private String summary;
    private boolean running;
    private boolean dropsInitialised;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            fetchWeather();
            mainHandler.postDelayed(this, POLL_MS);
        }
    };

    LondonWeatherView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        this.density = context.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        String[] cfg = resolveWeatherConfig(context);
        this.cityName = cfg[0];
        this.apiUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + cfg[1]
                + "&longitude=" + cfg[2] + "&current=temperature_2m,weather_code";
        this.summary = this.cityName + "  \u2022  LIVE WEATHER";

        rainPaint.setColor(0x58B4F0FF);
        rainPaint.setStrokeWidth(1.4f * density);
        rainPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    static String[] resolveWeatherConfig(Context context) {
        String city = "VICE CITY";
        String lat = context.getString(R.string.latitude).trim();
        String lon = context.getString(R.string.longitude).trim();
        if (lat.isEmpty()) lat = "25.7617";
        if (lon.isEmpty()) lon = "-80.1918";

        JSONObject overlay = readOverlayJson(context);
        if (overlay != null) {
            String c = overlay.optString("weather_city", "").trim();
            if (!c.isEmpty()) city = c.toUpperCase(Locale.ROOT);
            String la = overlay.optString("latitude", "").trim();
            String lo = overlay.optString("longitude", "").trim();
            if (!la.isEmpty() && !lo.isEmpty()) {
                lat = la;
                lon = lo;
            }
        }
        return new String[]{city, lat, lon};
    }

    static JSONObject readOverlayJson(Context context) {
        File extDir = Config.dir(context);
        File extFile = extDir != null ? new File(extDir, Config.FILE_NAME) : null;
        JSONObject obj = readFileJson(extFile);
        if (obj != null) return obj;
        return readFileJson(new File(context.getFilesDir(), Config.FILE_NAME));
    }

    private static JSONObject readFileJson(File file) {
        if (file == null || !file.isFile() || file.length() <= 0 || file.length() > 64 * 1024) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            return new JSONObject(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    void start() {
        if (running) return;
        running = true;
        mainHandler.removeCallbacks(pollTask);
        mainHandler.post(pollTask);
        invalidate();
    }

    void stop() {
        running = false;
        mainHandler.removeCallbacks(pollTask);
    }

    String currentSummary() {
        return summary;
    }

    private void fetchWeather() {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "CleanYourTV-ViceCity/1.0");
                if (conn.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                JSONObject root = new JSONObject(sb.toString());
                JSONObject current = root.optJSONObject("current");
                if (current == null) return;
                int tempC = (int) Math.round(current.optDouble("temperature_2m", 24.0));
                int code = current.optInt("weather_code", 0);
                int newMode = codeToMode(code);
                String label = codeToLabel(code);
                String text = cityName + " " + tempC + "\u00B0C " + label;
                mainHandler.post(() -> {
                    mode = newMode;
                    summary = text;
                    if (listener != null) listener.onWeatherUpdated(text);
                    invalidate();
                });
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static int codeToMode(int code) {
        if (code >= 95) return MODE_STORM;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return MODE_RAIN;
        if (code == 45 || code == 48) return MODE_FOG;
        if (code >= 2 && code <= 3) return MODE_CLOUDY;
        return MODE_CLEAR;
    }

    private static String codeToLabel(int code) {
        if (code >= 95) return "STORM";
        if (code >= 80 && code <= 82) return "SHOWERS";
        if (code >= 61 && code <= 67) return "RAIN";
        if (code >= 51 && code <= 57) return "DRIZZLE";
        if (code >= 71 && code <= 77) return "SNOW";
        if (code == 45 || code == 48) return "MIST";
        if (code == 3) return "OVERCAST";
        if (code == 2) return "CLOUDY";
        if (code == 1) return "FAIR";
        return "CLEAR";
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            fogPaint.setShader(new LinearGradient(
                    0, h * 0.25f, 0, h,
                    0x08DCE8F5, 0x36B4C8E0, Shader.TileMode.CLAMP));
            initDrops(w, h);
        }
    }

    private void initDrops(int w, int h) {
        for (int i = 0; i < dropX.length; i++) {
            dropX[i] = random.nextInt(Math.max(1, w));
            dropY[i] = random.nextInt(Math.max(1, h));
            dropLen[i] = (18 + random.nextInt(22)) * density;
            dropSpeed[i] = (9 + random.nextInt(8)) * density;
        }
        dropsInitialised = true;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (mode == MODE_FOG) {
            canvas.drawRect(0, 0, w, h, fogPaint);
        } else if (mode == MODE_CLOUDY) {
            canvas.drawColor(0x16141C2E);
        } else if (mode == MODE_RAIN || mode == MODE_STORM) {
            if (!dropsInitialised) initDrops(w, h);
            for (int i = 0; i < dropX.length; i++) {
                float x = dropX[i], y = dropY[i], len = dropLen[i];
                canvas.drawLine(x, y, x - 5f * density, y + len, rainPaint);
                y += dropSpeed[i];
                x -= 2.2f * density;
                if (y > h) {
                    y = -len;
                    x = random.nextInt(w);
                }
                if (x < 0) x += w;
                dropX[i] = x;
                dropY[i] = y;
            }
            if (running) postInvalidateDelayed(33L);
        }
    }
}
