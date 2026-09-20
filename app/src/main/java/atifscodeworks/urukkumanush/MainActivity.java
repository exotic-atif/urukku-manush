package atifscodeworks.urukkumanush;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;

public class MainActivity extends AppCompatActivity implements GameActionListener {
    private static final String TAG = "MainActivity";

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure external storage folder sdcard/Android/data/atifscodeworks.urukkumanush/files is created
        ensureExternalDataDirectory();

        // Configure dynamic high refresh rate (60, 90, 120, 144Hz+)
        enableHighRefreshRate();

        // Hide system bars and enable immersive fullscreen
        setupImmersiveFullscreen();

        gameView = new GameView(this);
        gameView.setActionListener(this);
        setContentView(gameView);
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
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) {
            gameView.pauseGame();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupImmersiveFullscreen();
        enableHighRefreshRate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
                    Toast.makeText(this, "🎉 Activated! Welcome to Urukku Manush!", Toast.LENGTH_SHORT).show();
                    gameView.reloadPlayerHead();
                    dialog.dismiss();
                    if (onActivated != null) {
                        onActivated.run();
                    }
                } else {
                    errorText.setText("❌ Invalid activation code! Try again.");
                    errorText.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    public void onShowOptionsDialog() {
        runOnUiThread(() -> {
            AudioManager audioMgr = gameView.getAudioManager();
            ScoreManager scoreMgr = gameView.getScoreManager();
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
            title.setText("⚙️ Game Options");
            title.setTextSize(22);
            title.setTypeface(font);
            title.setTextColor(Color.rgb(0, 229, 255));
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 24);
            layout.addView(title);

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

            // Reset High Score button
            Button resetScoreBtn = new Button(this);
            resetScoreBtn.setText("RESET HIGH SCORE");
            resetScoreBtn.setTextColor(Color.WHITE);
            resetScoreBtn.setTypeface(font);
            GradientDrawable btnResetBg = new GradientDrawable();
            btnResetBg.setColor(Color.rgb(231, 76, 60));
            btnResetBg.setCornerRadius(12f);
            resetScoreBtn.setBackground(btnResetBg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 24, 0, 16);
            resetScoreBtn.setLayoutParams(lp);
            layout.addView(resetScoreBtn);

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

            resetScoreBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                scoreMgr.resetHighScore();
                Toast.makeText(this, "High score reset to 0!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
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
            layout.setPadding(50, 40, 50, 40);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(20, 24, 38));
            bg.setCornerRadius(24f);
            bg.setStroke(3, Color.rgb(155, 89, 182));
            layout.setBackground(bg);

            // Header Title
            TextView title = new TextView(this);
            title.setText("🌟 Urukku Manush - Credits");
            title.setTextSize(22);
            title.setTypeface(font);
            title.setTextColor(Color.rgb(255, 215, 0));
            title.setGravity(Gravity.CENTER);
            layout.addView(title);

            // Creator Subheading
            TextView subTitle = new TextView(this);
            subTitle.setText("Game & Assets Made by Atif\nDigital Creator / Coder / Visual Storyteller\n★ Atif Arman (Exotic Atif) ★");
            subTitle.setTextSize(14);
            subTitle.setTypeface(font);
            subTitle.setTextColor(Color.rgb(0, 229, 255));
            subTitle.setGravity(Gravity.CENTER);
            subTitle.setPadding(0, 10, 0, 18);
            layout.addView(subTitle);

            // Full Bio
            String bioText = "Welcome to the official portfolio of Exotic Atif — a digital creator and web developer also known as Atif Arman. Exotic Atif's portfolio showcases creative work, coding projects, and visual storytelling.\n\n" +
                    "Exotic Atif is a teenage creator obsessed with building things that combine logic, design, and emotion. The core of Exotic Atif's portfolio is coding — where ideas become useful projects through clean structure and continuous experiments.\n\n" +
                    "As a digital creator passionate about coding, photography, videography, and creative content, Exotic Atif explores the intersection of technology and creativity. This journey is fueled by relentless curiosity for new tools, platforms, and techniques that push boundaries and express a unique perspective.\n\n" +
                    "Coding is Exotic Atif's creative playground for problem-solving and innovation. From developing websites to experimenting with new programming languages, every Exotic Atif project transforms ideas into functional, user-friendly creations. Photography and videography complement Exotic Atif's creative work, freezing moments and shaping visual stories that resonate.\n\n" +
                    "Exotic Atif shares work and creative progress across social platforms to connect with like-minded creators. For collaborations and direct communication, email is always open.\n\n" +
                    "Typography: DotGothic16 by Fontworks (Google Fonts OFL)\n" +
                    "Version 1.1 (Dynamic High-FPS Edition)";

            TextView content = new TextView(this);
            content.setText(bioText);
            content.setTextSize(13);
            content.setTypeface(font);
            content.setTextColor(Color.rgb(215, 228, 245));
            content.setPadding(0, 0, 0, 20);
            layout.addView(content);

            // Social Buttons Section Header
            TextView socialsHeader = new TextView(this);
            socialsHeader.setText("🌐 CONNECT WITH EXOTIC ATIF");
            socialsHeader.setTextSize(15);
            socialsHeader.setTypeface(font);
            socialsHeader.setTextColor(Color.rgb(255, 215, 0));
            socialsHeader.setGravity(Gravity.CENTER);
            socialsHeader.setPadding(0, 10, 0, 14);
            layout.addView(socialsHeader);

            // Social links
            addSocialButton(layout, "📸 Instagram (@exotic_atif)", "https://www.instagram.com/exotic_atif", Color.rgb(225, 48, 108), audioMgr, font);
            addSocialButton(layout, "📸 Instagram (@exotic.atif)", "https://www.instagram.com/exotic.atif", Color.rgb(193, 53, 132), audioMgr, font);
            addSocialButton(layout, "🎬 YouTube (@exotic_atif)", "https://www.youtube.com/@exotic_atif", Color.rgb(230, 33, 23), audioMgr, font);
            addSocialButton(layout, "✖️ X / Twitter (@exotic_atif)", "https://x.com/exotic_atif", Color.rgb(40, 45, 55), audioMgr, font);
            addSocialButton(layout, "🧵 Threads (@exotic_atif)", "https://www.threads.net/@exotic_atif", Color.rgb(30, 30, 30), audioMgr, font);
            addSocialButton(layout, "📘 Facebook (ExoticAtif)", "https://www.facebook.com/ExoticAtif", Color.rgb(24, 119, 242), audioMgr, font);
            addSocialButton(layout, "👻 Snapchat (@exotic_atif)", "https://www.snapchat.com/add/exotic_atif", Color.rgb(255, 252, 0), audioMgr, font, Color.BLACK);
            addSocialButton(layout, "✈️ Telegram (@exotic_atif)", "https://t.me/exotic_atif", Color.rgb(0, 136, 204), audioMgr, font);
            addSocialButton(layout, "🐙 GitHub (@exotic-atif)", "https://github.com/exotic-atif", Color.rgb(36, 41, 46), audioMgr, font);

            // Close button
            Button closeBtn = new Button(this);
            closeBtn.setText("BACK TO MENU");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTypeface(font);
            GradientDrawable btnCloseBg = new GradientDrawable();
            btnCloseBg.setColor(Color.rgb(155, 89, 182));
            btnCloseBg.setCornerRadius(14f);
            closeBtn.setBackground(btnCloseBg);
            closeBtn.setPadding(0, 20, 0, 20);
            LinearLayout.LayoutParams lpClose = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpClose.setMargins(0, 20, 0, 10);
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

    private void addSocialButton(LinearLayout layout, String label, String url, int bgColor, AudioManager audioMgr, Typeface font) {
        addSocialButton(layout, label, url, bgColor, audioMgr, font, Color.WHITE);
    }

    private void addSocialButton(LinearLayout layout, String label, String url, int bgColor, AudioManager audioMgr, Typeface font, int textColor) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextColor(textColor);
        btn.setTypeface(font);
        btn.setTextSize(13);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(12f);
        btn.setBackground(bg);
        btn.setPadding(20, 14, 20, 14);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 6, 0, 6);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            audioMgr.playClickSound();
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Opening " + url, Toast.LENGTH_SHORT).show();
            }
        });

        layout.addView(btn);
    }
}
