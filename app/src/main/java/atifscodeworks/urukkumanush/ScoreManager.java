package atifscodeworks.urukkumanush;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class ScoreManager {
    private static final String TAG = "ScoreManager";
    private static final String PREFS_NAME = "urukku_manush_prefs";
    private static final String KEY_HIGH_SCORE = "high_score";

    private final Context context;
    private final SharedPreferences prefs;
    private int highScore;

    public ScoreManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.highScore = prefs.getInt(KEY_HIGH_SCORE, 0);

        // Ensure external storage folder sdcard/Android/data/atifscodeworks.urukkumanush/ exists
        syncExternalBackup();
    }

    public int getHighScore() {
        return highScore;
    }

    public boolean checkAndSaveScore(int score) {
        if (score > highScore) {
            highScore = score;
            prefs.edit().putInt(KEY_HIGH_SCORE, highScore).apply();
            syncExternalBackup();
            return true;
        }
        return false;
    }

    public void resetHighScore() {
        highScore = 0;
        prefs.edit().putInt(KEY_HIGH_SCORE, 0).apply();
        syncExternalBackup();
    }

    /**
     * Ensures sdcard/Android/data/atifscodeworks.urukkumanush/files/ exists
     * and saves an external high score backup file.
     */
    private void syncExternalBackup() {
        try {
            File extDir = context.getExternalFilesDir(null);
            if (extDir != null) {
                if (!extDir.exists()) {
                    extDir.mkdirs();
                }
                File scoreFile = new File(extDir, "highscore.txt");
                try (FileOutputStream fos = new FileOutputStream(scoreFile)) {
                    String data = "Game: Urukku Manush\nDeveloper: Atif (Exotic Atif)\nHigh Score: " + highScore + "\n";
                    fos.write(data.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing external score backup", e);
        }
    }
}
