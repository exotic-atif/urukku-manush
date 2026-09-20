package atifscodeworks.urukkumanush;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

public class AppUpdater {
    private static final String TAG = "AppUpdater";

    public static final String GITHUB_REPO_API = "https://api.github.com/repos/exotic-atif/urukku-manush/releases/latest";
    public static final String GITHUB_RAW_VERSION = "https://raw.githubusercontent.com/exotic-atif/urukku-manush/main/version.json";
    public static final String GITHUB_RELEASES_WEB = "https://github.com/exotic-atif/urukku-manush/releases";

    private static final String PREFS_NAME = "app_updater_prefs";
    private static final String KEY_DOWNLOADED_VERSION = "downloaded_apk_version";
    private static final String KEY_DOWNLOADED_PATH = "downloaded_apk_path";
    private static final String KEY_DOWNLOADED_SIZE = "downloaded_apk_size";

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile Call activeCall = null;
    private volatile boolean isCancelled = false;
    private volatile boolean isPaused = false;
    private volatile ReleaseInfo currentReleaseInfo = null;
    private volatile DownloadProgressCallback activeCallback = null;

    public static class ReleaseInfo {
        public final String tagName;
        public final String releaseTitle;
        public final String changelog;
        public final String downloadUrl;
        public final String htmlUrl;
        public final long fileSizeBytes;
        public final boolean isUpdateAvailable;

        public ReleaseInfo(String tagName, String releaseTitle, String changelog, String downloadUrl, String htmlUrl, long fileSizeBytes, boolean isUpdateAvailable) {
            this.tagName = tagName;
            this.releaseTitle = releaseTitle;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
            this.htmlUrl = htmlUrl;
            this.fileSizeBytes = fileSizeBytes;
            this.isUpdateAvailable = isUpdateAvailable;
        }

        public String getFormattedSize() {
            if (fileSizeBytes <= 0) return "Unknown size";
            double mb = fileSizeBytes / (1024.0 * 1024.0);
            return String.format(java.util.Locale.US, "%.1f MB", mb);
        }
    }

    public interface CheckCallback {
        void onSuccess(ReleaseInfo releaseInfo);
        void onError(String error);
    }

    public interface DownloadProgressCallback {
        void onProgress(int progressPercent, long downloadedBytes, long totalBytes, float speedMBs);
        void onComplete(File apkFile);
        void onError(String error);
    }

    public AppUpdater(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cleanOldUpdates();
    }

    public String getCurrentVersion() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "2.0.0";
        } catch (Exception e) {
            return "2.0.0";
        }
    }

    /**
     * Checks if the complete APK for the given release is already downloaded locally.
     * Prevents re-downloading and allows immediate installation.
     */
    public File getDownloadedApkFile(ReleaseInfo info) {
        if (info == null) return null;
        try {
            String savedVer = prefs.getString(KEY_DOWNLOADED_VERSION, "");
            String targetVer = info.tagName != null ? info.tagName.trim() : "";
            if (savedVer.isEmpty() || !savedVer.equalsIgnoreCase(targetVer)) {
                return null;
            }
            String path = prefs.getString(KEY_DOWNLOADED_PATH, "");
            if (path.isEmpty()) return null;
            File file = new File(path);
            if (file.exists() && file.isFile() && file.length() > 5 * 1024 * 1024) {
                // If expected size is available, check match or close tolerance
                if (info.fileSizeBytes > 0 && Math.abs(file.length() - info.fileSizeBytes) > 1024) {
                    return null;
                }
                return file;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public boolean isApkAlreadyDownloaded(ReleaseInfo info) {
        return getDownloadedApkFile(info) != null;
    }

    /**
     * Deletes stale APKs if current installed version >= downloaded version,
     * so space is freed after installation or when a new version drops.
     */
    public void cleanOldUpdates() {
        executor.execute(() -> {
            try {
                String curVer = getCurrentVersion().replaceAll("[^0-9.]", "");
                String dlVer = prefs.getString(KEY_DOWNLOADED_VERSION, "").replaceAll("[^0-9.]", "");
                boolean shouldCleanAll = dlVer.isEmpty() || !isVersionGreater(dlVer, curVer);

                File dir = context.getExternalFilesDir("updates");
                if (dir != null && dir.exists()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.getName().endsWith(".apk") || f.getName().endsWith(".part")) {
                                if (shouldCleanAll || !f.getName().contains(dlVer)) {
                                    f.delete();
                                }
                            }
                        }
                    }
                }
                if (shouldCleanAll) {
                    prefs.edit().remove(KEY_DOWNLOADED_VERSION).remove(KEY_DOWNLOADED_PATH).remove(KEY_DOWNLOADED_SIZE).apply();
                }
            } catch (Exception ignored) {
            }
        });
    }

    public void checkForUpdates(CheckCallback callback) {
        executor.execute(() -> {
            StringBuilder errLog = new StringBuilder();

            // First attempt: GitHub API
            try {
                ReleaseInfo apiInfo = tryFetchGitHubApi();
                if (apiInfo != null) {
                    mainHandler.post(() -> callback.onSuccess(apiInfo));
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "GitHub API fetch failed: " + e.getMessage(), e);
                errLog.append("GitHub API: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            }

            // Fallback attempt: GitHub Raw version.json
            try {
                ReleaseInfo rawInfo = tryFetchRawVersionJson();
                if (rawInfo != null) {
                    mainHandler.post(() -> callback.onSuccess(rawInfo));
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "GitHub Raw fetch failed: " + e.getMessage(), e);
                if (errLog.length() > 0) errLog.append("\n");
                errLog.append("GitHub Raw: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            }

            String finalMsg = errLog.length() > 0 ? errLog.toString() : "Unable to reach GitHub update servers.";
            mainHandler.post(() -> callback.onError(finalMsg));
        });
    }

    private ReleaseInfo tryFetchGitHubApi() throws Exception {
        Request request = new Request.Builder()
                .url(GITHUB_REPO_API)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android)")
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        try (Response response = HttpClientProvider.get().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new java.io.IOException("HTTP " + response.code() + " " + response.message());
            }
            if (response.body() == null) {
                throw new java.io.IOException("Empty response body from GitHub");
            }
            String body = response.body().string();
            JSONObject json = new JSONObject(body);
            String tagName = json.optString("tag_name", "v2.0.0");
            String releaseTitle = json.optString("name", "Urukku Manush " + tagName);
            String changelog = json.optString("body", "• Performance optimizations and bug fixes.\n• High refresh rate display support.\n• Security and leaderboard updates.");
            String htmlUrl = json.optString("html_url", GITHUB_RELEASES_WEB);

            String downloadUrl = "";
            long sizeBytes = 0;

            JSONArray assets = json.optJSONArray("assets");
            if (assets != null && assets.length() > 0) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String assetName = asset.optString("name", "");
                    if (assetName.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "");
                        sizeBytes = asset.optLong("size", 0);
                        break;
                    }
                }
            }

            if (downloadUrl.isEmpty()) {
                downloadUrl = "https://github.com/exotic-atif/urukku-manush/releases/latest/download/urukku_manush.apk";
            }

            String cleanRemote = tagName.replaceAll("[^0-9.]", "");
            String cleanCur = getCurrentVersion().replaceAll("[^0-9.]", "");
            boolean isUpdate = isVersionGreater(cleanRemote, cleanCur);

            return new ReleaseInfo(tagName, releaseTitle, changelog, downloadUrl, htmlUrl, sizeBytes, isUpdate);
        }
    }

    private ReleaseInfo tryFetchRawVersionJson() throws Exception {
        Request request = new Request.Builder()
                .url(GITHUB_RAW_VERSION)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android)")
                .build();

        try (Response response = HttpClientProvider.get().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new java.io.IOException("HTTP " + response.code() + " " + response.message());
            }
            if (response.body() == null) {
                throw new java.io.IOException("Empty response body from Raw version.json");
            }
            String body = response.body().string();
            JSONObject json = new JSONObject(body);
            String tagName = json.optString("version", "v2.0.0");
            String releaseTitle = json.optString("name", "Urukku Manush " + tagName);
            String changelog = json.optString("changelog", "• Latest game features and optimizations.");
            String downloadUrl = json.optString("downloadUrl", "https://github.com/exotic-atif/urukku-manush/releases/latest/download/urukku_manush.apk");

            String cleanRemote = tagName.replaceAll("[^0-9.]", "");
            String cleanCur = getCurrentVersion().replaceAll("[^0-9.]", "");
            boolean isUpdate = isVersionGreater(cleanRemote, cleanCur);

            return new ReleaseInfo(tagName, releaseTitle, changelog, downloadUrl, GITHUB_RELEASES_WEB, 25000000L, isUpdate);
        }
    }

    private boolean isVersionGreater(String remote, String current) {
        String[] rParts = remote.split("\\.");
        String[] cParts = current.split("\\.");
        int len = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < len; i++) {
            int r = (i < rParts.length) ? parseSafeInt(rParts[i]) : 0;
            int c = (i < cParts.length) ? parseSafeInt(cParts[i]) : 0;
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    private int parseSafeInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public void pauseDownload() {
        isPaused = true;
        if (activeCall != null) {
            activeCall.cancel();
        }
    }

    public void resumeDownload() {
        if (currentReleaseInfo != null && activeCallback != null) {
            downloadUpdate(currentReleaseInfo, activeCallback);
        }
    }

    public void cancelDownload() {
        isCancelled = true;
        isPaused = false;
        if (activeCall != null) {
            activeCall.cancel();
        }
        executor.execute(() -> {
            try {
                File dir = context.getExternalFilesDir("updates");
                if (dir != null && dir.exists()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.getName().endsWith(".part") || f.getName().endsWith(".apk")) {
                                f.delete();
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void downloadUpdate(String targetUrl, DownloadProgressCallback callback) {
        ReleaseInfo info = (currentReleaseInfo != null) ? currentReleaseInfo :
                new ReleaseInfo("v_latest", "Urukku Manush Update", "", targetUrl, GITHUB_RELEASES_WEB, 0, true);
        downloadUpdate(info, callback);
    }

    public void downloadUpdate(ReleaseInfo releaseInfo, DownloadProgressCallback callback) {
        this.currentReleaseInfo = releaseInfo;
        this.activeCallback = callback;
        this.isCancelled = false;
        this.isPaused = false;

        executor.execute(() -> {
            try {
                // If the APK for this release is already fully downloaded, finish immediately
                File alreadyDownloaded = getDownloadedApkFile(releaseInfo);
                if (alreadyDownloaded != null) {
                    mainHandler.post(() -> callback.onComplete(alreadyDownloaded));
                    return;
                }

                String currentUrl = (releaseInfo != null && releaseInfo.downloadUrl != null && !releaseInfo.downloadUrl.isEmpty())
                        ? releaseInfo.downloadUrl
                        : "https://github.com/exotic-atif/urukku-manush/releases/latest/download/urukku_manush.apk";

                String versionTag = (releaseInfo != null && releaseInfo.tagName != null) ? releaseInfo.tagName.trim() : "v_latest";
                File updateDir = context.getExternalFilesDir("updates");
                if (updateDir != null && !updateDir.exists()) {
                    updateDir.mkdirs();
                }

                File destination = new File(updateDir, "urukku_manush_" + versionTag + ".apk");
                File tempPart = new File(updateDir, "urukku_manush_" + versionTag + ".part");

                long existingBytes = tempPart.exists() ? tempPart.length() : 0;

                Request.Builder reqBuilder = new Request.Builder()
                        .url(currentUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android)");

                if (existingBytes > 0) {
                    reqBuilder.header("Range", "bytes=" + existingBytes + "-");
                }

                Request request = reqBuilder.build();
                activeCall = HttpClientProvider.get().newCall(request);
                Response response = activeCall.execute();

                if (isCancelled) {
                    if (tempPart.exists()) tempPart.delete();
                    return;
                }

                int code = response.code();
                boolean isPartial = (code == 206);
                if (!response.isSuccessful() && !isPartial) {
                    mainHandler.post(() -> callback.onError("Server returned HTTP " + code + " (" + response.message() + ")"));
                    return;
                }

                long contentLength = (response.body() != null) ? response.body().contentLength() : 0;
                long totalLength = isPartial ? (existingBytes + contentLength) : contentLength;
                if (totalLength <= 0 && releaseInfo != null) {
                    totalLength = releaseInfo.fileSizeBytes;
                }

                // If server didn't honor Range and returned 200 OK, reset downloaded count
                boolean append = isPartial && existingBytes > 0;
                long downloadedSoFar = append ? existingBytes : 0;

                try (InputStream input = response.body().byteStream();
                     FileOutputStream output = new FileOutputStream(tempPart, append)) {
                    byte[] data = new byte[8192];
                    int count;
                    long startTime = System.currentTimeMillis();
                    long lastProgressTime = startTime;
                    int lastPercent = -1;

                    while ((count = input.read(data)) != -1) {
                        if (isCancelled) {
                            if (tempPart.exists()) tempPart.delete();
                            return;
                        }
                        if (isPaused) {
                            output.flush();
                            return;
                        }

                        output.write(data, 0, count);
                        downloadedSoFar += count;

                        long now = System.currentTimeMillis();
                        if (now - lastProgressTime >= 100 || downloadedSoFar == totalLength) {
                            lastProgressTime = now;
                            long elapsed = Math.max(1, now - startTime);
                            float speedMBs = ((downloadedSoFar - (append ? existingBytes : 0)) / 1024f / 1024f) / (elapsed / 1000f);

                            int percent = (totalLength > 0) ? (int) (downloadedSoFar * 100 / totalLength) : 0;
                            if (percent != lastPercent || downloadedSoFar == totalLength) {
                                lastPercent = percent;
                                final int finalPercent = percent;
                                final long finalDownloaded = downloadedSoFar;
                                final long finalTotal = totalLength;
                                final float finalSpeed = speedMBs;
                                mainHandler.post(() -> callback.onProgress(finalPercent, finalDownloaded, finalTotal, finalSpeed));
                            }
                        }
                    }
                    output.flush();
                }

                if (isPaused) return;
                if (isCancelled) {
                    if (tempPart.exists()) tempPart.delete();
                    return;
                }

                if (destination.exists()) destination.delete();
                tempPart.renameTo(destination);

                // Save downloaded version info in preferences
                prefs.edit()
                        .putString(KEY_DOWNLOADED_VERSION, versionTag)
                        .putString(KEY_DOWNLOADED_PATH, destination.getAbsolutePath())
                        .putLong(KEY_DOWNLOADED_SIZE, destination.length())
                        .apply();

                mainHandler.post(() -> callback.onComplete(destination));
            } catch (Exception e) {
                if (isPaused || isCancelled) {
                    return;
                }
                Log.e(TAG, "Error downloading update APK", e);
                mainHandler.post(() -> callback.onError(e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "Unknown error")));
            }
        });
    }

    public void installApk(Activity activity, File apkFile) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(permIntent);
                    return;
                }
            }

            Uri apkUri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", apkFile);

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(installIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed launching APK installer", e);
        }
    }
}
