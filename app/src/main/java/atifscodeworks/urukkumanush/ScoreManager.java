package atifscodeworks.urukkumanush;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class ScoreManager {
    private static final String TAG = "ScoreManager";
    private static final String PREFS_NAME = "urukku_manush_prefs";
    private static final String KEY_HIGH_SCORE = "high_score";
    private static final String KEY_SCORE_HASH = "high_score_hash";
    private static final String VAULT_FILE_NAME = "score_vault.dat";
    private static final String SALT = "MrJumper_AntiCheat_V3_#99@Atif";

    private final Context context;
    private final SharedPreferences prefs;
    private int highScore;

    public ScoreManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.highScore = loadVerifiedScore();

        // Safely remove legacy plaintext external files to prevent PC/USB tampering
        cleanLegacyExternalFiles();
    }

    public synchronized int getHighScore() {
        return highScore;
    }

    public synchronized boolean checkAndSaveScore(int score) {
        if (score > highScore) {
            this.highScore = score;
            saveVerifiedScore(highScore);
            return true;
        }
        return false;
    }

    public synchronized void setHighScore(int score) {
        if (score >= 0) {
            this.highScore = score;
            saveVerifiedScore(highScore);
        }
    }

    public synchronized void resetHighScore() {
        this.highScore = 0;
        saveVerifiedScore(0);
    }

    /**
     * Computes a cryptographic SHA-256 integrity hash for the given score
     * bound to the unique hardware Android ID and secret salt.
     */
    private String computeHash(int score) {
        try {
            String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (deviceId == null) {
                deviceId = "default_device";
            }
            String raw = score + ":" + deviceId + ":" + SALT;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error computing score hash", e);
            return "";
        }
    }

    /**
     * Loads and verifies the high score from internal private app storage (/data/data/...).
     * Falls back to SharedPreferences or migrates legacy scores if present.
     */
    private int loadVerifiedScore() {
        File vaultFile = new File(context.getFilesDir(), VAULT_FILE_NAME);
        if (vaultFile.exists()) {
            try (FileInputStream fis = new FileInputStream(vaultFile)) {
                byte[] data = new byte[(int) vaultFile.length()];
                int read = fis.read(data);
                if (read > 0) {
                    JSONObject json = new JSONObject(new String(data, StandardCharsets.UTF_8));
                    int savedScore = json.optInt("score", 0);
                    String savedHash = json.optString("hash", "");
                    String expectedHash = computeHash(savedScore);

                    if (!expectedHash.isEmpty() && expectedHash.equals(savedHash)) {
                        return savedScore;
                    } else {
                        Log.w(TAG, "Score vault tampering detected! Hash mismatch.");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to read internal score vault", e);
            }
        }

        // Fallback / First-run migration from SharedPreferences (for v2.1.1 or lower upgrades)
        int legacyPrefScore = prefs.getInt(KEY_HIGH_SCORE, 0);
        String legacyHash = prefs.getString(KEY_SCORE_HASH, "");
        if (legacyPrefScore > 0) {
            String expectedLegacyHash = computeHash(legacyPrefScore);
            if (legacyHash.isEmpty() || expectedLegacyHash.equals(legacyHash)) {
                // Migrate to secure vault
                saveVerifiedScore(legacyPrefScore);
                return legacyPrefScore;
            }
        }

        return 0;
    }

    /**
     * Saves the verified score to internal private storage and SharedPreferences with its signature.
     */
    private void saveVerifiedScore(int score) {
        String hash = computeHash(score);
        try {
            JSONObject json = new JSONObject();
            json.put("score", score);
            json.put("hash", hash);

            File vaultFile = new File(context.getFilesDir(), VAULT_FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(vaultFile)) {
                fos.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write internal score vault", e);
        }

        // Also keep synced in private SharedPreferences with hash
        prefs.edit()
                .putInt(KEY_HIGH_SCORE, score)
                .putString(KEY_SCORE_HASH, hash)
                .apply();
    }

    /**
     * Removes the insecure plaintext /sdcard/Android/data/.../highscore.txt file if it exists.
     */
    private void cleanLegacyExternalFiles() {
        try {
            File extDir = context.getExternalFilesDir(null);
            if (extDir != null && extDir.exists()) {
                File scoreFile = new File(extDir, "highscore.txt");
                if (scoreFile.exists()) {
                    boolean deleted = scoreFile.delete();
                    Log.d(TAG, "Cleaned legacy external highscore.txt: " + deleted);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not clean legacy external file", e);
        }
    }
}
