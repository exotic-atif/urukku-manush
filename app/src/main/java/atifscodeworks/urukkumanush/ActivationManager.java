package atifscodeworks.urukkumanush;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ActivationManager {
    private static final String TAG = "ActivationManager";
    private static final String PREFS_NAME = "urukku_manush_prefs";
    private static final String KEY_ACTIVATED = "is_activated";
    private static final String KEY_CODE = "activation_code";
    private static final String KEY_HEAD = "active_head";

    private final SharedPreferences prefs;
    private final Map<String, String> codeToHeadMap = new LinkedHashMap<>();

    public ActivationManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadCodesFromAssets(context);
    }

    private void loadCodesFromAssets(Context context) {
        try (InputStream is = context.getAssets().open("txt/actv_code.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    String code = parts[0].trim();
                    String head = parts[1].trim();
                    if (!code.isEmpty() && !head.isEmpty()) {
                        codeToHeadMap.put(code, head);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading actv_code.txt from assets", e);
            codeToHeadMap.put("dA5V1KRaM", "head_2.png");
            codeToHeadMap.put("gH05h4Nur4G", "head_1.png");
            codeToHeadMap.put("4r5H4NAd1", "head_3.png");
            codeToHeadMap.put("k4rM4k4rt4N1yA", "head_4.png");
        }
    }

    public boolean isActivated() {
        return prefs.getBoolean(KEY_ACTIVATED, false);
    }

    public boolean activate(String codeInput) {
        if (codeInput == null) return false;
        String trimmed = codeInput.trim();
        if (codeToHeadMap.containsKey(trimmed)) {
            String head = codeToHeadMap.get(trimmed);
            prefs.edit()
                    .putBoolean(KEY_ACTIVATED, true)
                    .putString(KEY_CODE, trimmed)
                    .putString(KEY_HEAD, head)
                    .apply();
            return true;
        }
        return false;
    }

    public String getActiveHead() {
        return prefs.getString(KEY_HEAD, "head_1.png");
    }

    public String getActiveCode() {
        return prefs.getString(KEY_CODE, "");
    }

    public Map<String, String> getCodeToHeadMap() {
        return Collections.unmodifiableMap(codeToHeadMap);
    }
}
