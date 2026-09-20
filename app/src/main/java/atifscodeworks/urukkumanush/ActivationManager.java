package atifscodeworks.urukkumanush;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ActivationManager {
    private static final String TAG = "ActivationManager";
    private static final String PREFS_NAME = "urukku_manush_prefs";
    private static final String KEY_ACTIVATED = "is_activated";
    private static final String KEY_CODE = "activation_code";
    private static final String KEY_HEAD = "active_head";
    private static final String KEY_HEAD_INDEX = "active_head_index";

    private static final String KEY_PENDING_ICON_HEAD = "pending_launcher_icon_head";
    private static final String KEY_APPLIED_ICON_HEAD = "applied_launcher_icon_head";

    private final SharedPreferences prefs;
    private final Context context;

    // Map of 32-byte SHA256 hex string -> head info
    private static class Entry {
        final byte[] hash;
        final int headIndex;
        final String headFile;

        Entry(byte[] hash, int headIndex, String headFile) {
            this.hash = hash;
            this.headIndex = headIndex;
            this.headFile = headFile;
        }
    }

    private final Map<String, Entry> hashToEntryMap = new HashMap<>();

    public ActivationManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadBinaryActivationHashes();
    }

    private void loadBinaryActivationHashes() {
        try (InputStream is = context.getAssets().open("bin/actv.bin")) {
            byte[] magic = new byte[4];
            int read = is.read(magic);
            if (read != 4 || magic[0] != 0x55 || magic[1] != 0x4D || magic[2] != 0x41 || magic[3] != 0x42) {
                Log.e(TAG, "Invalid actv.bin magic header");
                loadFallbackHashes();
                return;
            }
            int version = is.read();
            int count = is.read();
            for (int i = 0; i < count; i++) {
                byte[] hash = new byte[32];
                int hRead = is.read(hash);
                int headIdx = is.read();
                if (hRead == 32 && headIdx != -1) {
                    String hex = bytesToHex(hash);
                    hashToEntryMap.put(hex, new Entry(hash, headIdx, "head_" + headIdx + ".png"));
                }
            }
            Log.i(TAG, "Loaded " + hashToEntryMap.size() + " activation hash definitions from actv.bin");
        } catch (Exception e) {
            Log.e(TAG, "Error loading actv.bin from assets, using compiled hashes", e);
            loadFallbackHashes();
        }
    }

    private void loadFallbackHashes() {
        // Precomputed SHA-256 digests
        addFallback("702435e06380db25d3a84b28f69b5356f907f119b6630a5058288210c1fd23d1", 1);
        addFallback("9256f3db4e37f29c3e0ab6903b2fcc58c85c2a6e34654d73000927d3fa38064f", 2);
        addFallback("2941ab5bd328ed9d52c03bdcaede67feb1864316dcead6c49eb1838488dacdef", 3);
        addFallback("9fcb2ea43cafc7762016761b378e1bc2918ef8a65f77911315011323c259bb54", 4);
        addFallback("f4cdaaa043a69dfa07a7c20fa1c5b15d3b5f8e6ef89a7fd98f12b9055a2857d2", 5);
        addFallback("d07d632ac8c3f13b7aecbb0fa582eee0b301802db98689f0c0f64c3753aee336", 6);
    }

    private void addFallback(String hex, int headIdx) {
        hashToEntryMap.put(hex, new Entry(hexToBytes(hex), headIdx, "head_" + headIdx + ".png"));
    }

    public boolean isActivated() {
        return prefs.getBoolean(KEY_ACTIVATED, false);
    }

    public boolean activate(String codeInput) {
        if (codeInput == null) return false;
        String trimmed = codeInput.trim();
        if (trimmed.isEmpty()) return false;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] inputHash = digest.digest(trimmed.getBytes(StandardCharsets.UTF_8));
            String hex = bytesToHex(inputHash);

            if (hashToEntryMap.containsKey(hex)) {
                Entry entry = hashToEntryMap.get(hex);
                if (entry != null) {
                    prefs.edit()
                            .putBoolean(KEY_ACTIVATED, true)
                            .putString(KEY_CODE, trimmed)
                            .putString("activation_hash", hex)
                            .putString(KEY_HEAD, entry.headFile)
                            .putInt(KEY_HEAD_INDEX, entry.headIndex)
                            .putInt(KEY_PENDING_ICON_HEAD, entry.headIndex)
                            .apply();

                    // Defer launcher icon update to app close / exit so the active game does not get terminated
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error computing SHA-256 for activation", e);
        }
        return false;
    }

    /**
     * Applies pending launcher icon update when the user closes or minimizes the app.
     * In Android, modifying component enabled states can cause the running process to restart/finish.
     * Doing this onStop/onDestroy ensures the user quits normally without unexpected interruption during gameplay.
     */
    public void applyPendingLauncherIconUpdate() {
        try {
            int pending = prefs.getInt(KEY_PENDING_ICON_HEAD, -1);
            if (pending >= 1 && pending <= 6) {
                prefs.edit().remove(KEY_PENDING_ICON_HEAD).putInt(KEY_APPLIED_ICON_HEAD, pending).apply();
                updateLauncherIcon(pending);
                return;
            }

            if (isActivated()) {
                int activeHead = getActiveHeadIndex();
                int applied = prefs.getInt(KEY_APPLIED_ICON_HEAD, -1);
                if (applied != activeHead) {
                    prefs.edit().putInt(KEY_APPLIED_ICON_HEAD, activeHead).apply();
                    updateLauncherIcon(activeHead);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed applying pending launcher icon update", e);
        }
    }

    public void updateLauncherIcon(int headIndex) {
        try {
            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();

            String defaultActivity = pkg + ".MainActivity";
            String[] aliases = new String[]{
                    pkg + ".MainActivityHead1",
                    pkg + ".MainActivityHead2",
                    pkg + ".MainActivityHead3",
                    pkg + ".MainActivityHead4",
                    pkg + ".MainActivityHead5",
                    pkg + ".MainActivityHead6"
            };

            for (int i = 0; i < aliases.length; i++) {
                int targetState = (i + 1 == headIndex)
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                ComponentName comp = new ComponentName(pkg, aliases[i]);
                if (pm.getComponentEnabledSetting(comp) != targetState) {
                    pm.setComponentEnabledSetting(
                            comp,
                            targetState,
                            PackageManager.DONT_KILL_APP
                    );
                }
            }

            // Disable default activity icon so launcher shows the active alias
            int defaultState = (headIndex >= 1 && headIndex <= 6)
                    ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            ComponentName defComp = new ComponentName(pkg, defaultActivity);
            if (pm.getComponentEnabledSetting(defComp) != defaultState) {
                pm.setComponentEnabledSetting(
                        defComp,
                        defaultState,
                        PackageManager.DONT_KILL_APP
                );
            }
            Log.i(TAG, "Launcher icon updated for head " + headIndex);
        } catch (Exception e) {
            Log.e(TAG, "Failed updating launcher icon", e);
        }
    }

    public String getActiveHead() {
        return prefs.getString(KEY_HEAD, "head_1.png");
    }

    public int getActiveHeadIndex() {
        int idx = prefs.getInt(KEY_HEAD_INDEX, -1);
        if (idx != -1) return idx;
        String head = getActiveHead();
        if (head.contains("1")) return 1;
        if (head.contains("2")) return 2;
        if (head.contains("3")) return 3;
        if (head.contains("4")) return 4;
        if (head.contains("5")) return 5;
        if (head.contains("6")) return 6;
        return 1;
    }

    public String getActiveCode() {
        return prefs.getString(KEY_CODE, "");
    }

    public String getActiveHash() {
        String savedHash = prefs.getString("activation_hash", "");
        if (!savedHash.isEmpty()) return savedHash;
        String code = getActiveCode();
        if (!code.isEmpty()) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] inputHash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
                String hex = bytesToHex(inputHash);
                prefs.edit().putString("activation_hash", hex).apply();
                return hex;
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

