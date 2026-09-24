package atifscodeworks.urukkumanush;

import android.app.AlertDialog;
import android.content.Context;
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
import android.widget.FrameLayout;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

        initPushNotifications(actMgr);
    }

    private void initPushNotifications(ActivationManager actMgr) {
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            UrukkuManushMessagingService.createNotificationChannel(nm);
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful() || task.getResult() == null) {
                            Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                            return;
                        }
                        String token = task.getResult();
                        getSharedPreferences("urukku_manush_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString(UrukkuManushMessagingService.KEY_FCM_TOKEN, token)
                                .apply();

                        if (actMgr.isActivated()) {
                            leaderboardManager.registerDeviceToken(token, actMgr.getActiveHash());
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "Error initializing Firebase Messaging: " + e.getMessage());
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

    private Typeface getPressStartFont() {
        try {
            return Typeface.createFromAsset(getAssets(), "fonts/PressStart2P-Regular.ttf");
        } catch (Exception e) {
            return getGameFont();
        }
    }

    private static class PixelMedalView extends View {
        private final int rank;
        private final float density;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public PixelMedalView(Context context, int rank, Typeface font, float density) {
            super(context);
            this.rank = rank;
            this.density = density;

            int fillColor;
            int hlColor;
            if (rank == 1) {
                fillColor = Color.rgb(255, 216, 77); // Gold #FFD84D
                hlColor = Color.rgb(255, 245, 185);
            } else if (rank == 2) {
                fillColor = Color.rgb(215, 215, 215); // Silver #D7D7D7
                hlColor = Color.rgb(255, 255, 255);
            } else {
                fillColor = Color.rgb(217, 133, 82); // Bronze #D98552
                hlColor = Color.rgb(248, 185, 135);
            }

            fillPaint.setColor(fillColor);
            fillPaint.setStyle(Paint.Style.FILL);

            highlightPaint.setColor(hlColor);
            highlightPaint.setStyle(Paint.Style.FILL);

            strokePaint.setColor(Color.rgb(23, 23, 23));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2.2f * density);
            strokePaint.setStrokeJoin(Paint.Join.MITER);

            textPaint.setColor(Color.rgb(23, 23, 23));
            textPaint.setTypeface(font);
            textPaint.setTextSize(9.5f * density);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            float inset = 1.4f * density;
            float l = inset;
            float t = inset;
            float r = w - inset;
            float b = h - inset;
            float cut = (r - l) * 0.28f;

            path.reset();
            path.moveTo(l + cut, t);
            path.lineTo(r - cut, t);
            path.lineTo(r, t + cut);
            path.lineTo(r, b - cut);
            path.lineTo(r - cut, b);
            path.lineTo(l + cut, b);
            path.lineTo(l, b - cut);
            path.lineTo(l, t + cut);
            path.close();

            canvas.drawPath(path, fillPaint);

            // Pixel highlight on top-left
            float hlSize = 3.5f * density;
            canvas.drawRect(l + cut, t + 1.2f * density, l + cut + hlSize, t + 1.2f * density + hlSize, highlightPaint);

            canvas.drawPath(path, strokePaint);

            // Centered rank number
            String text = String.valueOf(rank);
            Rect bounds = new Rect();
            textPaint.getTextBounds(text, 0, text.length(), bounds);
            float textY = (h + bounds.height()) / 2f - bounds.bottom;
            float textX = w / 2f;
            canvas.drawText(text, textX, textY, textPaint);
        }
    }

    private static class YouBadgeView extends View {
        private final float density;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public YouBadgeView(Context context, Typeface font, float density) {
            super(context);
            this.density = density;

            fillPaint.setColor(Color.rgb(131, 230, 247)); // #83E6F7
            fillPaint.setStyle(Paint.Style.FILL);

            strokePaint.setColor(Color.rgb(15, 56, 70)); // #0F3846 Dark outline
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2f * density);
            strokePaint.setStrokeJoin(Paint.Join.MITER);

            textPaint.setColor(Color.rgb(15, 56, 70));
            textPaint.setTypeface(font);
            textPaint.setTextSize(6.8f * density);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            float inset = 1.2f * density;
            float l = inset;
            float t = inset;
            float r = w - inset;
            float b = h - inset;
            float arrowW = (b - t) * 0.45f;
            float arrowStartX = r - arrowW;
            float corner = 2f * density;

            path.reset();
            path.moveTo(l + corner, t);
            path.lineTo(arrowStartX, t);
            path.lineTo(r, (t + b) / 2f);
            path.lineTo(arrowStartX, b);
            path.lineTo(l + corner, b);
            path.lineTo(l, b - corner);
            path.lineTo(l, t + corner);
            path.close();

            canvas.drawPath(path, fillPaint);
            canvas.drawPath(path, strokePaint);

            // Centered "YOU" text in rectangular body
            String text = "YOU";
            Rect bounds = new Rect();
            textPaint.getTextBounds(text, 0, 3, bounds);
            float bodyCenterX = (l + arrowStartX) / 2f;
            float textY = (h + bounds.height()) / 2f - bounds.bottom;
            canvas.drawText(text, bodyCenterX, textY, textPaint);
        }
    }

    private static class RetroArcadeButton extends View {
        private String text;
        private Drawable icon;
        private int bodyColor;
        private int highlightColor;
        private int shadowColor;
        private int textColor = Color.WHITE;
        private float textSizeSp = 10.5f;
        private final Typeface font;
        private final float density;
        private boolean isPressed = false;
        private final RectF bounds = new RectF();
        private final RectF temp = new RectF();
        private final Path clipPath = new Path();
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public RetroArcadeButton(Context context, String text, int iconResId,
                                int bodyColor, int highlightColor, int shadowColor,
                                Typeface font, float density) {
            super(context);
            this.text = text;
            if (iconResId != 0) {
                try {
                    this.icon = ContextCompat.getDrawable(context, iconResId);
                    if (this.icon != null) {
                        this.icon = this.icon.mutate();
                    }
                } catch (Exception ignored) {}
            }
            this.bodyColor = bodyColor;
            this.highlightColor = highlightColor;
            this.shadowColor = shadowColor;
            this.font = font;
            this.density = density;
            setClickable(true);
            setFocusable(true);
        }

        public void setColors(int body, int hl, int shadow) {
            this.bodyColor = body;
            this.highlightColor = hl;
            this.shadowColor = shadow;
            invalidate();
        }

        public void setText(String text) {
            this.text = text;
            invalidate();
        }

        public void setTextColor(int color) {
            this.textColor = color;
            invalidate();
        }

        public void setTextSizeSp(float sp) {
            this.textSizeSp = sp;
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    isPressed = true;
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    if (isPressed) {
                        isPressed = false;
                        invalidate();
                        performClick();
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    isPressed = false;
                    invalidate();
                    break;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float strokeW = 2.0f * density;
            float r = 6.5f * density;
            // Inset by half stroke width: perfectly fits inside View, NO bleeding or clipping
            bounds.set(strokeW / 2f, strokeW / 2f, w - strokeW / 2f, h - strokeW / 2f);

            // 1. Button body fill
            fillPaint.setColor(bodyColor);
            canvas.drawRoundRect(bounds, r, r, fillPaint);

            // 2. Inner 3D slim bottom bevel & top highlight (smooth rounded clip, NO sharp black corners!)
            clipPath.reset();
            clipPath.addRoundRect(bounds, r, r, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);

            float pressOffsetY = isPressed ? (2.0f * density) : 0f;

            if (isPressed) {
                // When pressed: slim bevel (5%)
                float shadowH = bounds.height() * 0.05f;
                temp.set(bounds.left, bounds.bottom - shadowH, bounds.right, bounds.bottom);
                fillPaint.setColor(shadowColor);
                canvas.drawRect(temp, fillPaint);
            } else {
                // Normal state: slim, subtle bottom shadow bevel (11%) + subtle top highlight (8%)
                float shadowH = bounds.height() * 0.11f;
                temp.set(bounds.left, bounds.bottom - shadowH, bounds.right, bounds.bottom);
                fillPaint.setColor(shadowColor);
                canvas.drawRect(temp, fillPaint);

                float hlH = bounds.height() * 0.08f;
                temp.set(bounds.left, bounds.top, bounds.right, bounds.top + hlH);
                fillPaint.setColor(highlightColor);
                canvas.drawRect(temp, fillPaint);
            }

            canvas.restore();

            // 3. Dark outer pixel border (#171717)
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(strokeW);
            strokePaint.setColor(Color.rgb(23, 23, 23));
            canvas.drawRoundRect(bounds, r, r, strokePaint);

            // 4. Centered Icon + Text group with pressed depression offset
            float scaledPx = textSizeSp * density;
            textPaint.setTypeface(font);
            textPaint.setTextSize(scaledPx);
            textPaint.setColor(textColor);
            textPaint.setTextAlign(Paint.Align.LEFT);

            textStrokePaint.setTypeface(font);
            textStrokePaint.setTextSize(scaledPx);
            textStrokePaint.setStyle(Paint.Style.STROKE);
            textStrokePaint.setStrokeWidth(scaledPx * 0.22f);
            textStrokePaint.setColor(Color.rgb(23, 23, 23));
            textStrokePaint.setTextAlign(Paint.Align.LEFT);

            float textW = (text != null && !text.isEmpty()) ? textPaint.measureText(text) : 0f;
            float iconSize = (icon != null) ? Math.min(bounds.height() * 0.48f, 20 * density) : 0f;
            float gap = (icon != null && textW > 0) ? 5f * density : 0f;
            float totalW = iconSize + gap + textW;

            float startX = bounds.centerX() - (totalW / 2.0f);
            float centerY = bounds.centerY() + pressOffsetY;

            if (icon != null) {
                float iconLeft = startX;
                float iconTop = centerY - (iconSize / 2.0f);
                icon.setBounds((int) iconLeft, (int) iconTop, (int) (iconLeft + iconSize), (int) (iconTop + iconSize));
                icon.setTint(textColor);
                icon.draw(canvas);
            }

            if (textW > 0) {
                float textX = startX + iconSize + gap;
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float textY = centerY - (fm.ascent + fm.descent) / 2.0f;

                if (textColor == Color.WHITE) {
                    canvas.drawText(text, textX, textY, textStrokePaint);
                }
                canvas.drawText(text, textX, textY, textPaint);
            }
        }
    }



    @Override
    public void onShowActivationDialog(Runnable onActivated) {
        runOnUiThread(() -> {
            ActivationManager actMgr = gameView.getActivationManager();
            AudioManager audioMgr = gameView.getAudioManager();
            Typeface pixelFont = getPressStartFont();
            float density = getResources().getDisplayMetrics().density;

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            // Outer Parchment Cabinet Panel (#FFF0C7)
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            int panelPadding = (int) (12 * density);
            panel.setPadding(panelPadding, panelPadding, panelPadding, panelPadding);

            GradientDrawable panelBg = new GradientDrawable();
            panelBg.setColor(Color.rgb(255, 240, 199)); // #FFF0C7 Cream parchment
            panelBg.setCornerRadius(16 * density);
            panelBg.setStroke((int) (3.5f * density), Color.rgb(23, 23, 23)); // #171717 Dark border
            panel.setBackground(panelBg);

            // Header Box (Retro arcade blue with keys)
            FrameLayout headerBox = new FrameLayout(this);
            int headerPadV = (int) (8 * density);
            int headerPadH = (int) (12 * density);
            headerBox.setPadding(headerPadH, headerPadV, headerPadH, headerPadV);

            GradientDrawable headerBg = new GradientDrawable();
            headerBg.setColor(Color.rgb(38, 155, 232)); // #269BE8 Retro Blue
            headerBg.setCornerRadius(10 * density);
            headerBg.setStroke((int) (2.5f * density), Color.rgb(23, 23, 23));
            headerBox.setBackground(headerBg);

            int iconDim = (int) (22 * density);
            ImageView icLeft = new ImageView(this);
            icLeft.setImageResource(R.drawable.ic_key);
            icLeft.setColorFilter(Color.WHITE);
            FrameLayout.LayoutParams lpLeft = new FrameLayout.LayoutParams(iconDim, iconDim);
            lpLeft.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            headerBox.addView(icLeft, lpLeft);

            LinearLayout titleBox = new LinearLayout(this);
            titleBox.setOrientation(LinearLayout.VERTICAL);
            titleBox.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams lpTitleBox = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            lpTitleBox.gravity = Gravity.CENTER;
            titleBox.setLayoutParams(lpTitleBox);

            TextView titleTv = new TextView(this);
            titleTv.setText("GAME ACTIVATION");
            titleTv.setTypeface(pixelFont);
            titleTv.setTextSize(13.5f);
            titleTv.setTextColor(Color.WHITE);
            titleTv.setGravity(Gravity.CENTER);
            titleTv.setShadowLayer(2f * density, 1.5f * density, 2f * density, Color.rgb(23, 23, 23));
            titleBox.addView(titleTv);

            TextView subTv = new TextView(this);
            subTv.setText("Unlock Full Character Roster");
            subTv.setTypeface(pixelFont);
            subTv.setTextSize(7.5f);
            subTv.setTextColor(Color.rgb(18, 90, 145));
            subTv.setGravity(Gravity.CENTER);
            subTv.setPadding(0, (int) (2 * density), 0, 0);
            titleBox.addView(subTv);

            headerBox.addView(titleBox);

            ImageView icRight = new ImageView(this);
            icRight.setImageResource(R.drawable.ic_key);
            icRight.setColorFilter(Color.WHITE);
            FrameLayout.LayoutParams lpRight = new FrameLayout.LayoutParams(iconDim, iconDim);
            lpRight.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            headerBox.addView(icRight, lpRight);

            panel.addView(headerBox);

            // Middle Card for Code Entry
            LinearLayout card = createSettingsCard(density);
            LinearLayout.LayoutParams lpCard = (LinearLayout.LayoutParams) card.getLayoutParams();
            lpCard.setMargins(0, (int) (8 * density), 0, (int) (8 * density));
            card.setLayoutParams(lpCard);

            card.addView(createSectionHeaderView(R.drawable.ic_key, "ENTER ACTIVATION CODE", pixelFont, density));

            TextView descTv = new TextView(this);
            descTv.setText("Enter your secret key to unlock your character face & sync scores to the worldwide leaderboard!");
            descTv.setTypeface(pixelFont);
            descTv.setTextSize(8f);
            descTv.setTextColor(Color.rgb(90, 80, 70));
            descTv.setLineSpacing(0f, 1.25f);
            descTv.setPadding(0, 0, 0, (int) (8 * density));
            card.addView(descTv);

            EditText input = new EditText(this);
            input.setHint("ENTER CODE HERE");
            input.setHintTextColor(Color.rgb(170, 160, 145));
            input.setTextColor(Color.rgb(23, 23, 23));
            input.setTextSize(10f);
            input.setTypeface(pixelFont);
            input.setSingleLine(true);
            input.setGravity(Gravity.CENTER);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setColor(Color.rgb(255, 255, 255));
            inputBg.setCornerRadius(8 * density);
            inputBg.setStroke((int) (2f * density), Color.rgb(23, 23, 23));
            input.setBackground(inputBg);
            input.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
            card.addView(input);

            TextView errorText = new TextView(this);
            errorText.setTextSize(8f);
            errorText.setTypeface(pixelFont);
            errorText.setTextColor(Color.rgb(211, 47, 47));
            errorText.setGravity(Gravity.CENTER);
            errorText.setPadding(0, (int) (6 * density), 0, 0);
            errorText.setVisibility(View.GONE);
            card.addView(errorText);

            panel.addView(card);

            // Unlock & Play button (Retro green)
            RetroArcadeButton unlockBtn = new RetroArcadeButton(this, "UNLOCK & PLAY", R.drawable.ic_key,
                    Color.rgb(44, 203, 99), Color.rgb(112, 229, 141), Color.rgb(22, 115, 58),
                    pixelFont, density);
            unlockBtn.setTextSizeSp(10.5f);
            LinearLayout.LayoutParams lpUnlock = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (38 * density));
            unlockBtn.setLayoutParams(lpUnlock);
            panel.addView(unlockBtn);

            // Close / Cancel button (Retro red)
            RetroArcadeButton closeBtn = new RetroArcadeButton(this, "CANCEL", R.drawable.ic_close,
                    Color.rgb(232, 75, 75), Color.rgb(247, 108, 108), Color.rgb(143, 41, 41),
                    pixelFont, density);
            closeBtn.setTextSizeSp(10f);
            LinearLayout.LayoutParams lpClose = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (35 * density));
            lpClose.setMargins(0, (int) (6 * density), 0, 0);
            closeBtn.setLayoutParams(lpClose);
            panel.addView(closeBtn);

            builder.setView(panel);

            AlertDialog dialog = builder.create();
            dialog.show();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int maxDialogWidth = (int) (430 * density);
                int dialogWidth = Math.min(maxDialogWidth, (int) (screenWidth * 0.88f));
                dialog.getWindow().setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
            }

            closeBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
            });

            unlockBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                String code = input.getText().toString().trim();
                if (code.isEmpty()) return;

                if (actMgr.activate(code)) {
                    onActivationComplete(dialog, actMgr, code, onActivated, null);
                } else {
                    String hash = actMgr.computeSha256(code);
                    unlockBtn.setEnabled(false);
                    errorText.setText("Verifying code with server...");
                    errorText.setVisibility(View.VISIBLE);

                    leaderboardManager.fetchServerProfile(hash, code, (name, serverScore, charUsed, assetUrl) -> {
                        unlockBtn.setEnabled(true);
                        if (name != null && !name.isEmpty()) {
                            actMgr.activateWithOnlineProfile(code, hash, name, charUsed);
                            onActivationComplete(dialog, actMgr, code, onActivated, name);
                        } else {
                            errorText.setText("Invalid activation code! Try again.");
                            errorText.setVisibility(View.VISIBLE);
                        }
                    });
                }
            });
        });
    }

    private void onActivationComplete(AlertDialog dialog, ActivationManager actMgr, String code, Runnable onActivated, String onlineName) {
        String welcome = (onlineName != null && !onlineName.isEmpty())
                ? "Activated! Welcome " + onlineName + "!"
                : "Activated! Welcome to Urukku Manush!";
        Toast.makeText(this, welcome, Toast.LENGTH_SHORT).show();
        gameView.reloadPlayerHead();
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }

        ScoreManager scoreMgr = new ScoreManager(this);
        leaderboardManager.syncScoresWithServer(
                scoreMgr.getHighScore(),
                actMgr.getActiveHash(),
                actMgr.getActiveHeadIndex(),
                actMgr.getActiveHead(),
                code,
                syncedScore -> {
                    if (syncedScore > scoreMgr.getHighScore()) {
                        scoreMgr.setHighScore(syncedScore);
                        Toast.makeText(this, "Restored high score: " + syncedScore, Toast.LENGTH_SHORT).show();
                    }
                    gameView.reloadPlayerHead();
                }
        );

        if (onActivated != null) {
            onActivated.run();
        }
    }

    @Override
    public void onShowOptionsDialog() {
        runOnUiThread(() -> {
            AudioManager audioMgr = gameView.getAudioManager();
            ActivationManager actMgr = gameView.getActivationManager();
            ScoreManager scoreMgr = gameView.getScoreManager();
            Typeface pixelFont = getPressStartFont();
            float density = getResources().getDisplayMetrics().density;

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            // Outer Parchment Cabinet Panel (#FFF0C7)
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            int panelPadding = (int) (12 * density);
            panel.setPadding(panelPadding, panelPadding, panelPadding, panelPadding);

            GradientDrawable panelBg = new GradientDrawable();
            panelBg.setColor(Color.rgb(255, 240, 199)); // #FFF0C7 Cream parchment
            panelBg.setCornerRadius(16 * density);
            panelBg.setStroke((int) (3.5f * density), Color.rgb(23, 23, 23)); // #171717 Dark border
            panel.setBackground(panelBg);

            // Header Box (Retro arcade green with gears)
            FrameLayout headerBox = new FrameLayout(this);
            int headerPadV = (int) (8 * density);
            int headerPadH = (int) (12 * density);
            headerBox.setPadding(headerPadH, headerPadV, headerPadH, headerPadV);

            GradientDrawable headerBg = new GradientDrawable();
            headerBg.setColor(Color.rgb(79, 174, 79)); // #4FAE4F Retro Green
            headerBg.setCornerRadius(10 * density);
            headerBg.setStroke((int) (2.5f * density), Color.rgb(23, 23, 23));
            headerBox.setBackground(headerBg);

            // Left Gear
            ImageView leftGear = new ImageView(this);
            leftGear.setImageResource(R.drawable.ic_gear);
            leftGear.setColorFilter(Color.WHITE);
            int gearDim = (int) (24 * density);
            FrameLayout.LayoutParams lpLeftGear = new FrameLayout.LayoutParams(gearDim, gearDim);
            lpLeftGear.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            leftGear.setLayoutParams(lpLeftGear);
            headerBox.addView(leftGear);

            // Center Title & Version Subtitle
            LinearLayout titleBox = new LinearLayout(this);
            titleBox.setOrientation(LinearLayout.VERTICAL);
            titleBox.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams lpTitleBox = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            lpTitleBox.gravity = Gravity.CENTER;
            titleBox.setLayoutParams(lpTitleBox);

            TextView titleTv = new TextView(this);
            titleTv.setText("GAME SETTINGS");
            titleTv.setTypeface(pixelFont);
            titleTv.setTextSize(13.5f);
            titleTv.setTextColor(Color.WHITE);
            titleTv.setGravity(Gravity.CENTER);
            titleTv.setShadowLayer(2f * density, 1.5f * density, 2f * density, Color.rgb(23, 23, 23));
            titleBox.addView(titleTv);

            TextView subTv = new TextView(this);
            subTv.setText("Urukku Manush v" + appUpdater.getCurrentVersion());
            subTv.setTypeface(pixelFont);
            subTv.setTextSize(7.5f);
            subTv.setTextColor(Color.rgb(30, 77, 31)); // #1E4D1F
            subTv.setGravity(Gravity.CENTER);
            subTv.setPadding(0, (int) (2 * density), 0, 0);
            titleBox.addView(subTv);

            headerBox.addView(titleBox);

            // Right Gear
            ImageView rightGear = new ImageView(this);
            rightGear.setImageResource(R.drawable.ic_gear);
            rightGear.setColorFilter(Color.WHITE);
            FrameLayout.LayoutParams lpRightGear = new FrameLayout.LayoutParams(gearDim, gearDim);
            lpRightGear.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            rightGear.setLayoutParams(lpRightGear);
            headerBox.addView(rightGear);

            panel.addView(headerBox);

            // Middle Scrollable Container for sections
            ScrollView scrollContent = new ScrollView(this);
            scrollContent.setVerticalScrollBarEnabled(false);
            LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            lpScroll.setMargins(0, (int) (8 * density), 0, (int) (8 * density));
            scrollContent.setLayoutParams(lpScroll);

            LinearLayout cardsLayout = new LinearLayout(this);
            cardsLayout.setOrientation(LinearLayout.VERTICAL);

            // SECTION 1: AUDIO
            LinearLayout audioCard = createSettingsCard(density);

            // Section Header: Speaker icon + AUDIO
            audioCard.addView(createSectionHeaderView(R.drawable.ic_speaker, "AUDIO", pixelFont, density));

            // SFX Row
            LinearLayout sfxRow = new LinearLayout(this);
            sfxRow.setOrientation(LinearLayout.HORIZONTAL);
            sfxRow.setGravity(Gravity.CENTER_VERTICAL);
            sfxRow.setPadding(0, (int) (2 * density), 0, (int) (3 * density));

            ImageView sfxIcon = new ImageView(this);
            sfxIcon.setImageResource(R.drawable.ic_music);
            sfxIcon.setColorFilter(Color.rgb(43, 36, 27)); // #2B241B
            int iconNoteDim = (int) (16 * density);
            LinearLayout.LayoutParams lpNote1 = new LinearLayout.LayoutParams(iconNoteDim, iconNoteDim);
            lpNote1.setMargins(0, 0, (int) (6 * density), 0);
            sfxIcon.setLayoutParams(lpNote1);
            sfxRow.addView(sfxIcon);

            TextView sfxLabel = new TextView(this);
            sfxLabel.setText("SFX");
            sfxLabel.setTypeface(pixelFont);
            sfxLabel.setTextSize(10f);
            sfxLabel.setTextColor(Color.rgb(43, 36, 27));
            sfxLabel.setIncludeFontPadding(false);
            LinearLayout.LayoutParams lpSfxLbl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            sfxLabel.setLayoutParams(lpSfxLbl);
            sfxRow.addView(sfxLabel);

            boolean isSfx = audioMgr.isSfxEnabled();
            RetroArcadeButton btnSfx = new RetroArcadeButton(this, isSfx ? "ON" : "OFF", 0,
                    isSfx ? Color.rgb(44, 203, 99) : Color.rgb(107, 114, 128),
                    isSfx ? Color.rgb(112, 229, 141) : Color.rgb(156, 163, 175),
                    isSfx ? Color.rgb(22, 115, 58) : Color.rgb(55, 65, 81),
                    pixelFont, density);
            btnSfx.setTextSizeSp(9.5f);
            btnSfx.setLayoutParams(new LinearLayout.LayoutParams((int) (64 * density), (int) (32 * density)));
            btnSfx.setOnClickListener(v -> {
                boolean now = !audioMgr.isSfxEnabled();
                audioMgr.setSfxEnabled(now);
                if (now) audioMgr.playClickSound();
                btnSfx.setText(now ? "ON" : "OFF");
                btnSfx.setColors(
                        now ? Color.rgb(44, 203, 99) : Color.rgb(107, 114, 128),
                        now ? Color.rgb(112, 229, 141) : Color.rgb(156, 163, 175),
                        now ? Color.rgb(22, 115, 58) : Color.rgb(55, 65, 81)
                );
            });
            sfxRow.addView(btnSfx);
            audioCard.addView(sfxRow);

            // MUSIC Row
            LinearLayout musicRow = new LinearLayout(this);
            musicRow.setOrientation(LinearLayout.HORIZONTAL);
            musicRow.setGravity(Gravity.CENTER_VERTICAL);
            musicRow.setPadding(0, (int) (3 * density), 0, (int) (2 * density));

            ImageView musicIcon = new ImageView(this);
            musicIcon.setImageResource(R.drawable.ic_music);
            musicIcon.setColorFilter(Color.rgb(43, 36, 27));
            LinearLayout.LayoutParams lpNote2 = new LinearLayout.LayoutParams(iconNoteDim, iconNoteDim);
            lpNote2.setMargins(0, 0, (int) (6 * density), 0);
            musicIcon.setLayoutParams(lpNote2);
            musicRow.addView(musicIcon);

            TextView musicLabel = new TextView(this);
            musicLabel.setText("MUSIC");
            musicLabel.setTypeface(pixelFont);
            musicLabel.setTextSize(10f);
            musicLabel.setTextColor(Color.rgb(43, 36, 27));
            musicLabel.setIncludeFontPadding(false);
            LinearLayout.LayoutParams lpMusicLbl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            musicLabel.setLayoutParams(lpMusicLbl);
            musicRow.addView(musicLabel);

            boolean isBgm = audioMgr.isBgmEnabled();
            RetroArcadeButton btnBgm = new RetroArcadeButton(this, isBgm ? "ON" : "OFF", 0,
                    isBgm ? Color.rgb(44, 203, 99) : Color.rgb(107, 114, 128),
                    isBgm ? Color.rgb(112, 229, 141) : Color.rgb(156, 163, 175),
                    isBgm ? Color.rgb(22, 115, 58) : Color.rgb(55, 65, 81),
                    pixelFont, density);
            btnBgm.setTextSizeSp(9.5f);
            btnBgm.setLayoutParams(new LinearLayout.LayoutParams((int) (64 * density), (int) (32 * density)));
            btnBgm.setOnClickListener(v -> {
                boolean now = !audioMgr.isBgmEnabled();
                audioMgr.setBgmEnabled(now);
                audioMgr.playClickSound();
                btnBgm.setText(now ? "ON" : "OFF");
                btnBgm.setColors(
                        now ? Color.rgb(44, 203, 99) : Color.rgb(107, 114, 128),
                        now ? Color.rgb(112, 229, 141) : Color.rgb(156, 163, 175),
                        now ? Color.rgb(22, 115, 58) : Color.rgb(55, 65, 81)
                );
            });
            musicRow.addView(btnBgm);
            audioCard.addView(musicRow);

            cardsLayout.addView(audioCard);

            // SECTION 2: GAMEPLAY
            LinearLayout gameplayCard = createSettingsCard(density);
            gameplayCard.addView(createSectionHeaderView(R.drawable.ic_trophy, "GAMEPLAY", pixelFont, density));

            LinearLayout scoreMeterRow = new LinearLayout(this);
            scoreMeterRow.setOrientation(LinearLayout.HORIZONTAL);
            scoreMeterRow.setGravity(Gravity.CENTER_VERTICAL);
            scoreMeterRow.setPadding(0, (int) (2 * density), 0, (int) (2 * density));

            ImageView meterIcon = new ImageView(this);
            meterIcon.setImageResource(R.drawable.ic_trophy);
            meterIcon.setColorFilter(Color.rgb(43, 36, 27)); // #2B241B
            int iconMeterDim = (int) (16 * density);
            LinearLayout.LayoutParams lpMeterIc = new LinearLayout.LayoutParams(iconMeterDim, iconMeterDim);
            lpMeterIc.setMargins(0, 0, (int) (6 * density), 0);
            meterIcon.setLayoutParams(lpMeterIc);
            scoreMeterRow.addView(meterIcon);

            TextView meterLabel = new TextView(this);
            meterLabel.setText("SCORE METER");
            meterLabel.setTypeface(pixelFont);
            meterLabel.setTextSize(10f);
            meterLabel.setTextColor(Color.rgb(43, 36, 27));
            meterLabel.setIncludeFontPadding(false);
            LinearLayout.LayoutParams lpMeterLbl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            meterLabel.setLayoutParams(lpMeterLbl);
            scoreMeterRow.addView(meterLabel);

            boolean isMeter = scoreMgr.isScoreMeterEnabled();
            RetroArcadeButton btnMeter = new RetroArcadeButton(this, isMeter ? "ON" : "OFF", 0,
                    isMeter ? Color.rgb(44, 203, 99) : Color.rgb(107, 114, 128),
                    isMeter ? Color.rgb(112, 229, 141) : Color.rgb(156, 163, 175),
                    isMeter ? Color.rgb(22, 115, 58) : Color.rgb(55, 65, 81),
                    pixelFont, density);
            btnMeter.setTextSizeSp(9.5f);
            btnMeter.setLayoutParams(new LinearLayout.LayoutParams((int) (64 * density), (int) (32 * density)));
            btnMeter.setOnClickListener(v -> {
                boolean now = !scoreMgr.isScoreMeterEnabled();
                scoreMgr.setScoreMeterEnabled(now);
                if (now) audioMgr.playClickSound();
                btnMeter.setText(now ? "ON" : "OFF");
                btnMeter.setColors(
                        now ? Color.rgb(44, 203, 99) : Color.rgb(107, 114, 128),
                        now ? Color.rgb(112, 229, 141) : Color.rgb(156, 163, 175),
                        now ? Color.rgb(22, 115, 58) : Color.rgb(55, 65, 81)
                );
            });
            scoreMeterRow.addView(btnMeter);
            gameplayCard.addView(scoreMeterRow);

            cardsLayout.addView(gameplayCard);

            // SECTION 3: ONLINE
            LinearLayout onlineCard = createSettingsCard(density);
            onlineCard.addView(createSectionHeaderView(R.drawable.ic_online, "ONLINE", pixelFont, density));

            RetroArcadeButton btnLead = new RetroArcadeButton(this, "LEADERBOARD", R.drawable.ic_leaderboard,
                    Color.rgb(244, 181, 27), Color.rgb(255, 216, 77), Color.rgb(154, 106, 0),
                    pixelFont, density);
            btnLead.setTextSizeSp(10f);
            LinearLayout.LayoutParams lpBtnLead = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (35 * density));
            btnLead.setLayoutParams(lpBtnLead);
            onlineCard.addView(btnLead);

            cardsLayout.addView(onlineCard);

            // SECTION 3: SYSTEM
            LinearLayout sysCard = createSettingsCard(density);
            sysCard.addView(createSectionHeaderView(R.drawable.ic_gear, "SYSTEM", pixelFont, density));

            RetroArcadeButton btnUpdates = new RetroArcadeButton(this, "CHECK FOR UPDATES", R.drawable.ic_refresh,
                    Color.rgb(44, 203, 99), Color.rgb(112, 229, 141), Color.rgb(22, 115, 58),
                    pixelFont, density);
            btnUpdates.setTextSizeSp(9.5f);
            LinearLayout.LayoutParams lpBtnUpdates = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (35 * density));
            btnUpdates.setLayoutParams(lpBtnUpdates);
            sysCard.addView(btnUpdates);

            View gapSys = new View(this);
            gapSys.setLayoutParams(new LinearLayout.LayoutParams(1, (int) (4 * density)));
            sysCard.addView(gapSys);

            RetroArcadeButton btnActv = new RetroArcadeButton(this,
                    actMgr.isActivated() ? "ENTER ACTIVATION CODE" : "ACTIVATE GAME",
                    R.drawable.ic_key,
                    Color.rgb(38, 155, 232), Color.rgb(100, 198, 255), Color.rgb(18, 90, 145),
                    pixelFont, density);
            btnActv.setTextSizeSp(9f);
            LinearLayout.LayoutParams lpBtnActv = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (35 * density));
            btnActv.setLayoutParams(lpBtnActv);
            sysCard.addView(btnActv);

            cardsLayout.addView(sysCard);

            scrollContent.addView(cardsLayout);
            panel.addView(scrollContent);

            // CLOSE BUTTON (Red #E84B4B)
            RetroArcadeButton btnClose = new RetroArcadeButton(this, "CLOSE", R.drawable.ic_pixel_close,
                    Color.rgb(232, 75, 75), Color.rgb(247, 108, 108), Color.rgb(143, 41, 41),
                    pixelFont, density);
            btnClose.setTextSizeSp(11f);
            LinearLayout.LayoutParams lpClose = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (38 * density));
            btnClose.setLayoutParams(lpClose);
            panel.addView(btnClose);

            builder.setView(panel);

            AlertDialog dialog = builder.create();
            dialog.show();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int maxDialogWidth = (int) (430 * density);
                int dialogWidth = Math.min(maxDialogWidth, (int) (screenWidth * 0.88f));
                dialog.getWindow().setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
            }

            btnLead.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
                onShowLeaderboardDialog();
            });

            btnUpdates.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
                startUpdateCheck();
            });

            btnActv.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
                onShowActivationDialog(null);
            });

            btnClose.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
            });
        });
    }

    private LinearLayout createSettingsCard(float density) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int padH = (int) (8 * density);
        int padV = (int) (6 * density);
        card.setPadding(padH, padV, padH, padV);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.rgb(253, 243, 218)); // #FDF3DA
        cardBg.setCornerRadius(9 * density);
        cardBg.setStroke((int) (1.8f * density), Color.rgb(216, 185, 106)); // #D8B96A
        card.setBackground(cardBg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int) (5 * density));
        card.setLayoutParams(lp);
        return card;
    }

    private View createSectionHeaderView(int iconRes, String title, Typeface font, float density) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, (int) (5 * density));

        if (iconRes != 0) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(iconRes);
            iv.setColorFilter(Color.rgb(43, 36, 27)); // #2B241B
            int dim = (int) (16 * density);
            LinearLayout.LayoutParams lpIv = new LinearLayout.LayoutParams(dim, dim);
            lpIv.setMargins(0, 0, (int) (6 * density), 0);
            iv.setLayoutParams(lpIv);
            row.addView(iv);
        }

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTypeface(font);
        tv.setTextSize(10f);
        tv.setTextColor(Color.rgb(43, 36, 27));
        tv.setIncludeFontPadding(false);
        row.addView(tv);

        return row;
    }

    private String cleanVersionTag(String tag) {
        if (tag == null) return "";
        tag = tag.trim();
        if (tag.toLowerCase().startsWith("v")) {
            return tag;
        }
        return "v" + tag;
    }

    private void startUpdateCheck() {
        Typeface pixelFont = getPressStartFont();
        float density = getResources().getDisplayMetrics().density;
        AudioManager audioMgr = gameView != null ? gameView.getAudioManager() : null;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Outer Parchment Cabinet Panel (#FFF0C7)
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * density);
        panel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.rgb(255, 240, 199)); // #FFF0C7 Cream parchment
        panelBg.setCornerRadius(16 * density);
        panelBg.setStroke((int) (3.5f * density), Color.rgb(23, 23, 23)); // #171717
        panel.setBackground(panelBg);

        // Header Box (Retro Green with refresh icons)
        FrameLayout headerBox = new FrameLayout(this);
        int headerPadV = (int) (8 * density);
        int headerPadH = (int) (12 * density);
        headerBox.setPadding(headerPadH, headerPadV, headerPadH, headerPadV);

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(Color.rgb(79, 174, 79)); // #4FAE4F Retro Green
        headerBg.setCornerRadius(10 * density);
        headerBg.setStroke((int) (2.5f * density), Color.rgb(23, 23, 23));
        headerBox.setBackground(headerBg);

        int iconDim = (int) (22 * density);
        ImageView icLeft = new ImageView(this);
        icLeft.setImageResource(R.drawable.ic_refresh);
        icLeft.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams lpLeft = new FrameLayout.LayoutParams(iconDim, iconDim);
        lpLeft.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        headerBox.addView(icLeft, lpLeft);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpTitleBox = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lpTitleBox.gravity = Gravity.CENTER;
        titleBox.setLayoutParams(lpTitleBox);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("UPDATE WIZARD");
        tvTitle.setTypeface(pixelFont);
        tvTitle.setTextSize(13.5f);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setShadowLayer(2f * density, 1.5f * density, 2f * density, Color.rgb(23, 23, 23));
        titleBox.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText("GitHub Releases Sync");
        tvSub.setTypeface(pixelFont);
        tvSub.setTextSize(7.5f);
        tvSub.setTextColor(Color.rgb(30, 77, 31));
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setPadding(0, (int) (2 * density), 0, 0);
        titleBox.addView(tvSub);

        headerBox.addView(titleBox);

        ImageView icRight = new ImageView(this);
        icRight.setImageResource(R.drawable.ic_refresh);
        icRight.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams lpRight = new FrameLayout.LayoutParams(iconDim, iconDim);
        lpRight.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        headerBox.addView(icRight, lpRight);

        panel.addView(headerBox);

        View gapTop = new View(this);
        gapTop.setLayoutParams(new LinearLayout.LayoutParams(1, (int) (8 * density)));
        panel.addView(gapTop);

        // Content Card
        LinearLayout contentCard = createSettingsCard(density);
        contentCard.setGravity(Gravity.CENTER_HORIZONTAL);
        contentCard.setPadding((int) (14 * density), (int) (16 * density), (int) (14 * density), (int) (16 * density));

        TextView tvStatus = new TextView(this);
        tvStatus.setText("Checking GitHub for the latest release...");
        tvStatus.setTextSize(9f);
        tvStatus.setTypeface(pixelFont);
        tvStatus.setTextColor(Color.rgb(43, 36, 27));
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setLineSpacing(3 * density, 1f);
        contentCard.addView(tvStatus);

        ProgressBar spinner = new ProgressBar(this);
        LinearLayout.LayoutParams lpSpin = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSpin.setMargins(0, (int) (12 * density), 0, (int) (4 * density));
        spinner.setLayoutParams(lpSpin);
        contentCard.addView(spinner);

        panel.addView(contentCard);

        RetroArcadeButton closeBtn = new RetroArcadeButton(this, "CLOSE", R.drawable.ic_pixel_close,
                Color.rgb(232, 75, 75), Color.rgb(247, 108, 108), Color.rgb(143, 41, 41),
                pixelFont, density);
        closeBtn.setTextSizeSp(10.5f);
        LinearLayout.LayoutParams lpCls = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (36 * density));
        lpCls.setMargins(0, (int) (4 * density), 0, 0);
        closeBtn.setLayoutParams(lpCls);
        panel.addView(closeBtn);

        builder.setView(panel);
        AlertDialog dialog = builder.create();
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int maxDialogWidth = (int) (430 * density);
            int dialogWidth = Math.min(maxDialogWidth, (int) (screenWidth * 0.88f));
            dialog.getWindow().setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        closeBtn.setOnClickListener(v -> {
            if (audioMgr != null) audioMgr.playClickSound();
            dialog.dismiss();
        });

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
                tvStatus.setTextColor(Color.rgb(192, 57, 43));

                panel.removeView(closeBtn);

                LinearLayout btnRow = new LinearLayout(MainActivity.this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setPadding(0, (int) (4 * density), 0, 0);

                RetroArcadeButton retryBtn = new RetroArcadeButton(MainActivity.this, "RETRY", R.drawable.ic_refresh,
                        Color.rgb(44, 203, 99), Color.rgb(112, 229, 141), Color.rgb(22, 115, 58),
                        pixelFont, density);
                retryBtn.setTextSizeSp(9f);
                LinearLayout.LayoutParams lpRet = new LinearLayout.LayoutParams(0, (int) (36 * density), 1f);
                lpRet.setMargins(0, 0, (int) (4 * density), 0);
                retryBtn.setLayoutParams(lpRet);
                retryBtn.setOnClickListener(v -> {
                    if (audioMgr != null) audioMgr.playClickSound();
                    dialog.dismiss();
                    startUpdateCheck();
                });
                btnRow.addView(retryBtn);

                RetroArcadeButton webBtn = new RetroArcadeButton(MainActivity.this, "WEB", R.drawable.ic_globe,
                        Color.rgb(19, 191, 208), Color.rgb(82, 224, 237), Color.rgb(11, 126, 137),
                        pixelFont, density);
                webBtn.setTextSizeSp(9f);
                LinearLayout.LayoutParams lpW = new LinearLayout.LayoutParams(0, (int) (36 * density), 1f);
                lpW.setMargins((int) (4 * density), 0, (int) (4 * density), 0);
                webBtn.setLayoutParams(lpW);
                webBtn.setOnClickListener(v -> {
                    if (audioMgr != null) audioMgr.playClickSound();
                    openUrlSafely(AppUpdater.GITHUB_RELEASES_WEB);
                });
                btnRow.addView(webBtn);

                RetroArcadeButton errCloseBtn = new RetroArcadeButton(MainActivity.this, "CLOSE", R.drawable.ic_pixel_close,
                        Color.rgb(232, 75, 75), Color.rgb(247, 108, 108), Color.rgb(143, 41, 41),
                        pixelFont, density);
                errCloseBtn.setTextSizeSp(9f);
                LinearLayout.LayoutParams lpErrCls = new LinearLayout.LayoutParams(0, (int) (36 * density), 1f);
                lpErrCls.setMargins((int) (4 * density), 0, 0, 0);
                errCloseBtn.setLayoutParams(lpErrCls);
                errCloseBtn.setOnClickListener(v -> {
                    if (audioMgr != null) audioMgr.playClickSound();
                    dialog.dismiss();
                });
                btnRow.addView(errCloseBtn);

                panel.addView(btnRow);
            }
        });
    }

    private void showUpdateCenterDialog(AppUpdater.ReleaseInfo info) {
        Typeface pixelFont = getPressStartFont();
        float density = getResources().getDisplayMetrics().density;
        AudioManager audioMgr = gameView != null ? gameView.getAudioManager() : null;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Outer Parchment Cabinet Panel (#FFF0C7)
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * density);
        panel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.rgb(255, 240, 199)); // #FFF0C7 Cream parchment
        panelBg.setCornerRadius(16 * density);
        panelBg.setStroke((int) (3.5f * density), Color.rgb(23, 23, 23)); // #171717 Dark border
        panel.setBackground(panelBg);

        // Header Box (Retro arcade green with refresh icons)
        FrameLayout headerBox = new FrameLayout(this);
        int headerPadV = (int) (8 * density);
        int headerPadH = (int) (12 * density);
        headerBox.setPadding(headerPadH, headerPadV, headerPadH, headerPadV);

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(Color.rgb(79, 174, 79)); // #4FAE4F Retro Green
        headerBg.setCornerRadius(10 * density);
        headerBg.setStroke((int) (2.5f * density), Color.rgb(23, 23, 23));
        headerBox.setBackground(headerBg);

        int iconDim = (int) (22 * density);
        ImageView icLeft = new ImageView(this);
        icLeft.setImageResource(R.drawable.ic_refresh);
        icLeft.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams lpLeft = new FrameLayout.LayoutParams(iconDim, iconDim);
        lpLeft.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        headerBox.addView(icLeft, lpLeft);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpTitleBox = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lpTitleBox.gravity = Gravity.CENTER;
        titleBox.setLayoutParams(lpTitleBox);

        TextView titleTv = new TextView(this);
        titleTv.setText("UPDATE WIZARD");
        titleTv.setTypeface(pixelFont);
        titleTv.setTextSize(13.5f);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setGravity(Gravity.CENTER);
        titleTv.setShadowLayer(2f * density, 1.5f * density, 2f * density, Color.rgb(23, 23, 23));
        titleBox.addView(titleTv);

        TextView subTv = new TextView(this);
        subTv.setText("Release & Patch Manager");
        subTv.setTypeface(pixelFont);
        subTv.setTextSize(7.5f);
        subTv.setTextColor(Color.rgb(30, 77, 31));
        subTv.setGravity(Gravity.CENTER);
        subTv.setPadding(0, (int) (2 * density), 0, 0);
        titleBox.addView(subTv);

        headerBox.addView(titleBox);

        ImageView icRight = new ImageView(this);
        icRight.setImageResource(R.drawable.ic_refresh);
        icRight.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams lpRight = new FrameLayout.LayoutParams(iconDim, iconDim);
        lpRight.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        headerBox.addView(icRight, lpRight);

        panel.addView(headerBox);

        // Middle Container for cards
        LinearLayout cardsLayout = new LinearLayout(this);
        cardsLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpCards = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCards.setMargins(0, (int) (6 * density), 0, (int) (4 * density));
        cardsLayout.setLayoutParams(lpCards);

        boolean isDownloaded = appUpdater.isApkAlreadyDownloaded(info);
        String displayRemoteTag = cleanVersionTag(info.tagName);
        String displayCurrentTag = cleanVersionTag(appUpdater.getCurrentVersion());

        // CARD 1: RELEASE INFO & STATUS
        LinearLayout infoCard = createSettingsCard(density);
        infoCard.addView(createSectionHeaderView(R.drawable.ic_refresh, "RELEASE INFO", pixelFont, density));

        // Status Badge
        TextView badge = new TextView(this);
        if (isDownloaded) {
            badge.setText("PACKAGE READY TO INSTALL");
            badge.setTextColor(Color.rgb(22, 115, 58)); // Dark green
        } else if (info.isUpdateAvailable) {
            badge.setText("NEW VERSION AVAILABLE: " + displayRemoteTag);
            badge.setTextColor(Color.rgb(22, 115, 58));
        } else {
            badge.setText("YOU ARE ON THE LATEST VERSION");
            badge.setTextColor(Color.rgb(18, 90, 145)); // Blue
        }
        badge.setTextSize(9f);
        badge.setTypeface(pixelFont);
        badge.setPadding(0, 0, 0, (int) (4 * density));
        infoCard.addView(badge);

        TextView relName = new TextView(this);
        relName.setText(info.releaseTitle != null && !info.releaseTitle.isEmpty() ? info.releaseTitle : ("Urukku Manush " + displayRemoteTag));
        relName.setTextSize(9.5f);
        relName.setTypeface(pixelFont);
        relName.setTextColor(Color.rgb(43, 36, 27));
        relName.setPadding(0, (int) (1 * density), 0, (int) (3 * density));
        infoCard.addView(relName);

        TextView verCompare = new TextView(this);
        verCompare.setText("Installed: " + displayCurrentTag + "   ->   Available: " + displayRemoteTag);
        verCompare.setTextSize(8.5f);
        verCompare.setTypeface(pixelFont);
        verCompare.setTextColor(Color.rgb(80, 70, 60));
        verCompare.setPadding(0, 0, 0, (int) (3 * density));
        infoCard.addView(verCompare);

        TextView pkgSize = new TextView(this);
        pkgSize.setText("Package Size: " + info.getFormattedSize());
        pkgSize.setTextSize(8.5f);
        pkgSize.setTypeface(pixelFont);
        pkgSize.setTextColor(Color.rgb(80, 70, 60));
        infoCard.addView(pkgSize);

        cardsLayout.addView(infoCard);

        // CARD 2: PATCH NOTES & DETAILS (Scrollable text box)
        LinearLayout notesCard = createSettingsCard(density);
        notesCard.addView(createSectionHeaderView(R.drawable.ic_credits, "PATCH NOTES & DETAILS", pixelFont, density));

        ScrollView notesScroll = new ScrollView(this);
        notesScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (115 * density)));
        GradientDrawable notesBg = new GradientDrawable();
        notesBg.setColor(Color.rgb(255, 248, 235)); // #FFF8EB lighter cream
        notesBg.setCornerRadius(6 * density);
        notesBg.setStroke((int) (1.8f * density), Color.rgb(216, 185, 106));
        notesScroll.setBackground(notesBg);
        int nPad = (int) (8 * density);
        notesScroll.setPadding(nPad, nPad, nPad, nPad);

        // Allow smooth vertical scrolling inside this box without parent intercepting
        notesScroll.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        TextView notesText = new TextView(this);
        String changelogStr = (info.changelog != null && !info.changelog.trim().isEmpty())
                ? info.changelog.trim()
                : "• Performance optimizations and bug fixes.\n• High refresh rate display support.\n• Security and leaderboard updates.";
        notesText.setText(changelogStr);
        notesText.setTextSize(8.5f);
        notesText.setTypeface(pixelFont);
        notesText.setTextColor(Color.rgb(43, 36, 27));
        notesText.setLineSpacing(3 * density, 1f);
        notesScroll.addView(notesText);

        notesCard.addView(notesScroll);
        cardsLayout.addView(notesCard);

        // CARD 3: LIVE PROGRESS CONTAINER (Hidden until download begins)
        LinearLayout progressCard = createSettingsCard(density);
        progressCard.setVisibility(View.GONE);

        TextView progressLabel = new TextView(this);
        progressLabel.setText("Downloading update package...");
        progressLabel.setTextSize(8.5f);
        progressLabel.setTypeface(pixelFont);
        progressLabel.setTextColor(Color.rgb(18, 90, 145));
        progressCard.addView(progressLabel);

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(ColorStateList.valueOf(Color.rgb(44, 203, 99)));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(215, 200, 170)));
        LinearLayout.LayoutParams lpProgress = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (18 * density));
        lpProgress.setMargins(0, (int) (6 * density), 0, (int) (4 * density));
        progressBar.setLayoutParams(lpProgress);
        progressCard.addView(progressBar);

        TextView progressStats = new TextView(this);
        progressStats.setText("0% • 0 MB / " + info.getFormattedSize());
        progressStats.setTextSize(8f);
        progressStats.setTypeface(pixelFont);
        progressStats.setTextColor(Color.rgb(80, 70, 60));
        progressCard.addView(progressStats);

        // Controls row (Pause & Cancel)
        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setPadding(0, (int) (6 * density), 0, 0);

        RetroArcadeButton pauseBtn = new RetroArcadeButton(this, "PAUSE", 0,
                Color.rgb(244, 181, 27), Color.rgb(255, 216, 77), Color.rgb(154, 106, 0),
                pixelFont, density);
        pauseBtn.setTextSizeSp(9f);
        LinearLayout.LayoutParams lpPause = new LinearLayout.LayoutParams(0, (int) (32 * density), 1f);
        lpPause.setMargins(0, 0, (int) (4 * density), 0);
        pauseBtn.setLayoutParams(lpPause);
        controlsRow.addView(pauseBtn);

        RetroArcadeButton cancelBtn = new RetroArcadeButton(this, "CANCEL", 0,
                Color.rgb(232, 75, 75), Color.rgb(247, 108, 108), Color.rgb(143, 41, 41),
                pixelFont, density);
        cancelBtn.setTextSizeSp(9f);
        LinearLayout.LayoutParams lpCancel = new LinearLayout.LayoutParams(0, (int) (32 * density), 1f);
        lpCancel.setMargins((int) (4 * density), 0, 0, 0);
        cancelBtn.setLayoutParams(lpCancel);
        controlsRow.addView(cancelBtn);

        progressCard.addView(controlsRow);
        cardsLayout.addView(progressCard);

        panel.addView(cardsLayout);

        // MAIN ACTION BUTTON
        String actionBtnText;
        if (isDownloaded) {
            actionBtnText = "INSTALL NOW (" + displayRemoteTag + ")";
        } else if (info.isUpdateAvailable) {
            actionBtnText = "DOWNLOAD & INSTALL";
        } else {
            actionBtnText = "RE-INSTALL (" + displayRemoteTag + ")";
        }

        RetroArcadeButton actionBtn = new RetroArcadeButton(this, actionBtnText, R.drawable.ic_play,
                Color.rgb(44, 203, 99), Color.rgb(112, 229, 141), Color.rgb(22, 115, 58),
                pixelFont, density);
        actionBtn.setTextSizeSp(10.5f);
        LinearLayout.LayoutParams lpAct = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (38 * density));
        lpAct.setMargins(0, 0, 0, (int) (5 * density));
        actionBtn.setLayoutParams(lpAct);
        panel.addView(actionBtn);

        // SECONDARY ROW: GitHub/Web & Close
        LinearLayout rowButtons = new LinearLayout(this);
        rowButtons.setOrientation(LinearLayout.HORIZONTAL);

        RetroArcadeButton webBtn = new RetroArcadeButton(this, "VIEW ON GITHUB", R.drawable.ic_github,
                Color.rgb(19, 191, 208), Color.rgb(91, 229, 240), Color.rgb(8, 125, 138),
                pixelFont, density);
        webBtn.setTextSizeSp(8.5f);
        LinearLayout.LayoutParams lpWeb = new LinearLayout.LayoutParams(0, (int) (36 * density), 1f);
        lpWeb.setMargins(0, 0, (int) (4 * density), 0);
        webBtn.setLayoutParams(lpWeb);
        rowButtons.addView(webBtn);

        RetroArcadeButton closeBtn = new RetroArcadeButton(this, "CLOSE", R.drawable.ic_pixel_close,
                Color.rgb(232, 75, 75), Color.rgb(247, 108, 108), Color.rgb(143, 41, 41),
                pixelFont, density);
        closeBtn.setTextSizeSp(9.5f);
        LinearLayout.LayoutParams lpCls = new LinearLayout.LayoutParams(0, (int) (36 * density), 1f);
        lpCls.setMargins((int) (4 * density), 0, 0, 0);
        closeBtn.setLayoutParams(lpCls);
        rowButtons.addView(closeBtn);

        panel.addView(rowButtons);

        builder.setView(panel);
        AlertDialog updateDialog = builder.create();
        updateDialog.show();

        if (updateDialog.getWindow() != null) {
            updateDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int maxDialogWidth = (int) (440 * density);
            int dialogWidth = Math.min(maxDialogWidth, (int) (screenWidth * 0.90f));
            updateDialog.getWindow().setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        final File[] downloadedApk = new File[1];
        if (isDownloaded) {
            downloadedApk[0] = appUpdater.getDownloadedApkFile(info);
        }

        pauseBtn.setOnClickListener(pv -> {
            if (audioMgr != null) audioMgr.playClickSound();
            if (appUpdater.isPaused()) {
                appUpdater.resumeDownload();
                pauseBtn.setText("PAUSE");
                pauseBtn.setColors(Color.rgb(244, 181, 27), Color.rgb(255, 216, 77), Color.rgb(154, 106, 0));
                progressLabel.setText("Downloading update package...");
                progressLabel.setTextColor(Color.rgb(18, 90, 145));
            } else {
                appUpdater.pauseDownload();
                pauseBtn.setText("RESUME");
                pauseBtn.setColors(Color.rgb(44, 203, 99), Color.rgb(112, 229, 141), Color.rgb(22, 115, 58));
                progressLabel.setText("Download paused.");
                progressLabel.setTextColor(Color.rgb(154, 106, 0));
            }
        });

        cancelBtn.setOnClickListener(cv -> {
            if (audioMgr != null) audioMgr.playClickSound();
            appUpdater.cancelDownload();
            progressCard.setVisibility(View.GONE);
            actionBtn.setVisibility(View.VISIBLE);
            actionBtn.setEnabled(true);
            actionBtn.setText(info.isUpdateAvailable ? "DOWNLOAD & INSTALL" : "RE-INSTALL (" + displayRemoteTag + ")");
        });

        actionBtn.setOnClickListener(v -> {
            if (audioMgr != null) audioMgr.playClickSound();
            if (downloadedApk[0] != null && downloadedApk[0].exists()) {
                appUpdater.installApk(MainActivity.this, downloadedApk[0]);
                return;
            }

            actionBtn.setVisibility(View.GONE);
            progressCard.setVisibility(View.VISIBLE);
            pauseBtn.setText("PAUSE");
            pauseBtn.setColors(Color.rgb(244, 181, 27), Color.rgb(255, 216, 77), Color.rgb(154, 106, 0));
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
                    actionBtn.setColors(Color.rgb(44, 203, 99), Color.rgb(112, 229, 141), Color.rgb(22, 115, 58));

                    progressLabel.setText("Download verified and complete!");
                    progressLabel.setTextColor(Color.rgb(22, 115, 58));
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
                    progressLabel.setTextColor(Color.rgb(192, 57, 43));
                    Toast.makeText(MainActivity.this, "Download error: " + error, Toast.LENGTH_LONG).show();
                }
            });
        });

        webBtn.setOnClickListener(v -> {
            if (audioMgr != null) audioMgr.playClickSound();
            openUrlSafely(info.htmlUrl);
        });

        closeBtn.setOnClickListener(v -> {
            if (audioMgr != null) audioMgr.playClickSound();
            updateDialog.dismiss();
        });
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
            Typeface pixelFont = getPressStartFont();
            float density = getResources().getDisplayMetrics().density;

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            // Outer Parchment Panel
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            int panelPadding = (int) (14 * density);
            panel.setPadding(panelPadding, panelPadding, panelPadding, panelPadding);

            GradientDrawable panelBg = new GradientDrawable();
            panelBg.setColor(Color.rgb(255, 240, 199)); // #FFF0C7 Cream parchment
            panelBg.setCornerRadius(16 * density);
            panelBg.setStroke((int) (3.5f * density), Color.rgb(23, 23, 23)); // #171717 Dark border
            panel.setBackground(panelBg);

            // Green Header Box
            FrameLayout headerBox = new FrameLayout(this);
            int headerPadV = (int) (10 * density);
            int headerPadH = (int) (12 * density);
            headerBox.setPadding(headerPadH, headerPadV, headerPadH, headerPadV);

            GradientDrawable headerBg = new GradientDrawable();
            headerBg.setColor(Color.rgb(79, 174, 79)); // #4FAE4F Retro Green
            headerBg.setCornerRadius(10 * density);
            headerBg.setStroke((int) (2.5f * density), Color.rgb(23, 23, 23)); // #171717
            headerBox.setBackground(headerBg);

            // Title & Subtitle vertical box
            LinearLayout titleBox = new LinearLayout(this);
            titleBox.setOrientation(LinearLayout.VERTICAL);
            titleBox.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams lpTitleBox = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            lpTitleBox.gravity = Gravity.CENTER;
            titleBox.setLayoutParams(lpTitleBox);

            TextView titleTv = new TextView(this);
            titleTv.setText("Global Leaderboard");
            titleTv.setTypeface(pixelFont);
            titleTv.setTextSize(13.5f);
            titleTv.setTextColor(Color.WHITE);
            titleTv.setGravity(Gravity.CENTER);
            titleTv.setShadowLayer(2f * density, 1.5f * density, 2f * density, Color.rgb(23, 23, 23));
            titleBox.addView(titleTv);

            TextView subtitleTv = new TextView(this);
            subtitleTv.setText("Fly Higher. Compete Worldwide.");
            subtitleTv.setTypeface(pixelFont);
            subtitleTv.setTextSize(7.5f);
            subtitleTv.setTextColor(Color.rgb(30, 77, 31)); // #1E4D1F
            subtitleTv.setGravity(Gravity.CENTER);
            subtitleTv.setPadding(0, (int) (3 * density), 0, 0);
            titleBox.addView(subtitleTv);

            headerBox.addView(titleBox);

            // Crowned Earth icon at top right
            ImageView globeIcon = new ImageView(this);
            globeIcon.setImageResource(R.drawable.ic_crowned_earth);
            int globeDim = (int) (34 * density);
            FrameLayout.LayoutParams lpGlobe = new FrameLayout.LayoutParams(globeDim, globeDim);
            lpGlobe.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            globeIcon.setLayoutParams(lpGlobe);
            headerBox.addView(globeIcon);

            panel.addView(headerBox);

            // Table Column Headers (YOU slot, #, Name, Score)
            LinearLayout colHeaders = new LinearLayout(this);
            colHeaders.setOrientation(LinearLayout.HORIZONTAL);
            colHeaders.setGravity(Gravity.CENTER_VERTICAL);
            colHeaders.setPadding((int) (8 * density), (int) (10 * density), (int) (8 * density), (int) (6 * density));

            View hdrSpacer = new View(this);
            hdrSpacer.setLayoutParams(new LinearLayout.LayoutParams((int) (36 * density), 1));
            colHeaders.addView(hdrSpacer);

            TextView tvHdrRank = new TextView(this);
            tvHdrRank.setText("#");
            tvHdrRank.setTypeface(pixelFont);
            tvHdrRank.setTextSize(10.5f);
            tvHdrRank.setTextColor(Color.rgb(43, 36, 27)); // #2B241B
            tvHdrRank.setGravity(Gravity.CENTER);
            tvHdrRank.setIncludeFontPadding(false);
            tvHdrRank.setLayoutParams(new LinearLayout.LayoutParams((int) (30 * density), LinearLayout.LayoutParams.WRAP_CONTENT));
            colHeaders.addView(tvHdrRank);

            TextView tvHdrName = new TextView(this);
            tvHdrName.setText("Name");
            tvHdrName.setTypeface(pixelFont);
            tvHdrName.setTextSize(10.5f);
            tvHdrName.setTextColor(Color.rgb(43, 36, 27));
            tvHdrName.setIncludeFontPadding(false);
            tvHdrName.setPadding((int) (10 * density), 0, 0, 0);
            LinearLayout.LayoutParams lpHdrName = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvHdrName.setLayoutParams(lpHdrName);
            colHeaders.addView(tvHdrName);

            TextView tvHdrScore = new TextView(this);
            tvHdrScore.setText("Score");
            tvHdrScore.setTypeface(pixelFont);
            tvHdrScore.setTextSize(10.5f);
            tvHdrScore.setTextColor(Color.rgb(43, 36, 27));
            tvHdrScore.setGravity(Gravity.END);
            tvHdrScore.setIncludeFontPadding(false);
            tvHdrScore.setLayoutParams(new LinearLayout.LayoutParams((int) (64 * density), LinearLayout.LayoutParams.WRAP_CONTENT));
            colHeaders.addView(tvHdrScore);

            panel.addView(colHeaders);

            // Scrollable list container (capped at 195dp so entire dialog fits comfortably in landscape)
            ScrollView listScrollView = new ScrollView(this) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int maxHeight = (int) (195 * getResources().getDisplayMetrics().density);
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
            listScrollView.setVerticalScrollBarEnabled(false);
            LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            listScrollView.setLayoutParams(lpScroll);

            LinearLayout listContainer = new LinearLayout(this);
            listContainer.setOrientation(LinearLayout.VERTICAL);
            listContainer.setPadding(0, (int) (2 * density), 0, (int) (2 * density));
            listScrollView.addView(listContainer);

            panel.addView(listScrollView);

            TextView statusText = new TextView(this);
            statusText.setText("Loading top players...");
            statusText.setTextSize(10f);
            statusText.setTypeface(pixelFont);
            statusText.setTextColor(Color.rgb(80, 70, 60));
            statusText.setGravity(Gravity.CENTER);
            statusText.setPadding(0, (int) (16 * density), 0, (int) (16 * density));
            listContainer.addView(statusText);

            // Bottom Buttons Row (Refresh & Close)
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpBtnRow = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpBtnRow.setMargins(0, (int) (12 * density), 0, 0);
            btnRow.setLayoutParams(lpBtnRow);

            RetroArcadeButton refreshBtn = new RetroArcadeButton(this, "REFRESH", R.drawable.ic_pixel_refresh,
                    Color.rgb(76, 175, 80), Color.rgb(112, 229, 141), Color.rgb(40, 107, 50),
                    pixelFont, density);
            refreshBtn.setTextSizeSp(9.5f);
            LinearLayout.LayoutParams lpRef = new LinearLayout.LayoutParams(0, (int) (36 * density), 1f);
            lpRef.setMargins(0, 0, (int) (6 * density), 0);
            refreshBtn.setLayoutParams(lpRef);
            btnRow.addView(refreshBtn);

            RetroArcadeButton closeBtn = new RetroArcadeButton(this, "CLOSE", R.drawable.ic_pixel_close,
                    Color.rgb(232, 75, 75), Color.rgb(247, 108, 108), Color.rgb(143, 41, 41),
                    pixelFont, density);
            closeBtn.setTextSizeSp(9.5f);
            LinearLayout.LayoutParams lpCls = new LinearLayout.LayoutParams(0, (int) (36 * density), 1f);
            lpCls.setMargins((int) (6 * density), 0, 0, 0);
            closeBtn.setLayoutParams(lpCls);
            btnRow.addView(closeBtn);

            panel.addView(btnRow);

            builder.setView(panel);

            AlertDialog dialog = builder.create();
            dialog.show();

            // Set fixed un-stretched cabinet width (430dp max or 88% of screen width)
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int maxDialogWidth = (int) (430 * density);
                int dialogWidth = Math.min(maxDialogWidth, (int) (screenWidth * 0.88f));
                dialog.getWindow().setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
            }

            Runnable loadLeaderboard = () -> {
                leaderboardManager.fetchLeaderboard(new LeaderboardManager.FetchCallback() {
                    @Override
                    public void onSuccess(List<LeaderboardManager.Entry> entries, boolean isFromCache) {
                        listContainer.removeAllViews();

                        if (entries.isEmpty()) {
                            TextView emptyTv = new TextView(MainActivity.this);
                            emptyTv.setText("No scores recorded yet. Play to set a record!");
                            emptyTv.setTextSize(10f);
                            emptyTv.setTypeface(pixelFont);
                            emptyTv.setTextColor(Color.rgb(90, 80, 70));
                            emptyTv.setGravity(Gravity.CENTER);
                            emptyTv.setPadding(0, (int) (20 * density), 0, (int) (20 * density));
                            listContainer.addView(emptyTv);
                            return;
                        }

                        String activeName = actMgr.isActivated() ? leaderboardManager.getPlayerName(actMgr.getActiveHeadIndex()) : "";
                        String mySavedName = getSharedPreferences("urukku_manush_prefs", Context.MODE_PRIVATE).getString("saved_player_name", "");
                        String myActiveCode = actMgr.getActiveCode();
                        String myActiveHash = actMgr.getActiveHash();
                        int myHeadIdx = actMgr.getActiveHeadIndex();
                        boolean meMatched = false;
                        View myRowView = null;

                        for (int i = 0; i < entries.size(); i++) {
                            LeaderboardManager.Entry e = entries.get(i);
                            int rank = i + 1;
                            boolean isMe = false;
                            if (!meMatched) {
                                if ((myActiveHash != null && !myActiveHash.isEmpty() && myActiveHash.equalsIgnoreCase(e.code))
                                        || (myActiveCode != null && !myActiveCode.isEmpty() && myActiveCode.equalsIgnoreCase(e.code))
                                        || (e.headIndex > 0 && e.headIndex == myHeadIdx)
                                        || (!activeName.isEmpty() && activeName.equalsIgnoreCase(e.name))
                                        || (!mySavedName.isEmpty() && mySavedName.equalsIgnoreCase(e.name))) {
                                    isMe = true;
                                    meMatched = true;
                                }
                            }

                            LinearLayout row = new LinearLayout(MainActivity.this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setGravity(Gravity.CENTER_VERTICAL);
                            row.setPadding((int) (8 * density), (int) (4 * density), (int) (8 * density), (int) (4 * density));

                            LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            lpRow.setMargins(0, (int) (2 * density), 0, (int) (2 * density));
                            row.setLayoutParams(lpRow);

                            // Background highlight depending on rank & player
                            GradientDrawable rowBg = new GradientDrawable();
                            rowBg.setCornerRadius(8 * density);

                            if (isMe) {
                                // Full cyan highlighted row for the active player
                                rowBg.setColor(Color.rgb(161, 237, 253)); // #A1EDFD Cyan
                                rowBg.setStroke((int) (2.5f * density), Color.rgb(24, 152, 186)); // #1898BA Blue border
                                row.setBackground(rowBg);
                                myRowView = row;
                            } else if (rank == 1) {
                                rowBg.setColor(Color.rgb(255, 216, 77)); // Gold #FFD84D
                                row.setBackground(rowBg);
                            } else if (rank == 2) {
                                rowBg.setColor(Color.rgb(215, 215, 215)); // Silver #D7D7D7
                                row.setBackground(rowBg);
                            } else if (rank == 3) {
                                rowBg.setColor(Color.rgb(232, 168, 124)); // Bronze #E8A87C
                                row.setBackground(rowBg);
                            } else {
                                row.setBackgroundColor(Color.TRANSPARENT);
                            }

                            // Slot 1: [YOU] > marker badge (or transparent spacer)
                            FrameLayout badgeSlot = new FrameLayout(MainActivity.this);
                            badgeSlot.setLayoutParams(new LinearLayout.LayoutParams((int) (36 * density), (int) (22 * density)));
                            if (isMe) {
                                YouBadgeView youBadge = new YouBadgeView(MainActivity.this, pixelFont, density);
                                FrameLayout.LayoutParams lpBadge = new FrameLayout.LayoutParams(
                                        (int) (34 * density), (int) (19 * density));
                                lpBadge.gravity = Gravity.CENTER_VERTICAL;
                                youBadge.setLayoutParams(lpBadge);
                                badgeSlot.addView(youBadge);
                            }
                            row.addView(badgeSlot);

                            // Slot 2: Rank / Medal column - strictly 30dp wide, matching header tvHdrRank
                            FrameLayout rankBox = new FrameLayout(MainActivity.this);
                            rankBox.setLayoutParams(new LinearLayout.LayoutParams((int) (30 * density), (int) (26 * density)));

                            if (rank <= 3) {
                                PixelMedalView medal = new PixelMedalView(MainActivity.this, rank, pixelFont, density);
                                FrameLayout.LayoutParams lpMedal = new FrameLayout.LayoutParams(
                                        (int) (24 * density), (int) (24 * density));
                                lpMedal.gravity = Gravity.CENTER;
                                medal.setLayoutParams(lpMedal);
                                rankBox.addView(medal);
                            } else {
                                TextView tvRank = new TextView(MainActivity.this);
                                tvRank.setText(String.valueOf(rank));
                                tvRank.setTypeface(pixelFont);
                                tvRank.setTextSize(11f);
                                tvRank.setTextColor(Color.rgb(23, 23, 23));
                                tvRank.setGravity(Gravity.CENTER);
                                tvRank.setIncludeFontPadding(false);
                                FrameLayout.LayoutParams lpRank = new FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                                tvRank.setLayoutParams(lpRank);
                                rankBox.addView(tvRank);
                            }
                            row.addView(rankBox);

                            // Slot 3: Player Name
                            TextView rankName = new TextView(MainActivity.this);
                            rankName.setText(e.name);
                            rankName.setTypeface(pixelFont);
                            rankName.setTextSize(10.5f);
                            rankName.setTextColor(Color.rgb(23, 23, 23));
                            rankName.setPadding((int) (10 * density), 0, 0, 0);
                            rankName.setSingleLine(true);
                            rankName.setEllipsize(android.text.TextUtils.TruncateAt.END);
                            rankName.setIncludeFontPadding(false);
                            LinearLayout.LayoutParams lpName = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                            rankName.setLayoutParams(lpName);
                            row.addView(rankName);

                            // Slot 4: Score (Right-aligned)
                            TextView scoreTv = new TextView(MainActivity.this);
                            scoreTv.setText(String.valueOf(e.score));
                            scoreTv.setTypeface(pixelFont);
                            scoreTv.setTextSize(11f);
                            scoreTv.setTextColor(Color.rgb(23, 23, 23));
                            scoreTv.setGravity(Gravity.END);
                            scoreTv.setIncludeFontPadding(false);
                            scoreTv.setLayoutParams(new LinearLayout.LayoutParams((int) (64 * density), LinearLayout.LayoutParams.WRAP_CONTENT));
                            row.addView(scoreTv);

                            listContainer.addView(row);
                        }

                        // Autoscroll to current player's score if present
                        final View targetRow = myRowView;
                        if (targetRow != null) {
                            listScrollView.post(() -> {
                                int scrollY = Math.max(0, targetRow.getTop() - (int) (8 * density));
                                listScrollView.smoothScrollTo(0, scrollY);
                            });
                        }
                    }

                    @Override
                    public void onError(String message) {
                        listContainer.removeAllViews();
                        TextView errTv = new TextView(MainActivity.this);
                        errTv.setText("Notice: " + message);
                        errTv.setTextSize(10f);
                        errTv.setTypeface(pixelFont);
                        errTv.setTextColor(Color.rgb(200, 60, 60));
                        errTv.setGravity(Gravity.CENTER);
                        errTv.setPadding(0, (int) (14 * density), 0, (int) (14 * density));
                        listContainer.addView(errTv);
                    }
                });
            };

            // First load from live/cache
            loadLeaderboard.run();

            // Refresh action
            refreshBtn.setOnClickListener(v -> {
                audioMgr.playClickSound();
                listContainer.removeAllViews();
                TextView syncingTv = new TextView(MainActivity.this);
                syncingTv.setText("Syncing scores...");
                syncingTv.setTypeface(pixelFont);
                syncingTv.setTextSize(10f);
                syncingTv.setTextColor(Color.rgb(80, 70, 60));
                syncingTv.setGravity(Gravity.CENTER);
                syncingTv.setPadding(0, (int) (20 * density), 0, (int) (20 * density));
                listContainer.addView(syncingTv);

                leaderboardManager.syncScoresWithServer(
                        scoreMgr.getHighScore(),
                        actMgr.getActiveHash(),
                        actMgr.getActiveHeadIndex(),
                        actMgr.getActiveHead(),
                        syncedScore -> {
                            if (syncedScore > scoreMgr.getHighScore()) {
                                scoreMgr.setHighScore(syncedScore);
                            }
                            loadLeaderboard.run();
                        }
                );
            });

            // Close action
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
            Typeface pixelFont = getPressStartFont();
            Typeface font = getGameFont();
            float density = getResources().getDisplayMetrics().density;

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            // Outer Parchment Cabinet Panel (#FFF0C7)
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            int panelPadding = (int) (12 * density);
            panel.setPadding(panelPadding, panelPadding, panelPadding, panelPadding);

            GradientDrawable panelBg = new GradientDrawable();
            panelBg.setColor(Color.rgb(255, 240, 199)); // #FFF0C7 Cream parchment
            panelBg.setCornerRadius(16 * density);
            panelBg.setStroke((int) (3.5f * density), Color.rgb(23, 23, 23)); // #171717 Dark border
            panel.setBackground(panelBg);

            // Header Box (Retro arcade green with crowns)
            FrameLayout headerBox = new FrameLayout(this);
            int headerPadV = (int) (8 * density);
            int headerPadH = (int) (12 * density);
            headerBox.setPadding(headerPadH, headerPadV, headerPadH, headerPadV);

            GradientDrawable headerBg = new GradientDrawable();
            headerBg.setColor(Color.rgb(79, 174, 79)); // #4FAE4F Retro Green
            headerBg.setCornerRadius(10 * density);
            headerBg.setStroke((int) (2.5f * density), Color.rgb(23, 23, 23));
            headerBox.setBackground(headerBg);

            // Left Crown
            ImageView leftCrown = new ImageView(this);
            leftCrown.setImageResource(R.drawable.ic_crown);
            int crownDim = (int) (28 * density);
            FrameLayout.LayoutParams lpLeftCrown = new FrameLayout.LayoutParams(crownDim, crownDim);
            lpLeftCrown.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            leftCrown.setLayoutParams(lpLeftCrown);
            headerBox.addView(leftCrown);

            // Center Title & Subtitle
            LinearLayout titleBox = new LinearLayout(this);
            titleBox.setOrientation(LinearLayout.VERTICAL);
            titleBox.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams lpTitleBox = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            lpTitleBox.gravity = Gravity.CENTER;
            titleBox.setLayoutParams(lpTitleBox);

            TextView titleTv = new TextView(this);
            titleTv.setText("Credits & Creator");
            titleTv.setTypeface(pixelFont);
            titleTv.setTextSize(13.5f);
            titleTv.setTextColor(Color.WHITE);
            titleTv.setGravity(Gravity.CENTER);
            titleTv.setShadowLayer(2f * density, 1.5f * density, 2f * density, Color.rgb(23, 23, 23));
            titleBox.addView(titleTv);

            TextView subTv = new TextView(this);
            subTv.setText("Atif Arman (Exotic Atif) • Creator & Developer");
            subTv.setTypeface(pixelFont);
            subTv.setTextSize(7.5f);
            subTv.setTextColor(Color.rgb(30, 77, 31)); // #1E4D1F
            subTv.setGravity(Gravity.CENTER);
            subTv.setPadding(0, (int) (2 * density), 0, 0);
            titleBox.addView(subTv);

            headerBox.addView(titleBox);

            // Right Crown
            ImageView rightCrown = new ImageView(this);
            rightCrown.setImageResource(R.drawable.ic_crown);
            FrameLayout.LayoutParams lpRightCrown = new FrameLayout.LayoutParams(crownDim, crownDim);
            lpRightCrown.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            rightCrown.setLayoutParams(lpRightCrown);
            headerBox.addView(rightCrown);

            panel.addView(headerBox);

            // Scrollable Middle Content
            ScrollView scrollContent = new ScrollView(this);
            scrollContent.setVerticalScrollBarEnabled(false);
            LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            lpScroll.setMargins(0, (int) (8 * density), 0, (int) (8 * density));
            scrollContent.setLayoutParams(lpScroll);

            LinearLayout contentLayout = new LinearLayout(this);
            contentLayout.setOrientation(LinearLayout.VERTICAL);

            // Creator & Project Specs Card
            LinearLayout infoCard = new LinearLayout(this);
            infoCard.setOrientation(LinearLayout.VERTICAL);
            int infoPad = (int) (10 * density);
            infoCard.setPadding(infoPad, infoPad, infoPad, infoPad);
            GradientDrawable infoBg = new GradientDrawable();
            infoBg.setColor(Color.rgb(255, 246, 217)); // #FFF6D9
            infoBg.setCornerRadius(9 * density);
            infoBg.setStroke((int) (1.8f * density), Color.rgb(216, 185, 106)); // #D8B96A
            infoCard.setBackground(infoBg);

            String[] bullets = new String[]{
                    "■  Coding, Art & Audio Design: Atif Arman",
                    "■  Typography: DotGothic16 by Fontworks (Google Fonts OFL)",
                    "■  Edition: Version " + appUpdater.getCurrentVersion() + " (High-FPS Dynamic Edition)"
            };

            for (String bullet : bullets) {
                TextView bulletTv = new TextView(this);
                bulletTv.setText(bullet);
                bulletTv.setTypeface(font);
                bulletTv.setTextSize(11.5f);
                bulletTv.setTextColor(Color.rgb(43, 36, 27)); // #2B241B
                bulletTv.setPadding(0, (int) (1.5f * density), 0, (int) (1.5f * density));
                infoCard.addView(bulletTv);
            }
            contentLayout.addView(infoCard);

            // Separator: ── CONNECT WITH EXOTIC ATIF ──
            TextView sepTv = new TextView(this);
            sepTv.setText("── CONNECT WITH EXOTIC ATIF ──");
            sepTv.setTypeface(pixelFont);
            sepTv.setTextSize(8.5f);
            sepTv.setTextColor(Color.rgb(43, 36, 27));
            sepTv.setGravity(Gravity.CENTER);
            sepTv.setPadding(0, (int) (8 * density), 0, (int) (6 * density));
            contentLayout.addView(sepTv);

            // Social Buttons Grid (Matching Picture 2)
            // Row 1: Instagram, YouTube, Snapchat
            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setPadding(0, 0, 0, (int) (4 * density));

            RetroArcadeButton btnInsta = new RetroArcadeButton(this, "INSTAGRAM", R.drawable.ic_instagram,
                    Color.rgb(214, 41, 118), Color.rgb(255, 95, 165), Color.rgb(138, 18, 73),
                    pixelFont, density);
            btnInsta.setTextSizeSp(8.5f);
            LinearLayout.LayoutParams lpInsta = new LinearLayout.LayoutParams(0, (int) (38 * density), 1f);
            lpInsta.setMargins(0, 0, (int) (3 * density), 0);
            btnInsta.setLayoutParams(lpInsta);
            btnInsta.setOnClickListener(v -> { audioMgr.playClickSound(); openUrlSafely("https://www.instagram.com/exotic_atif"); });
            row1.addView(btnInsta);

            RetroArcadeButton btnYt = new RetroArcadeButton(this, "YOUTUBE", R.drawable.ic_youtube,
                    Color.rgb(255, 43, 43), Color.rgb(255, 110, 110), Color.rgb(168, 19, 19),
                    pixelFont, density);
            btnYt.setTextSizeSp(8.5f);
            LinearLayout.LayoutParams lpYt = new LinearLayout.LayoutParams(0, (int) (38 * density), 1f);
            lpYt.setMargins((int) (2 * density), 0, (int) (2 * density), 0);
            btnYt.setLayoutParams(lpYt);
            btnYt.setOnClickListener(v -> { audioMgr.playClickSound(); openUrlSafely("https://www.youtube.com/@exotic_atif"); });
            row1.addView(btnYt);

            RetroArcadeButton btnSnap = new RetroArcadeButton(this, "SNAPCHAT", R.drawable.ic_snapchat,
                    Color.rgb(255, 210, 31), Color.rgb(255, 226, 102), Color.rgb(168, 133, 0),
                    pixelFont, density);
            btnSnap.setTextSizeSp(8.5f);
            btnSnap.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams lpSnap = new LinearLayout.LayoutParams(0, (int) (38 * density), 1f);
            lpSnap.setMargins((int) (3 * density), 0, 0, 0);
            btnSnap.setLayoutParams(lpSnap);
            btnSnap.setOnClickListener(v -> { audioMgr.playClickSound(); openUrlSafely("https://www.snapchat.com/add/exotic_atif"); });
            row1.addView(btnSnap);

            contentLayout.addView(row1);

            // Row 2: X/Twitter, Threads, GitHub
            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.setPadding(0, 0, 0, (int) (4 * density));

            RetroArcadeButton btnX = new RetroArcadeButton(this, "X / TWITTER", R.drawable.ic_x_twitter,
                    Color.rgb(23, 23, 23), Color.rgb(58, 58, 58), Color.rgb(10, 10, 10),
                    pixelFont, density);
            btnX.setTextSizeSp(7.8f);
            LinearLayout.LayoutParams lpX = new LinearLayout.LayoutParams(0, (int) (38 * density), 1f);
            lpX.setMargins(0, 0, (int) (3 * density), 0);
            btnX.setLayoutParams(lpX);
            btnX.setOnClickListener(v -> { audioMgr.playClickSound(); openUrlSafely("https://x.com/exotic_atif"); });
            row2.addView(btnX);

            RetroArcadeButton btnThreads = new RetroArcadeButton(this, "THREADS", R.drawable.ic_threads,
                    Color.rgb(23, 23, 23), Color.rgb(58, 58, 58), Color.rgb(10, 10, 10),
                    pixelFont, density);
            btnThreads.setTextSizeSp(8.5f);
            LinearLayout.LayoutParams lpThreads = new LinearLayout.LayoutParams(0, (int) (38 * density), 1f);
            lpThreads.setMargins((int) (2 * density), 0, (int) (2 * density), 0);
            btnThreads.setLayoutParams(lpThreads);
            btnThreads.setOnClickListener(v -> { audioMgr.playClickSound(); openUrlSafely("https://www.threads.net/@exotic_atif"); });
            row2.addView(btnThreads);

            RetroArcadeButton btnGit = new RetroArcadeButton(this, "GITHUB", R.drawable.ic_github,
                    Color.rgb(23, 23, 23), Color.rgb(58, 58, 58), Color.rgb(10, 10, 10),
                    pixelFont, density);
            btnGit.setTextSizeSp(8.5f);
            LinearLayout.LayoutParams lpGit = new LinearLayout.LayoutParams(0, (int) (38 * density), 1f);
            lpGit.setMargins((int) (3 * density), 0, 0, 0);
            btnGit.setLayoutParams(lpGit);
            btnGit.setOnClickListener(v -> { audioMgr.playClickSound(); openUrlSafely("https://github.com/exotic-atif"); });
            row2.addView(btnGit);

            contentLayout.addView(row2);

            // Row 3: Official Releases & Web (Full width Cyan)
            RetroArcadeButton btnWeb = new RetroArcadeButton(this, "OFFICIAL RELEASES & WEB", R.drawable.ic_globe,
                    Color.rgb(19, 191, 208), Color.rgb(91, 229, 240), Color.rgb(8, 125, 138),
                    pixelFont, density);
            btnWeb.setTextSizeSp(9.5f);
            LinearLayout.LayoutParams lpWeb = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (38 * density));
            lpWeb.setMargins(0, 0, 0, (int) (4 * density));
            btnWeb.setLayoutParams(lpWeb);
            btnWeb.setOnClickListener(v -> { audioMgr.playClickSound(); openUrlSafely("https://github.com/exotic-atif/urukku-manush/releases"); });
            contentLayout.addView(btnWeb);

            scrollContent.addView(contentLayout);
            panel.addView(scrollContent);

            // BACK TO MENU BUTTON (Red #E84B4B)
            RetroArcadeButton btnBack = new RetroArcadeButton(this, "BACK TO MENU", R.drawable.ic_back,
                    Color.rgb(232, 75, 75), Color.rgb(247, 108, 108), Color.rgb(143, 41, 41),
                    pixelFont, density);
            btnBack.setTextSizeSp(11f);
            LinearLayout.LayoutParams lpBack = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (40 * density));
            btnBack.setLayoutParams(lpBack);
            panel.addView(btnBack);

            builder.setView(panel);

            AlertDialog dialog = builder.create();
            dialog.show();

            btnBack.setOnClickListener(v -> {
                audioMgr.playClickSound();
                dialog.dismiss();
            });

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int maxDialogWidth = (int) (480 * density);
                int dialogWidth = Math.min(maxDialogWidth, (int) (screenWidth * 0.90f));
                dialog.getWindow().setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
    }
}
