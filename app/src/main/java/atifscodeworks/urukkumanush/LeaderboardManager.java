package atifscodeworks.urukkumanush;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LeaderboardManager {
    private static final String TAG = "LeaderboardManager";
    private static final String PREFS_NAME = "urukku_manush_prefs";
    private static final String KEY_PLAYER_NAME = "saved_player_name";
    private static final String KEY_SUPABASE_ANON_KEY = "supabase_anon_key";

    // Supabase project: https://ibsvgxwihatdcsccqseq.supabase.co
    public static final String SUPABASE_URL = "https://ibsvgxwihatdcsccqseq.supabase.co";
    // Default placeholder anon key - player/admin can also customize it in settings
    public static final String DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummy_or_user_key";

    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class Entry {
        public final String playerName;
        public final int score;
        public final String characterUsed;

        public Entry(String playerName, int score, String characterUsed) {
            this.playerName = playerName;
            this.score = score;
            this.characterUsed = characterUsed;
        }
    }

    public interface FetchCallback {
        void onSuccess(List<Entry> entries);
        void onError(String message);
    }

    public interface SubmitCallback {
        void onSuccess();
        void onError(String message);
    }

    public LeaderboardManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getPlayerName() {
        return prefs.getString(KEY_PLAYER_NAME, "Player");
    }

    public void setPlayerName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            prefs.edit().putString(KEY_PLAYER_NAME, name.trim()).apply();
        }
    }

    public String getAnonKey() {
        return prefs.getString(KEY_SUPABASE_ANON_KEY, DEFAULT_ANON_KEY);
    }

    public void setAnonKey(String key) {
        if (key != null && !key.trim().isEmpty()) {
            prefs.edit().putString(KEY_SUPABASE_ANON_KEY, key.trim()).apply();
        }
    }

    public void fetchTopScores(FetchCallback callback) {
        executor.execute(() -> {
            try {
                String endpoint = SUPABASE_URL + "/rest/v1/leaderboard?select=player_name,score,character_used&order=score.desc&limit=10";
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", getAnonKey());
                conn.setRequestProperty("Authorization", "Bearer " + getAnonKey());
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONArray arr = new JSONArray(sb.toString());
                    List<Entry> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String name = obj.optString("player_name", "Unknown");
                        int score = obj.optInt("score", 0);
                        String charUsed = obj.optString("character_used", "head_1.png");
                        list.add(new Entry(name, score, charUsed));
                    }
                    mainHandler.post(() -> callback.onSuccess(list));
                } else {
                    mainHandler.post(() -> callback.onError("Server returned response code: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching leaderboard", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Network error"));
            }
        });
    }

    public void submitScore(String playerName, int score, String characterUsed, SubmitCallback callback) {
        executor.execute(() -> {
            try {
                String endpoint = SUPABASE_URL + "/rest/v1/leaderboard";
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("apikey", getAnonKey());
                conn.setRequestProperty("Authorization", "Bearer " + getAnonKey());
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Prefer", "return=minimal");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                JSONObject payload = new JSONObject();
                payload.put("player_name", (playerName != null && !playerName.isEmpty()) ? playerName : "Player");
                payload.put("score", score);
                payload.put("character_used", (characterUsed != null) ? characterUsed : "head_1.png");

                byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data);
                    os.flush();
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    mainHandler.post(() -> callback.onError("Score submit failed: code " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error submitting score", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Network error"));
            }
        });
    }
}
