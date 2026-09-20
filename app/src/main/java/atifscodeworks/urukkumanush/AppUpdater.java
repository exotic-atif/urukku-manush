package atifscodeworks.urukkumanush;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppUpdater {
    private static final String TAG = "AppUpdater";

    public static final String GITHUB_REPO_API = "https://api.github.com/repos/exotic-atif/urukku-manush/releases/latest";
    public static final String GITHUB_RELEASES_WEB = "https://github.com/exotic-atif/urukku-manush/releases";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

    public void cleanOldUpdates() {
        executor.execute(() -> {
            try {
                File dir = context.getExternalFilesDir("updates");
                if (dir != null && dir.exists()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.getName().endsWith(".apk")) {
                                f.delete();
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    public void checkForUpdates(CheckCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(GITHUB_REPO_API);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "UrukkuManush-Updater");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
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

                    final ReleaseInfo info = new ReleaseInfo(tagName, releaseTitle, changelog, downloadUrl, htmlUrl, sizeBytes, isUpdate);
                    mainHandler.post(() -> callback.onSuccess(info));
                    return;
                }

                mainHandler.post(() -> callback.onError("GitHub release check returned status: " + code));
            } catch (java.net.UnknownHostException e) {
                Log.w(TAG, "Network host unreachable: " + e.getMessage());
                mainHandler.post(() -> callback.onError("No internet connection. Please turn on Mobile Data or connect to working Wi-Fi and try again."));
            } catch (Exception e) {
                Log.e(TAG, "Error checking updates from GitHub", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Network connection failed. Please check internet."));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
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

    public void downloadUpdate(String targetUrl, DownloadProgressCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            InputStream input = null;
            FileOutputStream output = null;
            try {
                String currentUrl = (targetUrl != null && !targetUrl.isEmpty())
                        ? targetUrl
                        : "https://github.com/exotic-atif/urukku-manush/releases/latest/download/urukku_manush.apk";

                // Follow redirects manually across domains (GitHub -> AWS S3)
                int redirects = 0;
                while (redirects < 6) {
                    URL u = new URL(currentUrl);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "UrukkuManush-Updater");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(20000);

                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                            || code == 307 || code == 308) {
                        String loc = conn.getHeaderField("Location");
                        if (loc != null) {
                            currentUrl = loc;
                            conn.disconnect();
                            redirects++;
                            continue;
                        }
                    }
                    break;
                }

                int respCode = conn.getResponseCode();
                if (respCode != HttpURLConnection.HTTP_OK) {
                    final int errCode = respCode;
                    mainHandler.post(() -> callback.onError("Download server returned code: " + errCode));
                    return;
                }

                long fileLength = conn.getContentLengthLong();
                File updateDir = context.getExternalFilesDir("updates");
                if (updateDir != null && !updateDir.exists()) {
                    updateDir.mkdirs();
                }

                File destination = new File(updateDir, "urukku_manush.apk");
                if (destination.exists()) destination.delete();

                input = conn.getInputStream();
                output = new FileOutputStream(destination);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                long startTime = System.currentTimeMillis();
                long lastProgressTime = startTime;
                int lastPercent = -1;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);

                    long now = System.currentTimeMillis();
                    if (now - lastProgressTime >= 100 || total == fileLength) {
                        lastProgressTime = now;
                        long elapsed = Math.max(1, now - startTime);
                        float speedMBs = (total / 1024f / 1024f) / (elapsed / 1000f);

                        int percent = (fileLength > 0) ? (int) (total * 100 / fileLength) : 0;
                        if (percent != lastPercent || total == fileLength) {
                            lastPercent = percent;
                            final long finalTotal = total;
                            final long finalLength = fileLength;
                            final float finalSpeed = speedMBs;
                            final int finalPercent = percent;
                            mainHandler.post(() -> callback.onProgress(finalPercent, finalTotal, finalLength, finalSpeed));
                        }
                    }
                }
                output.flush();

                mainHandler.post(() -> callback.onComplete(destination));
            } catch (Exception e) {
                Log.e(TAG, "Error downloading update APK", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Download encountered an error"));
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                    if (conn != null) conn.disconnect();
                } catch (Exception ignored) {
                }
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
