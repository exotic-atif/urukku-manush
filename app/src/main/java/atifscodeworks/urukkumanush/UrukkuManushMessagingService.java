package atifscodeworks.urukkumanush;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class UrukkuManushMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";
    public static final String CHANNEL_ID = "mr_jumper_rivals";
    public static final String NEW_CHANNEL_ID = "urukku_manush_rivals";
    private static final String PREFS_NAME = "urukku_manush_prefs";
    public static final String KEY_FCM_TOKEN = "fcm_device_token";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.i(TAG, "New FCM Device Token received: " + token);

        // Store locally
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply();

        // If player is already activated, sync new token with Supabase immediately
        ActivationManager actMgr = new ActivationManager(this);
        if (actMgr.isActivated()) {
            LeaderboardManager lbMgr = new LeaderboardManager(this);
            lbMgr.registerDeviceToken(token, actMgr.getActiveHash());
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.i(TAG, "Push message received from: " + remoteMessage.getFrom());

        String title = "Score Beaten! 😱";
        String body = "Someone just beat your score! Jump back in and reclaim your spot!";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        } else if (!remoteMessage.getData().isEmpty()) {
            if (remoteMessage.getData().containsKey("title")) {
                title = remoteMessage.getData().get("title");
            }
            if (remoteMessage.getData().containsKey("body")) {
                body = remoteMessage.getData().get("body");
            }
        }

        showNotification(title, body);
    }

    private void showNotification(String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        createNotificationChannel(nm);

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.app_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setColor(Color.rgb(0, 229, 255))
                .setVibrate(new long[]{0, 250, 150, 250})
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent);

        nm.notify((int) System.currentTimeMillis(), builder.build());
    }

    public static void createNotificationChannel(NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long[] vibPattern = new long[]{0, 250, 150, 250};

            // Existing channel from v2.2.0 (preserves user settings and avoids OEM silence)
            NotificationChannel legacyChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Urukku Manush Rivalry Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            legacyChannel.setDescription("Alerts you when a rival player beats your high score on the leaderboard");
            legacyChannel.enableLights(true);
            legacyChannel.setLightColor(Color.rgb(0, 229, 255));
            legacyChannel.enableVibration(true);
            legacyChannel.setVibrationPattern(vibPattern);
            legacyChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(legacyChannel);

            // New channel
            NotificationChannel newChannel = new NotificationChannel(
                    NEW_CHANNEL_ID,
                    "Urukku Manush Rivalry Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            newChannel.setDescription("Alerts you with sound and popup banners when a rival beats your score");
            newChannel.enableLights(true);
            newChannel.setLightColor(Color.rgb(0, 229, 255));
            newChannel.enableVibration(true);
            newChannel.setVibrationPattern(vibPattern);
            newChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(newChannel);
        }
    }
}
