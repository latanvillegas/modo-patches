package app.morphe.extension.cromite.amoled;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Window;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Runtime portion of Cromite patch 14. Android-framework-only implementation.
 *
 * This class must be packaged into the patched APK before adding bytecode calls
 * to it. The patcher Kotlin source is NOT itself part of the app runtime.
 */
public final class AmoledBrightnessController {
    private static final String PREFS = "morphe_cromite_amoled";
    private static final String KEY_MANUAL = "manual_window_brightness";
    private static final String KEY_BRIGHTNESS = "window_brightness_percent";
    private static final String KEY_WEB = "web_luminance_percent";
    private static final String KEY_AMOLED = "amoled_enabled";
    private static final String KEY_THEME = "browser_theme";
    private static final String KEY_WEB_MODE = "web_darkening_mode";
    private static volatile Context browserContext;

    private AmoledBrightnessController() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Called from ChromeTabbedActivity.onResume and whenever the dialog
     * changes the window-brightness setting. No WRITE_SETTINGS permission.
     */
    public static void applyWindowBrightness(Activity activity) {
        if (activity == null) return;
        browserContext = activity.getApplicationContext();
        SharedPreferences p = prefs(activity);
        float value = -1.0f; // Android: follow system brightness
        if (p.getBoolean(KEY_MANUAL, false)) {
            int percent = clamp(p.getInt(KEY_BRIGHTNESS, 50));
            // Android WindowManager expects (0,1] for explicit brightness.
            // Map UI 0% to a very dim but nonzero value, not the system sentinel.
            value = Math.max(0.01f, percent / 100.0f);
        }
        Window window = activity.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.screenBrightness = value;
        window.setAttributes(params);
    }

    /** In-app shortcut while Chromium's Theme settings preference hook is unverified. */
    public static void installSettingsShortcut(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Window window = activity.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout) decor;
        final String tag = "morphe_amoled_settings_shortcut";
        if (root.findViewWithTag(tag) != null) return;
        Button shortcut = new Button(activity);
        shortcut.setTag(tag);
        shortcut.setText("AMOLED");
        shortcut.setContentDescription("Abrir ajustes AMOLED y brillo");
        shortcut.setTextSize(12);
        shortcut.setAllCaps(false);
        int margin = (int) (16 * activity.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM);
        layout.setMargins(margin, margin, margin, margin);
        shortcut.setOnClickListener(view -> showSettings(activity));
        root.addView(shortcut, layout);
    }

    public static int webLuminancePercent(Context context) {
        return clamp(prefs(context).getInt(KEY_WEB, 100));
    }

    public static boolean amoledEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AMOLED, false);
    }

    /** Only active when AMOLED is on and automatic web darkening is selected. */
    /** Called by the patched Chromium web-content decision on its false path. */
    public static boolean shouldAutoDarkenWebContents() {
        Context context = browserContext;
        return context != null && automaticWebDarkeningEnabled(context);
    }

    public static boolean automaticWebDarkeningEnabled(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean(KEY_AMOLED, false)
                && "automatic".equals(p.getString(KEY_WEB_MODE, "manual"));
    }

    public static String browserTheme(Context context) {
        String value = prefs(context).getString(KEY_THEME, "device");
        if ("black".equals(value) || "white".equals(value)) return value;
        return "device";
    }

    /**
     * The controls persist their values; web luminance and theme need separate
     * verified Chromium rendering hooks before they can affect page/UI colors.
     */
    public static void showSettings(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences p = prefs(activity);
        final LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        panel.setPadding(pad, pad, pad, pad);

        TextView themeLabel = new TextView(activity);
        themeLabel.setText("Tema del navegador");
        panel.addView(themeLabel);

        android.widget.Spinner theme = new android.widget.Spinner(activity);
        final String[] themes = {"Sistema", "Negro puro", "Blanco puro"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                activity, android.R.layout.simple_spinner_dropdown_item, themes);
        theme.setAdapter(adapter);
        String savedTheme = browserTheme(activity);
        theme.setSelection("black".equals(savedTheme) ? 1 : "white".equals(savedTheme) ? 2 : 0);
        panel.addView(theme);

        CheckBox amoled = new CheckBox(activity);
        amoled.setText("AMOLED");
        amoled.setChecked(p.getBoolean(KEY_AMOLED, false));
        panel.addView(amoled);

        TextView webModeLabel = new TextView(activity);
        webModeLabel.setText("Oscurecimiento de páginas web");
        panel.addView(webModeLabel);
        android.widget.Spinner webMode = new android.widget.Spinner(activity);
        final String[] webModes = {"Manual (casilla de Cromite)", "Automático con AMOLED"};
        android.widget.ArrayAdapter<String> webModeAdapter = new android.widget.ArrayAdapter<>(
                activity, android.R.layout.simple_spinner_dropdown_item, webModes);
        webMode.setAdapter(webModeAdapter);
        webMode.setSelection("automatic".equals(p.getString(KEY_WEB_MODE, "manual")) ? 1 : 0);
        webMode.setEnabled(amoled.isChecked());
        panel.addView(webMode);

        CheckBox manual = new CheckBox(activity);
        manual.setText("Control manual del brillo de ventana");
        manual.setChecked(p.getBoolean(KEY_MANUAL, false));
        panel.addView(manual);

        TextView windowLabel = new TextView(activity);
        windowLabel.setText("Brillo de ventana (%)");
        panel.addView(windowLabel);
        SeekBar windowBar = new SeekBar(activity);
        windowBar.setMax(100);
        windowBar.setProgress(clamp(p.getInt(KEY_BRIGHTNESS, 50)));
        windowBar.setEnabled(manual.isChecked());
        panel.addView(windowBar);

        TextView webLabel = new TextView(activity);
        webLabel.setText("Luminancia web (%) — pendiente de integración");
        panel.addView(webLabel);
        SeekBar webBar = new SeekBar(activity);
        webBar.setMax(100);
        webBar.setProgress(webLuminancePercent(activity));
        panel.addView(webBar);

        manual.setOnCheckedChangeListener((button, checked) -> {
            p.edit().putBoolean(KEY_MANUAL, checked).apply();
            windowBar.setEnabled(checked);
            applyWindowBrightness(activity);
        });
        windowBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) {
                    p.edit().putInt(KEY_BRIGHTNESS, clamp(value)).apply();
                    applyWindowBrightness(activity);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        webBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) p.edit().putInt(KEY_WEB, clamp(value)).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        amoled.setOnCheckedChangeListener((button, checked) -> {
            p.edit().putBoolean(KEY_AMOLED, checked).apply();
            webMode.setEnabled(checked);
        });
        webMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    android.view.View view, int position, long id) {
                p.edit().putString(KEY_WEB_MODE, position == 1 ? "automatic" : "manual").apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        theme.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    android.view.View view, int position, long id) {
                String value = position == 1 ? "black" : position == 2 ? "white" : "device";
                p.edit().putString(KEY_THEME, value).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        new AlertDialog.Builder(activity)
                .setTitle("AMOLED y brillo")
                .setView(panel)
                .setPositiveButton("Cerrar", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
