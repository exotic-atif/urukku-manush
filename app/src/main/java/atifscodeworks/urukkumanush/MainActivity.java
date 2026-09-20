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
import android.content.res.ColorStateList;
import android.util.Log;
import android.widget.ImageView;
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

import com.google.android.material.button.MaterialButton;

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

    private MaterialButton createMaterialIconButton(int resId, String label, int bgColor, int textColor, Typeface font) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(label);
        btn.setTextColor(textColor);
        btn.setTypeface(font);
        btn.setTextSize(13);
        btn.setLetterSpacing(0f);
        btn.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        btn.setCornerRadius((int) (14 * getResources().getDisplayMetrics().density));
        btn.setInsetTop(0);
        btn.setInsetBottom(0);

        if (resId != 0) {
            btn.setIconResource(resId);
            btn.setIconTint(ColorStateList.valueOf(textColor));
            btn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            int iconSize = (int) (18 * getResources().getDisplayMetrics().density);
            btn.setIconSize(iconSize);
            btn.setIconPadding((int) (8 * getResources().getDisplayMetrics().density));
        }

        return btn;
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
            ActivationManager actMgr = gameView.getActivationManager();
            Typeface font = getGameFont();
            float density = getResources().getDisplayMetrics().density;

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            ScrollView scrollView = new ScrollView(this);
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding((int) (24 * density), (int) (20 * density), (int) (24 * density), (int) (20 * density));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(21, 26, 40));
            bg.setCornerRadius(20 * density);
            bg.setStroke((int) (2 * density), Color.rgb(39, 217, 242));
            layout.setBackground(bg);

            // Title "GAME SETTINGS"
            TextView title = new TextView(this);
            title.setText("GAME SETTINGS");
            title.setTextSize(20);
            title.setTypeface(font);
            title.setTextColor(Color.rgb(255, 213, 42));
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, (int) (6 * density));
            layout.addView(title);

            // Version label
            TextView verLabel = new TextView(this);
            verLabel.setText("Urukku Manush v" + appUpdater.getCurrentVersion());
            verLabel.setTextSize(12);
            verLabel.setTypeface(font);
            verLabel.setTextColor(Color.rgb(174, 183, 204));
            verLabel.setGravity(Gravity.CENTER);
            verLabel.setPadding(0, 0, 0, (int) (14 * density));
            layout.addView(verLabel);

            // SECTION 1: AUDIO
            layout.addView(createSectionHeader("AUDIO", font, density));

            // SFX Toggle Row
            LinearLayout sfxRow = new LinearLayout(this);
            sfxRow.setOrientation(LinearLayout.HORIZONTAL);
            sfxRow.setGravity(Gravity.CENTER_VERTICAL);
            sfxRow.setPadding((int) (8 * density), (int) (6 * density), (int) (8 * density), (int) (6 * density));

            TextView sfxLabel = new TextView(this);
            sfxLabel.setText("SFX");
            sfxLabel.setTextSize(14);
            sfxLabel.setTypeface(font);
            sfxLabel.setTextColor(Color.rgb(243, 245, 250));
            LinearLayout.LayoutParams lpSfx = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            sfxLabel.setLayoutParams(lpSfx);
            sfxRow.addView(sfxLabel);

            MaterialButton btnSfx = new MaterialButton(this);
            btnSfx.setText(audioMgr.isSfxEnabled() ? "[ ON ]" : "[ OFF ]");
            btnSfx.setTextColor(Color.WHITE);
            btnSfx.setTypeface(font);
            btnSfx.setTextSize(12);
            btnSfx.setLetterSpacing(0f);
            btnSfx.setCornerRadius((int) (8 * density));
            btnSfx.setInsetTop(0);
            btnSfx.setInsetBottom(0);
            btnSfx.setBackgroundTintList(ColorStateList.valueOf(audioMgr.isSfxEnabled() ? Color.rgb(40, 209, 124) : Color.rgb(239, 83, 80)));
            LinearLayout.LayoutParams lpBtnSfx = new LinearLayout.LayoutParams((int) (90 * density), (int) (38 * density));
            btnSfx.setLayoutParams(lpBtnSfx);
            btnSfx.setOnClickListener(v -> {
                boolean nowEnabled = !audioMgr.isSfxEnabled();
                audioMgr.setSfxEnabled(nowEnabled);
                if (nowEnabled) audioMgr.playClickSound();
                btnSfx.setText(nowEnabled ? "[ ON ]" : "[ OFF ]");
                btnSfx.setBackgroundTintList(ColorStateList.valueOf(nowEnabled ? Color.rgb(40, 209, 124) : Color.rgb(239, 83, 80)));
            });
            sfxRow.addView(btnSfx);
            layout.addView(sfxRow);

            // Music Toggle Row
            LinearLayout musicRow = new LinearLayout(this);
            musicRow.setOrientation(LinearLayout.HORIZONTAL);
            musicRow.setGravity(Gravity.CENTER_VERTICAL);
            musicRow.setPadding((int) (8 * density), (int) (6 * density), (int) (8 * density), (int) (6 * density));

            TextView musicLabel = new TextView(this);
            musicLabel.setText("MUSIC");
            musicLabel.setTextSize(14);
            musicLabel.setTypeface(font);
            musicLabel.setTextColor(Color.rgb(243, 245, 250));
            LinearLayout.LayoutParams lpMusic = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            musicLabel.setLayoutParams(lpMusic);
            musicRow.addView(musicLabel);

            MaterialButton btnMusic = new MaterialButton(this);
            btnMusic.setText(audioMgr.isBgmEnabled() ? "[ ON ]" : "[ OFF ]");
            btnMusic.setTextColor(Color.WHITE);
            btnMusic.setTypeface(font);
            btnMusic.setTextSize(12);
            btnMusic.setLetterSpacing(0f);
            btnMusic.setCornerRadius((int) (8 * density));
            btnMusic.setInsetTop(0);
            btnMusic.setInsetBottom(0);
            btnMusic.setBackgroundTintList(ColorStateList.valueOf(audioMgr.isBgmEnabled() ? Color.rgb(40, 209, 124) : Color.rgb(239, 83, 80)));
            LinearLayout.LayoutParams lpBtnMusic = new LinearLayout.LayoutParams((int) (90 * density), (int) (38 * density));
            btnMusic.setLayoutParams(lpBtnMusic);
            btnMusic.setOnClickListener(v -> {
                boolean nowEnabled = !audioMgr.isBgmEnabled();
                audioMgr.setBgmEnabled(nowEnabled);
                audioMgr.playClickSound();
                btnMusic.setText(nowEnabled ? "[ ON ]" : "[ OFF ]");
                btnMusic.setBackgroundTintList(ColorStateList.valueOf(nowEnabled ? Color.rgb(40, 209, 124) : Color.rgb(239, 83, 80)));
            });
            musicRow.addView(btnMusic);
            layout.addView(musicRow);

            // SECTION 2: ONLINE
            layout.addView(createSectionHeader("ONLINE", font, density));

            MaterialButton leaderboardBtn = createMaterialIconButton(
                    R.drawable.ic_trophy,
                    "LEADERBOARD",
                    Color.rgb(243, 156, 18),
                    Color.WHITE,
                    font
            );
            LinearLayout.LayoutParams lpLead = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * density));
            lpLead.setMargins(0, (int) (6 * density), 0, (int) (6 * density));
            leaderboardBtn.setLayoutParams(lpLead);
            layout.addView(leaderboardBtn);

            // SECTION 3: SYSTEM
            layout.addView(createSectionHeader("SYSTEM", font, density));

            MaterialButton updateBtn = createMaterialIconButton(
                    R.drawable.ic_refresh,
                    "CHECK FOR UPDATES",
                    Color.rgb(40, 209, 124),
                    Color.WHITE,
                    font
            );
            LinearLayout.LayoutParams lpUp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * density));
            lpUp.setMargins(0, (int) (6 * density), 0, (int) (6 * density));
            updateBtn.setLayoutParams(lpUp);
            layout.addView(updateBtn);

            // Unlock / switch head button
            MaterialButton actvBtn = createMaterialIconButton(
                    R.drawable.ic_gear,
                    actMgr.isActivated() ? "ENTER ACTIVATION CODE" : "ACTIVATE GAME",
                    Color.rgb(59, 167, 232),
                    Color.WHITE,
                    font
            );
            LinearLayout.LayoutParams lpActv = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * density));
            lpActv.setMargins(0, (int) (4 * density), 0, (int) (14 * density));
            actvBtn.setLayoutParams(lpActv);
            layout.addView(actvBtn);

            // Close button
            MaterialButton closeBtn = createMaterialIconButton(
                    0,
                    "CLOSE",
                    Color.rgb(48, 56, 78),
                    Color.WHITE,
                    font
            );
            LinearLayout.LayoutParams lpClose = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * density));
            closeBtn.setLayoutParams(lpClose);
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

            actvBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
                onShowActivationDialog(null);
            });

            closeBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
            });
        });
    }

    private View createSectionHeader(String title, Typeface font, float density) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, (int) (14 * density), 0, (int) (6 * density));

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(12);
        tv.setTypeface(font);
        tv.setTextColor(Color.rgb(39, 217, 242));
        tv.setLetterSpacing(0.05f);
        container.addView(tv);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(48, 56, 78));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (1 * density));
        lp.setMargins(0, (int) (4 * density), 0, 0);
        divider.setLayoutParams(lp);
        container.addView(divider);

        return container;
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

        boolean isDownloaded = appUpdater.isApkAlreadyDownloaded(info);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(18, 22, 34));
        bg.setCornerRadius(24f);
        bg.setStroke(3, (isDownloaded || info.isUpdateAvailable) ? Color.rgb(46, 204, 113) : Color.rgb(0, 229, 255));
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
        if (isDownloaded) {
            badge.setText("UPDATE PACKAGE READY TO INSTALL");
            badge.setTextColor(Color.rgb(46, 204, 113));
        } else if (info.isUpdateAvailable) {
            badge.setText("NEW VERSION AVAILABLE: " + info.tagName);
            badge.setTextColor(Color.rgb(46, 204, 113));
        } else {
            badge.setText("YOU ARE ON THE LATEST VERSION");
            badge.setTextColor(Color.rgb(0, 229, 255));
        }
        badge.setTextSize(13);
        badge.setTypeface(font);
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
        verCompare.setText("Installed: v" + appUpdater.getCurrentVersion() + "   ->   Available: " + info.tagName);
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
        notesHeader.setText("PATCH NOTES & DETAILS");
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

        // Live Progress Container
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

        // Controls Row (Pause/Resume & Cancel)
        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setPadding(0, 8, 0, 0);

        MaterialButton pauseBtn = createMaterialIconButton(0, "PAUSE", Color.rgb(243, 156, 18), Color.WHITE, font);
        LinearLayout.LayoutParams lpPause = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lpPause.setMargins(0, 0, 6, 0);
        pauseBtn.setLayoutParams(lpPause);
        controlsRow.addView(pauseBtn);

        MaterialButton cancelBtn = createMaterialIconButton(0, "CANCEL", Color.rgb(231, 76, 60), Color.WHITE, font);
        LinearLayout.LayoutParams lpCancel = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lpCancel.setMargins(6, 0, 0, 0);
        cancelBtn.setLayoutParams(lpCancel);
        controlsRow.addView(cancelBtn);

        progressContainer.addView(controlsRow);
        layout.addView(progressContainer);

        // Action Button
        MaterialButton actionBtn = createMaterialIconButton(
                0,
                isDownloaded ? "INSTALL NOW (v" + info.tagName + ")" : (info.isUpdateAvailable ? "DOWNLOAD & INSTALL" : "RE-INSTALL (v" + info.tagName + ")"),
                Color.rgb(46, 204, 113),
                Color.WHITE,
                font
        );
        LinearLayout.LayoutParams lpAct = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpAct.setMargins(0, 20, 0, 8);
        actionBtn.setLayoutParams(lpAct);
        layout.addView(actionBtn);

        LinearLayout rowButtons = new LinearLayout(this);
        rowButtons.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton webBtn = createMaterialIconButton(R.drawable.ic_github, "VIEW ON GITHUB", Color.rgb(36, 41, 46), Color.WHITE, font);
        LinearLayout.LayoutParams lpWeb = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lpWeb.setMargins(0, 0, 6, 0);
        webBtn.setLayoutParams(lpWeb);
        rowButtons.addView(webBtn);

        MaterialButton closeBtn = createMaterialIconButton(0, "CLOSE", Color.rgb(60, 70, 90), Color.WHITE, font);
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
        if (isDownloaded) {
            downloadedApk[0] = appUpdater.getDownloadedApkFile(info);
        }

        pauseBtn.setOnClickListener(pv -> {
            if (appUpdater.isPaused()) {
                appUpdater.resumeDownload();
                pauseBtn.setText("PAUSE");
                progressLabel.setText("Downloading update package...");
                progressLabel.setTextColor(Color.rgb(0, 229, 255));
            } else {
                appUpdater.pauseDownload();
                pauseBtn.setText("RESUME");
                progressLabel.setText("Download paused.");
                progressLabel.setTextColor(Color.rgb(255, 215, 0));
            }
        });

        cancelBtn.setOnClickListener(cv -> {
            appUpdater.cancelDownload();
            progressContainer.setVisibility(View.GONE);
            actionBtn.setVisibility(View.VISIBLE);
            actionBtn.setEnabled(true);
            actionBtn.setText(info.isUpdateAvailable ? "DOWNLOAD & INSTALL" : "RE-INSTALL (v" + info.tagName + ")");
        });

        actionBtn.setOnClickListener(v -> {
            if (downloadedApk[0] != null && downloadedApk[0].exists()) {
                appUpdater.installApk(MainActivity.this, downloadedApk[0]);
                return;
            }

            actionBtn.setVisibility(View.GONE);
            progressContainer.setVisibility(View.VISIBLE);
            pauseBtn.setText("PAUSE");
            pauseBtn.setVisibility(View.VISIBLE);
            cancelBtn.setVisibility(View.VISIBLE);

            appUpdater.downloadUpdate(info, new AppUpdater.DownloadProgressCallback() {
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
                    pauseBtn.setVisibility(View.GONE);
                    cancelBtn.setVisibility(View.GONE);
                    actionBtn.setVisibility(View.VISIBLE);
                    actionBtn.setEnabled(true);
                    actionBtn.setText("PACKAGE READY - INSTALL NOW");
                    actionBtn.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(46, 204, 113)));

                    progressLabel.setText("Download verified and complete!");
                    progressLabel.setTextColor(Color.rgb(46, 204, 113));
                    progressBar.setProgress(100);

                    Toast.makeText(MainActivity.this, "Download complete! Opening Android installer...", Toast.LENGTH_SHORT).show();
                    appUpdater.installApk(MainActivity.this, apkFile);
                }

                @Override
                public void onError(String error) {
                    pauseBtn.setVisibility(View.GONE);
                    cancelBtn.setVisibility(View.GONE);
                    actionBtn.setVisibility(View.VISIBLE);
                    actionBtn.setEnabled(true);
                    actionBtn.setText("RETRY DOWNLOAD");
                    progressLabel.setText("Notice: " + error);
                    progressLabel.setTextColor(Color.rgb(255, 80, 80));
                    Toast.makeText(MainActivity.this, "Download error: " + error, Toast.LENGTH_LONG).show();
                }
            });
        });

        webBtn.setOnClickListener(v -> openUrlSafely(info.htmlUrl));
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

            MaterialButton refreshBtn = createMaterialIconButton(R.drawable.ic_refresh, "REFRESH", Color.rgb(52, 152, 219), Color.WHITE, font);
            LinearLayout.LayoutParams lpRef = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lpRef.setMargins(0, 14, 8, 0);
            refreshBtn.setLayoutParams(lpRef);
            btnRow.addView(refreshBtn);

            MaterialButton closeBtn = createMaterialIconButton(0, "CLOSE", Color.rgb(60, 70, 90), Color.WHITE, font);
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
                            boolean isMe = (actMgr.getActiveHash() != null && actMgr.getActiveHash().equalsIgnoreCase(e.code))
                                    || (e.headIndex == actMgr.getActiveHeadIndex());

                            LinearLayout row = new LinearLayout(MainActivity.this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setGravity(Gravity.CENTER_VERTICAL);
                            row.setPadding(16, 12, 16, 12);

                            GradientDrawable rowBg = new GradientDrawable();
                            if (isMe) {
                                rowBg.setColor(Color.rgb(24, 38, 56));
                                rowBg.setStroke(2, Color.rgb(0, 229, 255));
                            } else {
                                rowBg.setColor((i % 2 == 0) ? Color.rgb(24, 28, 42) : Color.rgb(20, 24, 36));
                            }
                            rowBg.setCornerRadius(10f);
                            row.setBackground(rowBg);

                            LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            lpRow.setMargins(0, 4, 0, 4);
                            row.setLayoutParams(lpRow);

                            int iconDim = (int) (22 * getResources().getDisplayMetrics().density);

                            if (i == 0) {
                                ImageView iv = new ImageView(MainActivity.this);
                                iv.setImageResource(R.drawable.ic_rank_1);
                                iv.setColorFilter(Color.rgb(255, 215, 0));
                                LinearLayout.LayoutParams lpIv = new LinearLayout.LayoutParams(iconDim, iconDim);
                                lpIv.setMargins(0, 0, 12, 0);
                                iv.setLayoutParams(lpIv);
                                row.addView(iv);
                            } else if (i == 1) {
                                ImageView iv = new ImageView(MainActivity.this);
                                iv.setImageResource(R.drawable.ic_rank_2);
                                iv.setColorFilter(Color.rgb(200, 214, 229));
                                LinearLayout.LayoutParams lpIv = new LinearLayout.LayoutParams(iconDim, iconDim);
                                lpIv.setMargins(0, 0, 12, 0);
                                iv.setLayoutParams(lpIv);
                                row.addView(iv);
                            } else if (i == 2) {
                                ImageView iv = new ImageView(MainActivity.this);
                                iv.setImageResource(R.drawable.ic_rank_3);
                                iv.setColorFilter(Color.rgb(205, 127, 50));
                                LinearLayout.LayoutParams lpIv = new LinearLayout.LayoutParams(iconDim, iconDim);
                                lpIv.setMargins(0, 0, 12, 0);
                                iv.setLayoutParams(lpIv);
                                row.addView(iv);
                            } else {
                                TextView rankPill = new TextView(MainActivity.this);
                                rankPill.setText("#" + (i + 1));
                                rankPill.setTypeface(font);
                                rankPill.setTextSize(12);
                                rankPill.setTextColor(Color.rgb(174, 183, 204));
                                rankPill.setGravity(Gravity.CENTER);
                                LinearLayout.LayoutParams lpPill = new LinearLayout.LayoutParams(iconDim, iconDim);
                                lpPill.setMargins(0, 0, 12, 0);
                                rankPill.setLayoutParams(lpPill);
                                row.addView(rankPill);
                            }

                            TextView rankName = new TextView(MainActivity.this);
                            rankName.setText(e.name + (isMe ? " (YOU)" : ""));
                            rankName.setTypeface(font);
                            rankName.setTextSize(14);
                            if (isMe) {
                                rankName.setTextColor(Color.rgb(0, 229, 255));
                            } else if (i == 0) {
                                rankName.setTextColor(Color.rgb(255, 215, 0));
                            } else if (i == 1) {
                                rankName.setTextColor(Color.rgb(200, 214, 229));
                            } else if (i == 2) {
                                rankName.setTextColor(Color.rgb(205, 127, 50));
                            } else {
                                rankName.setTextColor(Color.WHITE);
                            }
                            LinearLayout.LayoutParams lpName = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                            rankName.setLayoutParams(lpName);
                            row.addView(rankName);

                            TextView scoreTv = new TextView(MainActivity.this);
                            scoreTv.setText(String.valueOf(e.score));
                            scoreTv.setTypeface(font);
                            scoreTv.setTextSize(15);
                            scoreTv.setTextColor(isMe ? Color.rgb(0, 229, 255) : Color.rgb(255, 213, 42));
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
                    "• Edition: Version 2.1.0 (High-FPS Dynamic Edition)";

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
            MaterialButton closeBtn = createMaterialIconButton(0, "BACK TO MENU", Color.rgb(155, 89, 182), Color.WHITE, font);
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
        MaterialButton btn = createMaterialIconButton(iconResId, label, Color.rgb(32, 38, 56), Color.WHITE, font);
        btn.setTextSize(11);
        btn.setStrokeColor(ColorStateList.valueOf(Color.rgb(45, 52, 75)));
        btn.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));

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
