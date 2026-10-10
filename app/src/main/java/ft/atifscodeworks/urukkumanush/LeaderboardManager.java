package ft.atifscodeworks.urukkumanush;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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

    public interface ProfileCallback {
        void onProfile(String name, int score, String characterUsed, String assetUrl);
    }

    public interface GoogleLoginCallback {
        void onSuccess(String name, int score, String characterUsed, String codeHash, String assetUrl, String googleEmail);
        void onEmailNotBound(String email);
        void onError(String message);
    }

    public String getPlayerName(int headIndex) {
        String saved = prefs.getString(KEY_PLAYER_NAME, "");
        int activeIdx = prefs.getInt("active_head_index", -1);
        if (!saved.isEmpty() && !saved.startsWith("Player") && (headIndex <= 0 || headIndex == activeIdx)) {
            return saved;
        }

        // Lookup dynamically from cached Supabase leaderboard table
        List<Entry> cached = getCachedEntries();
        for (Entry e : cached) {
            if ((e.characterUsed != null && e.characterUsed.equals("head_" + headIndex + ".png"))
                    || e.headIndex == headIndex) {
                if (e.name != null && !e.name.isEmpty() && !e.name.startsWith("Player")) {
                    return e.name;
                }
            }
        }
        return (!saved.isEmpty() && !saved.startsWith("Player")) ? saved : "Player";
    }

    public void setPlayerName(String name) {
        if (name != null && !name.trim().isEmpty() && !name.startsWith("Player")) {
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

    public void syncScoresWithServer(int localHighScore, String code, int headIndex, String characterUsed, SyncCallback callback) {
        syncScoresWithServer(localHighScore, code, headIndex, characterUsed, null, callback);
    }

    // Multi-device sync logic on activation or refresh:
    // Pulls dynamic profile from Supabase (name, score, asset_url), downloads/decrypts custom head, and resolves score
    public void syncScoresWithServer(int localHighScore, String code, int headIndex, String characterUsed, String rawCode, SyncCallback callback) {
        final String cleanCode = ActivationManager.formatActivationCode(code);
        final String cleanRawCode = ActivationManager.formatActivationCode(rawCode);
        executor.execute(() -> {
            int resolved = localHighScore;
            try {
                if (cleanCode != null && !cleanCode.isEmpty()) {
                    int serverScore = fetchServerProfileSync(cleanCode, cleanRawCode);
                    if (cleanRawCode != null && !cleanRawCode.isEmpty()) {
                        // Fresh activation: adopt authoritative server profile score
                        resolved = Math.max(0, serverScore);
                        setLastSyncedScore(resolved);
                        markNeedsSync(false);
                    } else {
                        // Normal gameplay / startup sync
                        if (serverScore > resolved) {
                            resolved = serverScore;
                            setLastSyncedScore(serverScore);
                            markNeedsSync(false);
                        } else if (resolved > serverScore || needsSync()) {
                            boolean ok = executePushScore(resolved, cleanCode, headIndex, characterUsed);
                            if (ok) {
                                setLastSyncedScore(resolved);
                                markNeedsSync(false);
                            } else {
                                markNeedsSync(true);
                            }
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

    public void fetchServerProfile(String codeHash, String rawCode, ProfileCallback callback) {
        final String cleanHash = ActivationManager.formatActivationCode(codeHash);
        final String cleanRaw = ActivationManager.formatActivationCode(rawCode);
        executor.execute(() -> {
            String name = "";
            int score = -1;
            String charUsed = "";
            String assetUrl = "";

            try {
                String url = SUPABASE_URL + "/rest/v1/leaderboard?code=eq." + cleanHash + "&select=name,score,character_used,asset_url";
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
                            JSONObject obj = arr.getJSONObject(0);
                            name = obj.optString("name", "");
                            score = obj.optInt("score", 0);
                            charUsed = obj.optString("character_used", "");
                            assetUrl = obj.optString("asset_url", "");

                            if (!name.isEmpty() && !name.startsWith("Player")) {
                                setPlayerName(name);
                            }

                            if (!assetUrl.isEmpty() && cleanRaw != null && !cleanRaw.isEmpty()) {
                                downloadAndDecryptHead(assetUrl, cleanRaw, charUsed);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error querying server profile for code: " + e.getMessage());
            }

            final String fName = name;
            final int fScore = score;
            final String fChar = charUsed;
            final String fAsset = assetUrl;
            if (callback != null) {
                mainHandler.post(() -> callback.onProfile(fName, fScore, fChar, fAsset));
            }
        });
    }

    private int fetchServerProfileSync(String codeHash, String rawCode) {
        String cleanHash = ActivationManager.formatActivationCode(codeHash);
        String cleanRaw = ActivationManager.formatActivationCode(rawCode);
        try {
            String url = SUPABASE_URL + "/rest/v1/leaderboard?code=eq." + cleanHash + "&select=name,score,character_used,asset_url";
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
                        JSONObject obj = arr.getJSONObject(0);
                        String name = obj.optString("name", "");
                        int serverScore = obj.optInt("score", 0);
                        String charUsed = obj.optString("character_used", "");
                        String assetUrl = obj.optString("asset_url", "");

                        if (!name.isEmpty() && !name.startsWith("Player")) {
                            setPlayerName(name);
                        }

                        // Download encrypted head asset if not yet downloaded
                        File targetHead = (charUsed != null && !charUsed.isEmpty())
                                ? new File(context.getFilesDir(), charUsed)
                                : new File(context.getFilesDir(), "custom_head.png");
                        if (!assetUrl.isEmpty() && (!targetHead.exists() || targetHead.length() == 0)) {
                            String codeToUse = (cleanRaw != null && !cleanRaw.isEmpty()) ? cleanRaw : cleanHash;
                            downloadAndDecryptHead(assetUrl, codeToUse, charUsed);
                        }
                        return serverScore;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error syncing server profile: " + e.getMessage());
        }
        return -1;
    }

    public void downloadAndDecryptHead(String assetUrl, String rawCode, String characterUsed) {
        String cleanCode = ActivationManager.formatActivationCode(rawCode);
        try {
            Request request = new Request.Builder().url(assetUrl).build();
            try (Response response = HttpClientProvider.get().newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    byte[] encBytes = response.body().bytes();
                    if (encBytes.length > 16) {
                        byte[] key;
                        if (cleanCode != null && cleanCode.length() == 64 && isHex(cleanCode)) {
                            key = hexToBytes(cleanCode);
                        } else {
                            MessageDigest md = MessageDigest.getInstance("SHA-256");
                            key = md.digest(cleanCode.getBytes(StandardCharsets.UTF_8));
                        }
                        byte[] decrypted = decryptAesCbc(encBytes, key);
                        if (decrypted != null && decrypted.length > 0) {
                            File headFile = new File(context.getFilesDir(), "custom_head.png");
                            try (FileOutputStream fos = new FileOutputStream(headFile)) {
                                fos.write(decrypted);
                            }
                            if (characterUsed != null && !characterUsed.isEmpty()) {
                                File namedHeadFile = new File(context.getFilesDir(), characterUsed);
                                try (FileOutputStream fos = new FileOutputStream(namedHeadFile)) {
                                    fos.write(decrypted);
                                }
                            }
                            Log.i(TAG, "Successfully downloaded and decrypted custom head asset (" + decrypted.length + " bytes)");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed downloading/decrypting custom head: " + e.getMessage());
        }
    }

    private static boolean isHex(String s) {
        if (s == null || s.length() != 64) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
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

    public void verifyGoogleAccountEmail(String email, GoogleLoginCallback callback) {
        executor.execute(() -> {
            try {
                if (email == null || email.trim().isEmpty()) {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("Invalid Google email address."));
                    }
                    return;
                }

                String trimmedEmail = email.trim();
                String encodedEmail = URLEncoder.encode(trimmedEmail, "UTF-8");
                String url = SUPABASE_URL + "/rest/v1/leaderboard?g_email=ilike." + encodedEmail + "&select=id,name,score,character_used,asset_url,code,g_email";

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
                            JSONObject obj = arr.getJSONObject(0);
                            String name = obj.optString("name", "");
                            int score = obj.optInt("score", 0);
                            String charUsed = obj.optString("character_used", "");
                            String assetUrl = obj.optString("asset_url", "");
                            String codeHash = obj.optString("code", "");
                            String boundEmail = obj.optString("g_email", trimmedEmail);

                            if (!name.isEmpty() && !name.startsWith("Player")) {
                                setPlayerName(name);
                            }

                            if (!assetUrl.isEmpty() && !codeHash.isEmpty()) {
                                downloadAndDecryptHead(assetUrl, codeHash, charUsed);
                            }

                            if (callback != null) {
                                mainHandler.post(() -> callback.onSuccess(name, score, charUsed, codeHash, assetUrl, boundEmail));
                            }
                        } else {
                            if (callback != null) {
                                mainHandler.post(() -> callback.onEmailNotBound(trimmedEmail));
                            }
                        }
                    } else {
                        String err = "Server responded with status " + response.code();
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError(err));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking Google account in leaderboard", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Network error"));
                }
            }
        });
    }

    private byte[] decryptAesCbc(byte[] encData, byte[] key) throws Exception {
        byte[] iv = new byte[16];
        System.arraycopy(encData, 0, iv, 0, 16);
        byte[] cipherText = new byte[encData.length - 16];
        System.arraycopy(encData, 16, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(cipherText);
    }

    private boolean executePushScore(int score, String code, int headIndex, String characterUsed) {
        try {
            // Upsert row by unique "code" column
            String url = SUPABASE_URL + "/rest/v1/leaderboard?on_conflict=code";
            JSONObject payload = new JSONObject();
            String playerName = prefs.getString(KEY_PLAYER_NAME, "");
            if (!playerName.isEmpty() && !playerName.startsWith("Player")) {
                payload.put("name", playerName);
            }
            payload.put("code", code);
            payload.put("score", score);
            if (characterUsed != null && !characterUsed.isEmpty()) {
                payload.put("character_used", characterUsed);
            }

            String token = prefs.getString(UrukkuManushMessagingService.KEY_FCM_TOKEN, "");
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
                if (headIndex <= 0 && charUsed != null && charUsed.startsWith("head_")) {
                    try {
                        String num = charUsed.replaceAll("[^0-9]", "");
                        if (!num.isEmpty()) {
                            headIndex = Integer.parseInt(num);
                        }
                    } catch (Exception ignored) {}
                }
                list.add(new Entry(name, score, charUsed, code, headIndex));
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
