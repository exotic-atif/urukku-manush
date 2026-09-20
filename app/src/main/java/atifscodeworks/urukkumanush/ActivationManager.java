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
        if (isActivated()) {
            updateLauncherIcon(getActiveHeadIndex());
        }
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
                            .putString(KEY_HEAD, entry.headFile)
                            .putInt(KEY_HEAD_INDEX, entry.headIndex)
                            .apply();

                    // Dynamically update launcher app icon to the activated head
                    updateLauncherIcon(entry.headIndex);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error computing SHA-256 for activation", e);
        }
        return false;
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
                    pkg + ".MainActivityHead4"
            };

            for (int i = 0; i < aliases.length; i++) {
                int targetState = (i + 1 == headIndex)
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                pm.setComponentEnabledSetting(
                        new ComponentName(pkg, aliases[i]),
                        targetState,
                        PackageManager.DONT_KILL_APP
                );
            }

            // Disable default activity icon so launcher shows the active alias
            int defaultState = (headIndex >= 1 && headIndex <= 4)
                    ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            pm.setComponentEnabledSetting(
                    new ComponentName(pkg, defaultActivity),
                    defaultState,
                    PackageManager.DONT_KILL_APP
            );
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
        return 1;
    }

    public String getActiveCode() {
        return prefs.getString(KEY_CODE, "");
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

