package atifscodeworks.urukkumanush;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity implements GameActionListener {
    private static final String TAG = "MainActivity";

    private GameView gameView;
    private LeaderboardManager leaderboardManager;
    private AppUpdater appUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        leaderboardManager = new LeaderboardManager(this);
        appUpdater = new AppUpdater(this);

        // Ensure external storage folder sdcard/Android/data/atifscodeworks.urukkumanush/files is created
        ensureExternalDataDirectory();

        // Configure dynamic high refresh rate (60, 90, 120, 144Hz+)
        enableHighRefreshRate();

        // Hide system bars and enable immersive fullscreen
        setupImmersiveFullscreen();

        gameView = new GameView(this);
        gameView.setActionListener(this);
        setContentView(gameView);

        // Background multi-device highscore sync on launch if already activated
        ActivationManager actMgr = new ActivationManager(this);
        ScoreManager scoreMgr = new ScoreManager(this);
        if (actMgr.isActivated()) {
            leaderboardManager.syncScoresWithServer(
                    scoreMgr.getHighScore(),
                    actMgr.getActiveHash(),
                    actMgr.getActiveHeadIndex(),
                    actMgr.getActiveHead(),
                    syncedScore -> {
                        if (syncedScore > scoreMgr.getHighScore()) {
                            scoreMgr.setHighScore(syncedScore);
                        }
                    }
            );
        }
    }

    @Override
    public void onNewHighScore(int score) {
        ActivationManager actMgr = new ActivationManager(this);
        leaderboardManager.autoSyncHighScore(score, actMgr.getActiveHash(), actMgr.getActiveHeadIndex(), actMgr.getActiveHead());
    }

    private void setButtonVectorIcon(Button btn, int resId, int color) {
        try {
            Drawable icon = ContextCompat.getDrawable(this, resId);
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(color);
                int size = (int) (20 * getResources().getDisplayMetrics().density);
                icon.setBounds(0, 0, size, size);
                btn.setCompoundDrawables(icon, null, null, null);
                btn.setCompoundDrawablePadding((int) (10 * getResources().getDisplayMetrics().density));
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureExternalDataDirectory() {
        try {
            File extDir = getExternalFilesDir(null);
            if (extDir != null && !extDir.exists()) {
                extDir.mkdirs();
            }
            File cacheDir = getExternalCacheDir();
            if (cacheDir != null && !cacheDir.exists()) {
                cacheDir.mkdirs();
            }
        } catch (Exception ignored) {
        }
    }

    private void enableHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Display display;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display = getDisplay();
                } else {
                    display = getWindowManager().getDefaultDisplay();
                }

                if (display != null) {
                    Display.Mode[] modes = display.getSupportedModes();
                    Display.Mode maxMode = null;
                    float maxRate = 60.0f;
                    for (Display.Mode mode : modes) {
                        if (mode.getRefreshRate() > maxRate) {
                            maxRate = mode.getRefreshRate();
                            maxMode = mode;
                        }
                    }

                    if (maxMode != null) {
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        lp.preferredDisplayModeId = maxMode.getModeId();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            lp.preferredRefreshRate = maxRate;
                        }
                        getWindow().setAttributes(lp);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void setupImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupImmersiveFullscreen();
            if (gameView != null && gameView.getAudioManager() != null) {
                gameView.getAudioManager().resumeAll(gameView.getGameState() == GameView.State.PLAYING);
            }
        } else {
            // Notification shade pulled down, incoming call, or app minimized
            if (gameView != null) {
                gameView.pauseGame();
                if (gameView.getAudioManager() != null) {
                    gameView.getAudioManager().pauseAll();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) {
            gameView.pauseGame();
            if (gameView.getAudioManager() != null) {
                gameView.getAudioManager().pauseAll();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupImmersiveFullscreen();
        enableHighRefreshRate();
        if (gameView != null && gameView.getAudioManager() != null) {
            gameView.getAudioManager().resumeAll(gameView.getGameState() == GameView.State.PLAYING);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            if (gameView != null && gameView.getActivationManager() != null) {
                gameView.getActivationManager().applyPendingLauncherIconUpdate();
            } else {
                new ActivationManager(this).applyPendingLauncherIconUpdate();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying pending launcher icon update on stop", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (gameView != null && gameView.getActivationManager() != null) {
                gameView.getActivationManager().applyPendingLauncherIconUpdate();
            }
        } catch (Exception ignored) {
        }
        if (gameView != null && gameView.getAudioManager() != null) {
            gameView.getAudioManager().release();
        }
    }

    private Typeface getGameFont() {
        try {
            return Typeface.createFromAsset(getAssets(), "fonts/DotGothic16-Regular.ttf");
        } catch (Exception e) {
            return Typeface.DEFAULT_BOLD;
        }
    }

    @Override
    public void onShowActivationDialog(Runnable onActivated) {
        runOnUiThread(() -> {
            ActivationManager actMgr = gameView.getActivationManager();
            AudioManager audioMgr = gameView.getAudioManager();
            Typeface font = getGameFont();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            ScrollView scrollView = new ScrollView(this);
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(60, 40, 60, 40);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(24, 28, 42));
            bg.setCornerRadius(24f);
            bg.setStroke(3, Color.rgb(0, 229, 255));
            layout.setBackground(bg);

            TextView title = new TextView(this);
            title.setText("🔑 Activation Required");
            title.setTextSize(22);
            title.setTypeface(font);
            title.setTextColor(Color.rgb(255, 215, 0));
            title.setGravity(Gravity.CENTER);
            layout.addView(title);

            TextView desc = new TextView(this);
            desc.setText("Enter your activation code to unlock and play Urukku Manush!");
            desc.setTextSize(14);
            desc.setTypeface(font);
            desc.setTextColor(Color.rgb(210, 220, 240));
            desc.setPadding(0, 16, 0, 24);
            desc.setGravity(Gravity.CENTER);
            layout.addView(desc);

            EditText input = new EditText(this);
            input.setHint("Enter activation code");
            input.setHintTextColor(Color.rgb(120, 130, 150));
            input.setTextColor(Color.WHITE);
            input.setTextSize(16);
            input.setTypeface(font);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setColor(Color.rgb(36, 42, 62));
            inputBg.setCornerRadius(12f);
            inputBg.setStroke(2, Color.rgb(70, 80, 110));
            input.setBackground(inputBg);
            input.setPadding(30, 24, 30, 24);
            layout.addView(input);

            TextView errorText = new TextView(this);
            errorText.setTextSize(13);
            errorText.setTypeface(font);
            errorText.setTextColor(Color.rgb(255, 80, 80));
            errorText.setPadding(0, 12, 0, 12);
            errorText.setVisibility(View.GONE);
            layout.addView(errorText);

            Button unlockBtn = new Button(this);
            unlockBtn.setText("UNLOCK & PLAY");
            unlockBtn.setTextColor(Color.WHITE);
            unlockBtn.setTextSize(16);
            unlockBtn.setTypeface(font);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(Color.rgb(46, 204, 113));
            btnBg.setCornerRadius(14f);
            unlockBtn.setBackground(btnBg);
            unlockBtn.setPadding(0, 20, 0, 20);
            LinearLayout.LayoutParams lpUnlock = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpUnlock.setMargins(0, 16, 0, 0);
            unlockBtn.setLayoutParams(lpUnlock);
            layout.addView(unlockBtn);

            scrollView.addView(layout);
            builder.setView(scrollView);

            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            dialog.show();

            unlockBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                String code = input.getText().toString().trim();
                if (actMgr.activate(code)) {
                    Toast.makeText(this, "Activated! Welcome to Urukku Manush!", Toast.LENGTH_SHORT).show();
                    gameView.reloadPlayerHead();
                    dialog.dismiss();

                    // Multi-device sync on activation: local < server = pull, local > server = push
                    ScoreManager scoreMgr = new ScoreManager(this);
                    leaderboardManager.syncScoresWithServer(
                            scoreMgr.getHighScore(),
                            actMgr.getActiveHash(),
                            actMgr.getActiveHeadIndex(),
                            actMgr.getActiveHead(),
                            syncedScore -> {
                                if (syncedScore > scoreMgr.getHighScore()) {
                                    scoreMgr.setHighScore(syncedScore);
                                    Toast.makeText(this, "Restored high score: " + syncedScore, Toast.LENGTH_SHORT).show();
                                }
                            }
                    );

                    if (onActivated != null) {
                        onActivated.run();
                    }
                } else {
                    errorText.setText("Invalid activation code! Try again.");
                    errorText.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    public void onShowOptionsDialog() {
        runOnUiThread(() -> {
            AudioManager audioMgr = gameView.getAudioManager();
            Typeface font = getGameFont();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            ScrollView scrollView = new ScrollView(this);
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(60, 40, 60, 40);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(24, 28, 42));
            bg.setCornerRadius(24f);
            bg.setStroke(3, Color.rgb(52, 152, 219));
            layout.setBackground(bg);

            TextView title = new TextView(this);
            title.setText("Game Settings");
            title.setTextSize(22);
            title.setTypeface(font);
            title.setTextColor(Color.rgb(0, 229, 255));
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 20);
            layout.addView(title);

            // Version label
            TextView verLabel = new TextView(this);
            verLabel.setText("Urukku Manush v" + appUpdater.getCurrentVersion());
            verLabel.setTextSize(13);
            verLabel.setTypeface(font);
            verLabel.setTextColor(Color.rgb(180, 195, 220));
            verLabel.setGravity(Gravity.CENTER);
            verLabel.setPadding(0, 0, 0, 16);
            layout.addView(verLabel);

            // Mute SFX checkbox
            CheckBox cbMuteSfx = new CheckBox(this);
            cbMuteSfx.setText(" Mute SFX (Sound Effects)");
            cbMuteSfx.setTextColor(Color.WHITE);
            cbMuteSfx.setTypeface(font);
            cbMuteSfx.setTextSize(15);
            cbMuteSfx.setChecked(!audioMgr.isSfxEnabled());
            cbMuteSfx.setOnCheckedChangeListener((b, isMuted) -> {
                audioMgr.playClickSound();
                audioMgr.setSfxEnabled(!isMuted);
            });
            layout.addView(cbMuteSfx);

            // Mute BGM checkbox
            CheckBox cbMuteBgm = new CheckBox(this);
            cbMuteBgm.setText(" Mute BGM (Background Music)");
            cbMuteBgm.setTextColor(Color.WHITE);
            cbMuteBgm.setTypeface(font);
            cbMuteBgm.setTextSize(15);
            cbMuteBgm.setChecked(!audioMgr.isBgmEnabled());
            cbMuteBgm.setOnCheckedChangeListener((b, isMuted) -> {
                audioMgr.playClickSound();
                audioMgr.setBgmEnabled(!isMuted);
            });
            layout.addView(cbMuteBgm);

            // Leaderboard button with real SVG trophy icon
            Button leaderboardBtn = new Button(this);
            leaderboardBtn.setText("LEADERBOARD");
            leaderboardBtn.setTextColor(Color.WHITE);
            leaderboardBtn.setTypeface(font);
            setButtonVectorIcon(leaderboardBtn, R.drawable.ic_trophy, Color.WHITE);
            GradientDrawable btnLeadBg = new GradientDrawable();
            btnLeadBg.setColor(Color.rgb(243, 156, 18));
            btnLeadBg.setCornerRadius(12f);
            leaderboardBtn.setBackground(btnLeadBg);
            LinearLayout.LayoutParams lpLead = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpLead.setMargins(0, 20, 0, 10);
            leaderboardBtn.setLayoutParams(lpLead);
            layout.addView(leaderboardBtn);

            // In-App Check Updates button with real SVG refresh icon
            Button updateBtn = new Button(this);
            updateBtn.setText("CHECK FOR UPDATES");
            updateBtn.setTextColor(Color.WHITE);
            updateBtn.setTypeface(font);
            setButtonVectorIcon(updateBtn, R.drawable.ic_refresh, Color.WHITE);
            GradientDrawable btnUpBg = new GradientDrawable();
            btnUpBg.setColor(Color.rgb(46, 204, 113));
            btnUpBg.setCornerRadius(12f);
            updateBtn.setBackground(btnUpBg);
            LinearLayout.LayoutParams lpUp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpUp.setMargins(0, 0, 0, 14);
            updateBtn.setLayoutParams(lpUp);
            layout.addView(updateBtn);

            // Close button
            Button closeBtn = new Button(this);
            closeBtn.setText("CLOSE");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTypeface(font);
            GradientDrawable btnCloseBg = new GradientDrawable();
            btnCloseBg.setColor(Color.rgb(60, 70, 90));
            btnCloseBg.setCornerRadius(12f);
            closeBtn.setBackground(btnCloseBg);
            layout.addView(closeBtn);

            scrollView.addView(layout);
            builder.setView(scrollView);

            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            dialog.show();

            leaderboardBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
                onShowLeaderboardDialog();
            });

            updateBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
                startUpdateCheck();
            });

            closeBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
            });
        });
    }

    private void startUpdateCheck() {
        Typeface font = getGameFont();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(20, 24, 38));
        bg.setCornerRadius(24f);
        bg.setStroke(3, Color.rgb(0, 229, 255));
        layout.setBackground(bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Update Wizard");
        tvTitle.setTextSize(19);
        tvTitle.setTypeface(font);
        tvTitle.setTextColor(Color.rgb(0, 229, 255));
        tvTitle.setGravity(Gravity.CENTER);
        layout.addView(tvTitle);

        TextView tvStatus = new TextView(this);
        tvStatus.setText("Checking GitHub for the latest release...");
        tvStatus.setTextSize(14);
        tvStatus.setTypeface(font);
        tvStatus.setTextColor(Color.WHITE);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 20, 0, 20);
        layout.addView(tvStatus);

        ProgressBar spinner = new ProgressBar(this);
        layout.addView(spinner);

        builder.setView(layout);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();

        appUpdater.checkForUpdates(new AppUpdater.CheckCallback() {
            @Override
            public void onSuccess(AppUpdater.ReleaseInfo releaseInfo) {
                dialog.dismiss();
                showUpdateCenterDialog(releaseInfo);
            }

            @Override
            public void onError(String error) {
                spinner.setVisibility(View.GONE);
                tvStatus.setText("Could not reach GitHub Releases.\n" + error);
                tvStatus.setTextColor(Color.rgb(255, 100, 100));

                LinearLayout btnRow = new LinearLayout(MainActivity.this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setPadding(0, 16, 0, 0);

                Button retryBtn = new Button(MainActivity.this);
                retryBtn.setText("RETRY");
                retryBtn.setTypeface(font);
                retryBtn.setTextColor(Color.WHITE);
                setButtonVectorIcon(retryBtn, R.drawable.ic_refresh, Color.WHITE);
                GradientDrawable retBg = new GradientDrawable();
                retBg.setColor(Color.rgb(46, 204, 113));
                retBg.setCornerRadius(12f);
                retryBtn.setBackground(retBg);
                LinearLayout.LayoutParams lpRet = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lpRet.setMargins(0, 0, 6, 0);
                retryBtn.setLayoutParams(lpRet);
                retryBtn.setOnClickListener(v -> {
                    dialog.dismiss();
                    startUpdateCheck();
                });
                btnRow.addView(retryBtn);

                Button webBtn = new Button(MainActivity.this);
                webBtn.setText("BROWSER");
                webBtn.setTypeface(font);
                webBtn.setTextColor(Color.WHITE);
                setButtonVectorIcon(webBtn, R.drawable.ic_globe, Color.WHITE);
                GradientDrawable wBg = new GradientDrawable();
                wBg.setColor(Color.rgb(52, 152, 219));
                wBg.setCornerRadius(12f);
                webBtn.setBackground(wBg);
                LinearLayout.LayoutParams lpW = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lpW.setMargins(6, 0, 6, 0);
                webBtn.setLayoutParams(lpW);
                webBtn.setOnClickListener(v -> {
                    openUrlSafely(AppUpdater.GITHUB_RELEASES_WEB);
                });
                btnRow.addView(webBtn);

                Button closeBtn = new Button(MainActivity.this);
                closeBtn.setText("CLOSE");
                closeBtn.setTypeface(font);
                closeBtn.setTextColor(Color.WHITE);
                GradientDrawable bBg = new GradientDrawable();
                bBg.setColor(Color.rgb(60, 70, 90));
                bBg.setCornerRadius(12f);
                closeBtn.setBackground(bBg);
                LinearLayout.LayoutParams lpCls = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lpCls.setMargins(6, 0, 0, 0);
                closeBtn.setLayoutParams(lpCls);
                closeBtn.setOnClickListener(v -> dialog.dismiss());
                btnRow.addView(closeBtn);

                layout.addView(btnRow);
            }
        });
    }

    private void showUpdateCenterDialog(AppUpdater.ReleaseInfo info) {
        Typeface font = getGameFont();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 30);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(18, 22, 34));
        bg.setCornerRadius(24f);
        bg.setStroke(3, info.isUpdateAvailable ? Color.rgb(46, 204, 113) : Color.rgb(0, 229, 255));
        layout.setBackground(bg);

        // Header Title
        TextView title = new TextView(this);
        title.setText("Update Wizard");
        title.setTextSize(20);
        title.setTypeface(font);
        title.setTextColor(Color.rgb(255, 215, 0));
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        // Status Badge
        TextView badge = new TextView(this);
        badge.setText(info.isUpdateAvailable ? "⚡ NEW VERSION READY TO INSTALL" : "✅ YOU ARE UP TO DATE");
        badge.setTextSize(13);
        badge.setTypeface(font);
        badge.setTextColor(info.isUpdateAvailable ? Color.rgb(46, 204, 113) : Color.rgb(0, 229, 255));
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(0, 8, 0, 14);
        layout.addView(badge);

        // Info Card
        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(20, 16, 20, 16);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.rgb(28, 33, 50));
        cardBg.setCornerRadius(14f);
        infoCard.setBackground(cardBg);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, 0, 0, 16);
        infoCard.setLayoutParams(lpCard);

        TextView relName = new TextView(this);
        relName.setText(info.releaseTitle);
        relName.setTextSize(15);
        relName.setTypeface(font);
        relName.setTextColor(Color.WHITE);
        infoCard.addView(relName);

        TextView verCompare = new TextView(this);
        verCompare.setText("Installed: v" + appUpdater.getCurrentVersion() + "   ➔   Available: " + info.tagName);
        verCompare.setTextSize(13);
        verCompare.setTypeface(font);
        verCompare.setTextColor(Color.rgb(0, 229, 255));
        verCompare.setPadding(0, 6, 0, 4);
        infoCard.addView(verCompare);

        TextView pkgSize = new TextView(this);
        pkgSize.setText("Package Size: " + info.getFormattedSize());
        pkgSize.setTextSize(12);
        pkgSize.setTypeface(font);
        pkgSize.setTextColor(Color.rgb(180, 195, 220));
        infoCard.addView(pkgSize);

        layout.addView(infoCard);

        // Changelog Section Header
        TextView notesHeader = new TextView(this);
        notesHeader.setText("📋 PATCH NOTES & DETAILS");
        notesHeader.setTextSize(14);
        notesHeader.setTypeface(font);
        notesHeader.setTextColor(Color.rgb(255, 215, 0));
        notesHeader.setPadding(0, 0, 0, 8);
        layout.addView(notesHeader);

        // Changelog text box
        ScrollView notesScroll = new ScrollView(this);
        notesScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 260));
        GradientDrawable notesBg = new GradientDrawable();
        notesBg.setColor(Color.rgb(24, 28, 42));
        notesBg.setCornerRadius(10f);
        notesScroll.setBackground(notesBg);
        notesScroll.setPadding(16, 12, 16, 12);

        TextView notesText = new TextView(this);
        notesText.setText(info.changelog);
        notesText.setTextSize(13);
        notesText.setTypeface(font);
        notesText.setTextColor(Color.rgb(215, 225, 245));
        notesScroll.addView(notesText);
        layout.addView(notesScroll);

        // Live Progress Container (hidden initially)
        LinearLayout progressContainer = new LinearLayout(this);
        progressContainer.setOrientation(LinearLayout.VERTICAL);
        progressContainer.setPadding(0, 16, 0, 0);
        progressContainer.setVisibility(View.GONE);

        TextView progressLabel = new TextView(this);
        progressLabel.setText("Downloading update package...");
        progressLabel.setTextSize(13);
        progressLabel.setTypeface(font);
        progressLabel.setTextColor(Color.rgb(0, 229, 255));
        progressContainer.addView(progressLabel);

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams lpProgress = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 26);
        lpProgress.setMargins(0, 10, 0, 6);
        progressBar.setLayoutParams(lpProgress);
        progressContainer.addView(progressBar);

        TextView progressStats = new TextView(this);
        progressStats.setText("0% • 0 MB / " + info.getFormattedSize());
        progressStats.setTextSize(12);
        progressStats.setTypeface(font);
        progressStats.setTextColor(Color.rgb(180, 195, 220));
        progressContainer.addView(progressStats);

        layout.addView(progressContainer);

        // Action Buttons Row
        Button actionBtn = new Button(this);
        actionBtn.setText(info.isUpdateAvailable ? "DOWNLOAD & INSTALL" : "RE-INSTALL LATEST (v" + info.tagName + ")");
        actionBtn.setTextColor(Color.WHITE);
        actionBtn.setTypeface(font);
        GradientDrawable actBg = new GradientDrawable();
        actBg.setColor(Color.rgb(46, 204, 113));
        actBg.setCornerRadius(12f);
        actionBtn.setBackground(actBg);
        LinearLayout.LayoutParams lpAct = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpAct.setMargins(0, 20, 0, 8);
        actionBtn.setLayoutParams(lpAct);
        layout.addView(actionBtn);

        LinearLayout rowButtons = new LinearLayout(this);
        rowButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button webBtn = new Button(this);
        webBtn.setText("🌐 VIEW ON GITHUB");
        webBtn.setTextColor(Color.WHITE);
        webBtn.setTypeface(font);
        GradientDrawable webBg = new GradientDrawable();
        webBg.setColor(Color.rgb(36, 41, 46));
        webBg.setCornerRadius(12f);
        webBtn.setBackground(webBg);
        LinearLayout.LayoutParams lpWeb = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lpWeb.setMargins(0, 0, 6, 0);
        webBtn.setLayoutParams(lpWeb);
        rowButtons.addView(webBtn);

        Button closeBtn = new Button(this);
        closeBtn.setText("CLOSE");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTypeface(font);
        GradientDrawable clsBg = new GradientDrawable();
        clsBg.setColor(Color.rgb(60, 70, 90));
        clsBg.setCornerRadius(12f);
        closeBtn.setBackground(clsBg);
        LinearLayout.LayoutParams lpCls = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lpCls.setMargins(6, 0, 0, 0);
        closeBtn.setLayoutParams(lpCls);
        rowButtons.addView(closeBtn);

        layout.addView(rowButtons);

        scrollView.addView(layout);
        builder.setView(scrollView);

        AlertDialog updateDialog = builder.create();
        if (updateDialog.getWindow() != null) {
            updateDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        updateDialog.show();

        final File[] downloadedApk = new File[1];

        actionBtn.setOnClickListener(v -> {
            if (downloadedApk[0] != null && downloadedApk[0].exists()) {
                appUpdater.installApk(MainActivity.this, downloadedApk[0]);
                return;
            }

            actionBtn.setEnabled(false);
            actionBtn.setText("DOWNLOADING...");
            progressContainer.setVisibility(View.VISIBLE);

            appUpdater.downloadUpdate(info.downloadUrl, new AppUpdater.DownloadProgressCallback() {
                @Override
                public void onProgress(int progressPercent, long downloadedBytes, long totalBytes, float speedMBs) {
                    progressBar.setProgress(progressPercent);
                    double curMB = downloadedBytes / (1024.0 * 1024.0);
                    double totMB = totalBytes / (1024.0 * 1024.0);
                    String statsStr = String.format(java.util.Locale.US,
                            "%d%% • %.1f MB / %.1f MB • %.1f MB/s",
                            progressPercent, curMB, totMB, speedMBs);
                    progressStats.setText(statsStr);
                }

                @Override
                public void onComplete(File apkFile) {
                    downloadedApk[0] = apkFile;
                    actionBtn.setEnabled(true);
                    actionBtn.setText("PACKAGE READY - INSTALL NOW");
                    GradientDrawable instBg = new GradientDrawable();
                    instBg.setColor(Color.rgb(46, 204, 113));
                    instBg.setCornerRadius(12f);
                    actionBtn.setBackground(instBg);

                    progressLabel.setText("✅ Download verified and complete!");
                    progressLabel.setTextColor(Color.rgb(46, 204, 113));
                    progressBar.setProgress(100);

                    Toast.makeText(MainActivity.this, "Download complete! Opening Android installer...", Toast.LENGTH_SHORT).show();
                    appUpdater.installApk(MainActivity.this, apkFile);
                }

                @Override
                public void onError(String error) {
                    actionBtn.setEnabled(true);
                    actionBtn.setText("RETRY DOWNLOAD");
                    progressLabel.setText("❌ " + error);
                    progressLabel.setTextColor(Color.rgb(255, 80, 80));
                    Toast.makeText(MainActivity.this, "Download error: " + error, Toast.LENGTH_LONG).show();
                }
            });
        });

        webBtn.setOnClickListener(v -> {
            openUrlSafely(info.htmlUrl);
        });

        closeBtn.setOnClickListener(v -> updateDialog.dismiss());
    }

    public void openUrlSafely(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent chooser = Intent.createChooser(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Open with");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not open browser for: " + url, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onShowLeaderboardDialog() {
        runOnUiThread(() -> {
            AudioManager audioMgr = gameView.getAudioManager();
            ScoreManager scoreMgr = gameView.getScoreManager();
            ActivationManager actMgr = gameView.getActivationManager();
            Typeface font = getGameFont();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            ScrollView scrollView = new ScrollView(this);
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(40, 30, 40, 30);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(20, 24, 38));
            bg.setCornerRadius(24f);
            bg.setStroke(3, Color.rgb(243, 156, 18));
            layout.setBackground(bg);

            // Header Title
            TextView title = new TextView(this);
            title.setText("Global Leaderboard");
            title.setTextSize(20);
            title.setTypeface(font);
            title.setTextColor(Color.rgb(255, 215, 0));
            title.setGravity(Gravity.CENTER);
            layout.addView(title);

            // Player Profile Status Card (No submit button - sync is automatic)
            LinearLayout profileCard = new LinearLayout(this);
            profileCard.setOrientation(LinearLayout.VERTICAL);
            profileCard.setPadding(20, 14, 20, 14);
            GradientDrawable profBg = new GradientDrawable();
            profBg.setColor(Color.rgb(30, 35, 54));
            profBg.setCornerRadius(14f);
            profileCard.setBackground(profBg);
            LinearLayout.LayoutParams lpProf = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpProf.setMargins(0, 14, 0, 14);
            profileCard.setLayoutParams(lpProf);

            String playerName = leaderboardManager.getPlayerName(actMgr.getActiveHeadIndex());
            TextView tvPlayer = new TextView(this);
            tvPlayer.setText("Player: " + playerName);
            tvPlayer.setTextSize(14);
            tvPlayer.setTypeface(font);
            tvPlayer.setTextColor(Color.WHITE);
            profileCard.addView(tvPlayer);

            TextView myBest = new TextView(this);
            myBest.setText("Your High Score: " + scoreMgr.getHighScore());
            myBest.setTextSize(13);
            myBest.setTypeface(font);
            myBest.setTextColor(Color.rgb(0, 229, 255));
            myBest.setPadding(0, 4, 0, 0);
            profileCard.addView(myBest);

            layout.addView(profileCard);

            // Cache / sync status indicator
            TextView cacheStatus = new TextView(this);
            cacheStatus.setTextSize(11);
            cacheStatus.setTypeface(font);
            cacheStatus.setTextColor(Color.rgb(243, 156, 18));
            cacheStatus.setGravity(Gravity.CENTER);
            cacheStatus.setVisibility(View.GONE);
            layout.addView(cacheStatus);

            // Top scores list container
            LinearLayout listContainer = new LinearLayout(this);
            listContainer.setOrientation(LinearLayout.VERTICAL);
            layout.addView(listContainer);

            TextView statusText = new TextView(this);
            statusText.setText("Loading top players...");
            statusText.setTextSize(13);
            statusText.setTypeface(font);
            statusText.setTextColor(Color.rgb(200, 210, 230));
            statusText.setGravity(Gravity.CENTER);
            statusText.setPadding(0, 14, 0, 14);
            listContainer.addView(statusText);

            // Action Buttons Row (Refresh & Close)
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setGravity(Gravity.CENTER);

            Button refreshBtn = new Button(this);
            refreshBtn.setText("REFRESH");
            refreshBtn.setTextColor(Color.WHITE);
            refreshBtn.setTypeface(font);
            setButtonVectorIcon(refreshBtn, R.drawable.ic_refresh, Color.WHITE);
            GradientDrawable btnRefBg = new GradientDrawable();
            btnRefBg.setColor(Color.rgb(52, 152, 219));
            btnRefBg.setCornerRadius(12f);
            refreshBtn.setBackground(btnRefBg);
            LinearLayout.LayoutParams lpRef = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lpRef.setMargins(0, 14, 8, 0);
            refreshBtn.setLayoutParams(lpRef);
            btnRow.addView(refreshBtn);

            Button closeBtn = new Button(this);
            closeBtn.setText("CLOSE");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTypeface(font);
            GradientDrawable btnClsBg = new GradientDrawable();
            btnClsBg.setColor(Color.rgb(60, 70, 90));
            btnClsBg.setCornerRadius(12f);
            closeBtn.setBackground(btnClsBg);
            LinearLayout.LayoutParams lpCls = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lpCls.setMargins(8, 14, 0, 0);
            closeBtn.setLayoutParams(lpCls);
            btnRow.addView(closeBtn);

            layout.addView(btnRow);

            scrollView.addView(layout);
            builder.setView(scrollView);

            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            dialog.show();

            Runnable loadLeaderboard = () -> {
                leaderboardManager.fetchLeaderboard(new LeaderboardManager.FetchCallback() {
                    @Override
                    public void onSuccess(List<LeaderboardManager.Entry> entries, boolean isFromCache) {
                        listContainer.removeAllViews();
                        if (isFromCache) {
                            cacheStatus.setText("[OFFLINE CACHE]");
                            cacheStatus.setVisibility(View.VISIBLE);
                        } else {
                            cacheStatus.setVisibility(View.GONE);
                        }

                        if (entries.isEmpty()) {
                            TextView emptyTv = new TextView(MainActivity.this);
                            emptyTv.setText("No scores recorded yet. Play to set a record!");
                            emptyTv.setTextSize(13);
                            emptyTv.setTypeface(font);
                            emptyTv.setTextColor(Color.rgb(180, 190, 210));
                            emptyTv.setGravity(Gravity.CENTER);
                            emptyTv.setPadding(0, 14, 0, 14);
                            listContainer.addView(emptyTv);
                            return;
                        }

                        for (int i = 0; i < entries.size(); i++) {
                            LeaderboardManager.Entry e = entries.get(i);
                            LinearLayout row = new LinearLayout(MainActivity.this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setPadding(12, 8, 12, 8);

                            String rankLabel = (i == 0) ? "#1 [GOLD] " : (i == 1) ? "#2 [SILVER] " : (i == 2) ? "#3 [BRONZE] " : ("#" + (i + 1) + " ");
                            TextView rankName = new TextView(MainActivity.this);
                            rankName.setText(rankLabel + e.name);
                            rankName.setTypeface(font);
                            rankName.setTextSize(14);
                            rankName.setTextColor((i < 3) ? Color.rgb(255, 215, 0) : Color.WHITE);
                            LinearLayout.LayoutParams lpName = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                            rankName.setLayoutParams(lpName);
                            row.addView(rankName);

                            TextView scoreTv = new TextView(MainActivity.this);
                            scoreTv.setText(String.valueOf(e.score));
                            scoreTv.setTypeface(font);
                            scoreTv.setTextSize(15);
                            scoreTv.setTextColor(Color.rgb(0, 229, 255));
                            row.addView(scoreTv);

                            listContainer.addView(row);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        listContainer.removeAllViews();
                        TextView errTv = new TextView(MainActivity.this);
                        errTv.setText("Notice: " + message);
                        errTv.setTextSize(12);
                        errTv.setTypeface(font);
                        errTv.setTextColor(Color.rgb(255, 120, 120));
                        errTv.setGravity(Gravity.CENTER);
                        errTv.setPadding(0, 10, 0, 10);
                        listContainer.addView(errTv);
                    }
                });
            };

            // First load from live/cache
            loadLeaderboard.run();

            // Refresh action: sync local vs server highscore then reload
            refreshBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                statusText.setText("Syncing scores with server...");
                listContainer.removeAllViews();
                listContainer.addView(statusText);

                leaderboardManager.syncScoresWithServer(
                        scoreMgr.getHighScore(),
                        actMgr.getActiveHash(),
                        actMgr.getActiveHeadIndex(),
                        actMgr.getActiveHead(),
                        syncedScore -> {
                            if (syncedScore > scoreMgr.getHighScore()) {
                                scoreMgr.setHighScore(syncedScore);
                                myBest.setText("Your High Score: " + syncedScore);
                            }
                            loadLeaderboard.run();
                        }
                );
            });

            closeBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
            });
        });
    }

    @Override
    public void onShowCreditsDialog() {
        runOnUiThread(() -> {
            AudioManager audioMgr = gameView.getAudioManager();
            Typeface font = getGameFont();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            ScrollView scrollView = new ScrollView(this);
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(40, 30, 40, 30);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(20, 24, 38));
            bg.setCornerRadius(24f);
            bg.setStroke(3, Color.rgb(155, 89, 182));
            layout.setBackground(bg);

            // Header Title
            TextView title = new TextView(this);
            title.setText("Credits & Creator");
            title.setTextSize(22);
            title.setTypeface(font);
            title.setTextColor(Color.rgb(255, 215, 0));
            title.setGravity(Gravity.CENTER);
            layout.addView(title);

            // Creator Subheading
            TextView subTitle = new TextView(this);
            subTitle.setText("Atif Arman (Exotic Atif) • Creator & Developer");
            subTitle.setTextSize(13);
            subTitle.setTypeface(font);
            subTitle.setTextColor(Color.rgb(0, 229, 255));
            subTitle.setGravity(Gravity.CENTER);
            subTitle.setPadding(0, 8, 0, 14);
            layout.addView(subTitle);

            // Summarized Concise Specs
            String bioText = "• Coding, Art & Audio Design: Atif Arman\n" +
                    "• Typography: DotGothic16 by Fontworks (Google Fonts OFL)\n" +
                    "• Edition: Version 2.0.0 (High-FPS Dynamic Edition)";

            TextView content = new TextView(this);
            content.setText(bioText);
            content.setTextSize(12);
            content.setTypeface(font);
            content.setTextColor(Color.rgb(215, 228, 245));
            content.setPadding(0, 0, 0, 14);
            layout.addView(content);

            // Social Buttons Section Header
            TextView socialsHeader = new TextView(this);
            socialsHeader.setText("CONNECT WITH EXOTIC ATIF");
            socialsHeader.setTextSize(14);
            socialsHeader.setTypeface(font);
            socialsHeader.setTextColor(Color.rgb(255, 215, 0));
            socialsHeader.setGravity(Gravity.CENTER);
            socialsHeader.setPadding(0, 6, 0, 10);
            layout.addView(socialsHeader);

            // Row 1: Instagram, YouTube, Snapchat
            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            addSocialButton(row1, R.drawable.ic_instagram, "Instagram", "https://www.instagram.com/exotic_atif", Color.rgb(225, 48, 108), audioMgr, font);
            addSocialButton(row1, R.drawable.ic_youtube, "YouTube", "https://www.youtube.com/@exotic_atif", Color.rgb(230, 33, 23), audioMgr, font);
            addSocialButton(row1, R.drawable.ic_snapchat, "Snapchat", "https://www.snapchat.com/add/exotic_atif", Color.rgb(44, 49, 62), audioMgr, font);
            layout.addView(row1);

            // Row 2: Twitter, Threads, GitHub
            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            addSocialButton(row2, R.drawable.ic_x_twitter, "X / Twitter", "https://x.com/exotic_atif", Color.rgb(40, 45, 55), audioMgr, font);
            addSocialButton(row2, R.drawable.ic_threads, "Threads", "https://www.threads.net/@exotic_atif", Color.rgb(30, 34, 45), audioMgr, font);
            addSocialButton(row2, R.drawable.ic_github, "GitHub", "https://github.com/exotic-atif", Color.rgb(36, 41, 46), audioMgr, font);
            layout.addView(row2);

            // Row 3: Web / Releases
            LinearLayout row3 = new LinearLayout(this);
            row3.setOrientation(LinearLayout.HORIZONTAL);
            addSocialButton(row3, R.drawable.ic_globe, "Official Releases & Web", "https://github.com/exotic-atif/urukku-manush/releases", Color.rgb(0, 131, 143), audioMgr, font);
            layout.addView(row3);

            // Close button
            Button closeBtn = new Button(this);
            closeBtn.setText("BACK TO MENU");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTypeface(font);
            GradientDrawable btnCloseBg = new GradientDrawable();
            btnCloseBg.setColor(Color.rgb(155, 89, 182));
            btnCloseBg.setCornerRadius(14f);
            closeBtn.setBackground(btnCloseBg);
            closeBtn.setPadding(0, 16, 0, 16);
            LinearLayout.LayoutParams lpClose = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpClose.setMargins(0, 16, 0, 6);
            closeBtn.setLayoutParams(lpClose);
            layout.addView(closeBtn);

            scrollView.addView(layout);
            builder.setView(scrollView);

            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            dialog.show();

            closeBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
            });
        });
    }

    private void addSocialButton(LinearLayout row, int iconResId, String label, String url, int bgColor, AudioManager audioMgr, Typeface font) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(font);
        btn.setTextSize(12);
        setButtonVectorIcon(btn, iconResId, Color.WHITE);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(12f);
        btn.setBackground(bg);
        btn.setPadding(14, 12, 14, 12);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(4, 4, 4, 4);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            audioMgr.playClickSound();
            openUrlSafely(url);
        });

        row.addView(btn);
    }
}
