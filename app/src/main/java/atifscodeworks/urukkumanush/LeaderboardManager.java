package atifscodeworks.urukkumanush;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LeaderboardManager {
    private static final String TAG = "LeaderboardManager";
    private static final String PREFS_NAME = "urukku_manush_prefs";
    private static final String KEY_PLAYER_NAME = "saved_player_name";
    private static final String KEY_SUPABASE_ANON_KEY = "supabase_anon_key";
    private static final String KEY_CACHED_LEADERBOARD = "cached_leaderboard_json";
    private static final String KEY_NEEDS_SYNC = "leaderboard_needs_sync";
    private static final String KEY_LAST_SYNCED_SCORE = "leaderboard_last_synced_score";

    public static final String SUPABASE_URL = "https://ibsvgxwihatdcsccqseq.supabase.co";
    // Project anon key placeholder or configured key
    public static final String DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imlic3ZneHdpaGF0ZGNzY2Nxc2VxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM2ODg1MTUsImV4cCI6MjA5OTI2NDUxNX0.tfqZUSKdyUl4SiPwwjFBoJ1Ss141i-ZU8jltvHv47L4";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class Entry {
        public final String name;
        public final int score;
        public final String characterUsed;
        public final String code;
        public final int headIndex;

        public Entry(String name, int score, String characterUsed) {
            this(name, score, characterUsed, "", -1);
        }

        public Entry(String name, int score, String characterUsed, String code, int headIndex) {
            this.name = name;
            this.score = score;
            this.characterUsed = characterUsed;
            this.code = code;
            this.headIndex = headIndex;
        }
    }

    public interface FetchCallback {
        void onSuccess(List<Entry> entries, boolean isFromCache);
        void onError(String message);
    }

    public interface SyncCallback {
        void onSynced(int resolvedHighScore);
    }

    public LeaderboardManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getAnonKey() {
        return prefs.getString(KEY_SUPABASE_ANON_KEY, DEFAULT_ANON_KEY);
    }

    public void setAnonKey(String key) {
        if (key != null && !key.trim().isEmpty()) {
            prefs.edit().putString(KEY_SUPABASE_ANON_KEY, key.trim()).apply();
        }
    }

    public String getPlayerName(int headIndex) {
        String saved = prefs.getString(KEY_PLAYER_NAME, "");
        if (!saved.isEmpty()) return saved;

        // Lookup dynamically from cached Supabase leaderboard table
        List<Entry> cached = getCachedEntries();
        for (Entry e : cached) {
            if ((e.characterUsed != null && e.characterUsed.equals("head_" + headIndex + ".png"))
                    || e.headIndex == headIndex) {
                if (e.name != null && !e.name.isEmpty()) {
                    prefs.edit().putString(KEY_PLAYER_NAME, e.name).apply();
                    return e.name;
                }
            }
        }
        return "Player " + headIndex;
    }

    public void setPlayerName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            prefs.edit().putString(KEY_PLAYER_NAME, name.trim()).apply();
        }
    }

    public boolean needsSync() {
        return prefs.getBoolean(KEY_NEEDS_SYNC, false);
    }

    public void markNeedsSync(boolean needs) {
        prefs.edit().putBoolean(KEY_NEEDS_SYNC, needs).apply();
    }

    public int getLastSyncedScore() {
        return prefs.getInt(KEY_LAST_SYNCED_SCORE, 0);
    }

    public void setLastSyncedScore(int score) {
        prefs.edit().putInt(KEY_LAST_SYNCED_SCORE, score).apply();
    }

    // Auto-sync when a new highscore is achieved during gameplay (silent background sync)
    public void autoSyncHighScore(int localHighScore, String code, int headIndex, String characterUsed) {
        if (code == null || code.isEmpty() || localHighScore <= 0) return;
        if (localHighScore <= getLastSyncedScore()) return;

        executor.execute(() -> {
            boolean success = executePushScore(localHighScore, code, headIndex, characterUsed);
            if (success) {
                setLastSyncedScore(localHighScore);
                markNeedsSync(false);
            } else {
                markNeedsSync(true);
            }
        });
    }

    // Multi-device sync logic on activation or refresh:
    // local > server -> push local
    // local < server -> pull server
    public void syncScoresWithServer(int localHighScore, String code, int headIndex, String characterUsed, SyncCallback callback) {
        executor.execute(() -> {
            int resolved = localHighScore;
            try {
                if (code != null && !code.isEmpty()) {
                    int serverScore = fetchServerScoreForCode(code);
                    if (serverScore > resolved) {
                        resolved = serverScore;
                        setLastSyncedScore(serverScore);
                        markNeedsSync(false);
                    } else if (resolved > serverScore || needsSync()) {
                        boolean ok = executePushScore(resolved, code, headIndex, characterUsed);
                        if (ok) {
                            setLastSyncedScore(resolved);
                            markNeedsSync(false);
                        } else {
                            markNeedsSync(true);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Multi-device score sync error: " + e.getMessage());
            }

            final int finalScore = resolved;
            if (callback != null) {
                mainHandler.post(() -> callback.onSynced(finalScore));
            }
        });
    }

    private int fetchServerScoreForCode(String code) {
        try {
            String url = SUPABASE_URL + "/rest/v1/leaderboard?code=eq." + code + "&select=score";
            Request request = new Request.Builder()
                    .url(url)
                    .header("apikey", getAnonKey())
                    .header("Authorization", "Bearer " + getAnonKey())
                    .header("Accept", "application/json")
                    .build();

            try (Response response = HttpClientProvider.get().newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonStr = response.body().string();
                    JSONArray arr = new JSONArray(jsonStr);
                    if (arr.length() > 0) {
                        return arr.getJSONObject(0).optInt("score", 0);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error querying server score for code: " + e.getMessage());
        }
        return -1;
    }

    private boolean executePushScore(int score, String code, int headIndex, String characterUsed) {
        try {
            // Upsert row by unique "code" column
            String url = SUPABASE_URL + "/rest/v1/leaderboard?on_conflict=code";
            JSONObject payload = new JSONObject();
            payload.put("name", getPlayerName(headIndex));
            payload.put("code", code);
            payload.put("score", score);
            payload.put("character_used", characterUsed != null ? characterUsed : "head_1.png");

            String token = prefs.getString(MrJumperMessagingService.KEY_FCM_TOKEN, "");
            if (!token.isEmpty()) {
                payload.put("fcm_token", token);
            }

            RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(url)
                    .header("apikey", getAnonKey())
                    .header("Authorization", "Bearer " + getAnonKey())
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = HttpClientProvider.get().newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed pushing score to Supabase", e);
            return false;
        }
    }

    public void registerDeviceToken(String fcmToken, String code) {
        if (fcmToken == null || fcmToken.isEmpty() || code == null || code.isEmpty()) return;
        executor.execute(() -> {
            try {
                String url = SUPABASE_URL + "/rest/v1/leaderboard?code=eq." + code;
                JSONObject payload = new JSONObject();
                payload.put("fcm_token", fcmToken);

                RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);
                Request request = new Request.Builder()
                        .url(url)
                        .header("apikey", getAnonKey())
                        .header("Authorization", "Bearer " + getAnonKey())
                        .header("Content-Type", "application/json")
                        .patch(body)
                        .build();

                try (Response response = HttpClientProvider.get().newCall(request).execute()) {
                    Log.i(TAG, "Device token registered with Supabase: " + response.isSuccessful());
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed registering FCM device token to Supabase", e);
            }
        });
    }

    // Fetch leaderboard with offline cache support
    public void fetchLeaderboard(FetchCallback callback) {
        executor.execute(() -> {
            try {
                String endpoint = SUPABASE_URL + "/rest/v1/leaderboard?select=name,score,character_used&order=score.desc&limit=10";
                Request request = new Request.Builder()
                        .url(endpoint)
                        .header("apikey", getAnonKey())
                        .header("Authorization", "Bearer " + getAnonKey())
                        .header("Accept", "application/json")
                        .build();

                try (Response response = HttpClientProvider.get().newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String bodyStr = response.body().string();
                        // Cache for offline viewing
                        prefs.edit().putString(KEY_CACHED_LEADERBOARD, bodyStr).apply();

                        List<Entry> list = parseEntries(bodyStr);
                        mainHandler.post(() -> callback.onSuccess(list, false));
                        return;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Network error fetching live leaderboard, loading cache: " + e.getMessage());
            }

            // Offline Cache Fallback
            List<Entry> cached = getCachedEntries();
            if (!cached.isEmpty()) {
                mainHandler.post(() -> callback.onSuccess(cached, true));
            } else {
                mainHandler.post(() -> callback.onError("No network connection & no cached leaderboard available."));
            }
        });
    }

    public List<Entry> getCachedEntries() {
        String cachedJson = prefs.getString(KEY_CACHED_LEADERBOARD, "");
        if (!cachedJson.isEmpty()) {
            return parseEntries(cachedJson);
        }
        return new ArrayList<>();
    }

    private List<Entry> parseEntries(String jsonStr) {
        List<Entry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.optString("name", obj.optString("player_name", "Player"));
                int score = obj.optInt("score", 0);
                String charUsed = obj.optString("character_used", "head_1.png");
                String code = obj.optString("code", obj.optString("activation_hash", ""));
                int headIndex = obj.optInt("head_index", -1);
                list.add(new Entry(name, score, charUsed, code, headIndex));
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
