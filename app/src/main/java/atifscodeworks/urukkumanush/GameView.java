package atifscodeworks.urukkumanush;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.core.content.ContextCompat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "GameView";

    public enum State {
        INTRO,
        MENU,
        PLAYING,
        PAUSED,
        COUNTDOWN,
        GAME_OVER
    }

    private State currentState = State.INTRO;
    private GameThread gameThread;
    private GameActionListener actionListener;

    private ActivationManager activationManager;
    private ScoreManager scoreManager;
    private AudioManager audioManager;
    private BackgroundScroller backgroundScroller;
    private Player player;
    private final List<Obstacle> obstacles = new ArrayList<>();

    private int screenWidth;
    private int screenHeight;

    // Intro state variables
    private Bitmap logoBitmap;
    private float introTimer = 0f;
    private static final float INTRO_DURATION = 3.5f;
    private float introAlpha = 255f;

    // Menu state variables (4 arcade buttons)
    private float menuBobbingTimer = 0f;
    private final RectF btnPlay = new RectF();
    private final RectF btnOptions = new RectF();
    private final RectF btnLeaderboard = new RectF();
    private final RectF btnCredits = new RectF();
    private int pressedMenuButton = 0; // 0=none, 1=play, 2=options, 3=leaderboard, 4=credits

    private Drawable drawablePlay;
    private Drawable drawableGear;
    private Drawable drawableTrophy;
    private Drawable drawableCredits;
    private Drawable drawableRefresh;
    private Drawable drawableClose;
    private Typeface dotGothic;
    private Typeface pressStartFont;

    // Playing state variables
    private int currentScore = 0;
    private boolean isNewBest = false;
    private float baseObstacleSpeed;
    private float obstacleSpawnDistance;
    private final RectF btnPause = new RectF();

    // Paused state variables
    private final RectF pausedCard = new RectF();
    private final RectF btnResume = new RectF();
    private final RectF btnRestart = new RectF();
    private final RectF btnToggleScore = new RectF();
    private final RectF btnMenu = new RectF();
    private int pressedPauseButton = 0; // 0=none, 1=resume, 2=restart, 3=toggleScore, 4=menu

    // Countdown state variables
    private float countdownTimer = 3.0f;

    // Game Over state variables
    private final RectF gameOverCard = new RectF();
    private final RectF gameOverStatsCard = new RectF();
    private final RectF btnGameOverPlayAgain = new RectF();
    private final RectF btnGameOverMenu = new RectF();
    private int pressedGameOverButton = 0; // 0=none, 1=playAgain, 2=menu

    // Reusable cached RectFs and Paints to avoid GC allocation during rendering
    private final RectF tempRect = new RectF();
    private final RectF tempRect2 = new RectF();
    private final RectF menuLogoDestRect = new RectF();
    private final Rect textBounds = new Rect();
    private final Path buttonClipPath = new Path();

    // Paints
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leftAlignPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leftStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint uiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint uiStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint();

    public GameView(Context context) {
        super(context);
        init();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        getHolder().addCallback(this);
        setFocusable(true);

        activationManager = new ActivationManager(getContext());
        scoreManager = new ScoreManager(getContext());
        audioManager = new AudioManager(getContext());
        backgroundScroller = new BackgroundScroller(getContext());
        player = new Player(getContext());

        loadLogoBitmap();

        // Setup Paints with Fonts
        try {
            dotGothic = Typeface.createFromAsset(getContext().getAssets(), "fonts/DotGothic16-Regular.ttf");
        } catch (Exception e) {
            Log.e(TAG, "Error loading DotGothic16 font", e);
        }
        if (dotGothic == null) {
            dotGothic = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        }

        try {
            pressStartFont = Typeface.createFromAsset(getContext().getAssets(), "fonts/PressStart2P-Regular.ttf");
        } catch (Exception e) {
            Log.e(TAG, "Error loading PressStart2P font", e);
        }
        if (pressStartFont == null) {
            pressStartFont = dotGothic;
        }

        try {
            drawablePlay = ContextCompat.getDrawable(getContext(), R.drawable.ic_play);
            drawableGear = ContextCompat.getDrawable(getContext(), R.drawable.ic_gear);
            drawableTrophy = ContextCompat.getDrawable(getContext(), R.drawable.ic_leaderboard);
            drawableCredits = ContextCompat.getDrawable(getContext(), R.drawable.ic_credits);
            drawableRefresh = ContextCompat.getDrawable(getContext(), R.drawable.ic_refresh);
            drawableClose = ContextCompat.getDrawable(getContext(), R.drawable.ic_close);
        } catch (Exception e) {
            Log.e(TAG, "Error loading menu drawables", e);
        }

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(dotGothic);

        textStrokePaint.setTextAlign(Paint.Align.CENTER);
        textStrokePaint.setTypeface(dotGothic);
        textStrokePaint.setStyle(Paint.Style.STROKE);
        textStrokePaint.setColor(Color.BLACK);

        leftAlignPaint.setTextAlign(Paint.Align.LEFT);
        leftAlignPaint.setTypeface(dotGothic);

        leftStrokePaint.setTextAlign(Paint.Align.LEFT);
        leftStrokePaint.setTypeface(dotGothic);
        leftStrokePaint.setStyle(Paint.Style.STROKE);
        leftStrokePaint.setColor(Color.BLACK);

        uiPaint.setStyle(Paint.Style.FILL);
        uiStrokePaint.setStyle(Paint.Style.STROKE);

        overlayPaint.setStyle(Paint.Style.FILL);
    }

    public void setActionListener(GameActionListener listener) {
        this.actionListener = listener;
    }

    public ActivationManager getActivationManager() {
        return activationManager;
    }

    public ScoreManager getScoreManager() {
        return scoreManager;
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    private void loadLogoBitmap() {
        try (InputStream is = getContext().getAssets().open("imgs/logo-new.png")) {
            logoBitmap = BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            try (InputStream is2 = getContext().getAssets().open("imgs/logo.png")) {
                logoBitmap = BitmapFactory.decodeStream(is2);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                holder.getSurface().setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            } catch (Exception ignored) {
            }
        }

        if (gameThread == null || !gameThread.isAlive()) {
            gameThread = new GameThread(holder, this);
            gameThread.setRunning(true);
            gameThread.start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;

        backgroundScroller.init(width, height);
        player.init(width, height, activationManager.getActiveHead());

        this.baseObstacleSpeed = width * 0.28f;
        this.obstacleSpawnDistance = width * 0.42f;

        computeButtonLayouts();

        if (currentState == State.INTRO) {
            audioManager.playIntroMusic();
        }
    }

    private void computeButtonLayouts() {
        float btnW = screenWidth * 0.205f;
        float btnH = screenHeight * 0.145f;
        float centerY = screenHeight * 0.72f;

        // Menu buttons (Play, Options, Leaderboard, Credits)
        float spacing = screenWidth * 0.022f;
        float totalMenuW = (btnW * 4) + (spacing * 3);
        float startX = (screenWidth - totalMenuW) / 2.0f;

        btnPlay.set(startX, centerY - (btnH / 2f), startX + btnW, centerY + (btnH / 2f));
        btnOptions.set(startX + (btnW + spacing), centerY - (btnH / 2f), startX + (btnW + spacing) + btnW, centerY + (btnH / 2f));
        btnLeaderboard.set(startX + (btnW + spacing) * 2, centerY - (btnH / 2f), startX + (btnW + spacing) * 2 + btnW, centerY + (btnH / 2f));
        btnCredits.set(startX + (btnW + spacing) * 3, centerY - (btnH / 2f), startX + (btnW + spacing) * 3 + btnW, centerY + (btnH / 2f));

        // In-game Pause button (top right)
        float pauseBtnSize = screenHeight * 0.12f;
        float margin = screenHeight * 0.04f;
        btnPause.set(screenWidth - margin - pauseBtnSize, margin, screenWidth - margin, margin + pauseBtnSize);

        // Paused cabinet & buttons (Parchment cabinet with 4 vertical arcade buttons)
        float pausedCardW = screenWidth * 0.40f;
        float pausedCardH = screenHeight * 0.82f;
        pausedCard.set((screenWidth - pausedCardW) / 2f, (screenHeight - pausedCardH) / 2f,
                (screenWidth + pausedCardW) / 2f, (screenHeight + pausedCardH) / 2f);

        float hPadTop = pausedCardH * 0.040f;
        float headerH = pausedCardH * 0.150f;
        float headerBottom = pausedCard.top + hPadTop + headerH;

        float pBtnW = pausedCardW * 0.86f;
        float pBtnH = pausedCardH * 0.138f;
        float pSpacing = pausedCardH * 0.038f;
        float gapHeader = pausedCardH * 0.042f;

        float pBtnStartX = (screenWidth - pBtnW) / 2f;
        float pFirstBtnY = headerBottom + gapHeader;

        btnResume.set(pBtnStartX, pFirstBtnY, pBtnStartX + pBtnW, pFirstBtnY + pBtnH);
        btnRestart.set(pBtnStartX, pFirstBtnY + pBtnH + pSpacing, pBtnStartX + pBtnW, pFirstBtnY + (pBtnH * 2) + pSpacing);
        btnToggleScore.set(pBtnStartX, pFirstBtnY + (pBtnH + pSpacing) * 2, pBtnStartX + pBtnW, pFirstBtnY + (pBtnH + pSpacing) * 2 + pBtnH);
        btnMenu.set(pBtnStartX, pFirstBtnY + (pBtnH + pSpacing) * 3, pBtnStartX + pBtnW, pFirstBtnY + (pBtnH + pSpacing) * 3 + pBtnH);

        // Game Over cabinet & buttons
        float cardW = screenWidth * 0.52f;
        float cardH = screenHeight * 0.78f;
        gameOverCard.set((screenWidth - cardW) / 2f, (screenHeight - cardH) / 2f,
                (screenWidth + cardW) / 2f, (screenHeight + cardH) / 2f);

        float statsPadX = cardW * 0.06f;
        float statsTop = gameOverCard.top + (cardH * 0.25f);
        float statsH = cardH * 0.40f;
        gameOverStatsCard.set(gameOverCard.left + statsPadX, statsTop, gameOverCard.right - statsPadX, statsTop + statsH);

        float goBtnW = cardW * 0.43f;
        float goBtnH = cardH * 0.20f;
        float goBtnY = gameOverCard.bottom - goBtnH - (cardH * 0.07f);
        float goGap = cardW * 0.04f;
        float goStartX = (screenWidth - (goBtnW * 2 + goGap)) / 2f;

        btnGameOverPlayAgain.set(goStartX, goBtnY, goStartX + goBtnW, goBtnY + goBtnH);
        btnGameOverMenu.set(goStartX + goBtnW + goGap, goBtnY, goStartX + goBtnW + goGap + goBtnW, goBtnY + goBtnH);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        if (gameThread != null) {
            gameThread.setRunning(false);
            while (retry) {
                try {
                    gameThread.join(200);
                    retry = false;
                } catch (InterruptedException ignored) {
                }
            }
            gameThread = null;
        }
    }

    public void update(float dt) {
        switch (currentState) {
            case INTRO:
                introTimer += dt;
                if (introTimer > (INTRO_DURATION - 0.8f)) {
                    float fadeProgress = (introTimer - (INTRO_DURATION - 0.8f)) / 0.8f;
                    introAlpha = Math.max(0, 255 * (1.0f - fadeProgress));
                }
                if (introTimer >= INTRO_DURATION) {
                    currentState = State.MENU;
                    if (!activationManager.isActivated()) {
                        post(() -> {
                            if (actionListener != null) {
                                actionListener.onShowActivationDialog(this::onFirstTimeActivationSuccess);
                            }
                        });
                    }
                }
                break;

            case MENU:
                menuBobbingTimer += dt * 3.5f;
                backgroundScroller.update(dt * 0.5f);
                break;

            case PLAYING:
                // Smooth progressive speed multiplier
                float speedMultiplier = 1.0f;
                if (currentScore >= 3 && currentScore <= 25) {
                    speedMultiplier = 1.0f + ((currentScore - 3) / 22.0f) * 0.20f; // Smooth ramp 1.00x -> 1.20x
                } else if (currentScore > 25 && currentScore <= 50) {
                    speedMultiplier = 1.20f + ((currentScore - 25) / 25.0f) * 0.15f; // Smooth ramp 1.20x -> 1.35x
                } else if (currentScore > 50) {
                    speedMultiplier = 1.35f; // Hard cap at 1.35x for balanced gameplay
                }

                float currentObstacleSpeed = baseObstacleSpeed * speedMultiplier;
                backgroundScroller.setSpeedMultiplier(speedMultiplier);
                backgroundScroller.update(dt);
                player.update(dt);

                // Check ground collision
                if (player.checkGroundCollision()) {
                    triggerGameOver();
                    return;
                }

                // Update obstacles
                float lastObstacleX = -1;
                Iterator<Obstacle> iterator = obstacles.iterator();
                while (iterator.hasNext()) {
                    Obstacle ob = iterator.next();
                    ob.setSpeed(currentObstacleSpeed);
                    // Movement state is strictly decided at spawn time; never mutate in-flight obstacles!
                    ob.update(dt);

                    if (ob.getX() > lastObstacleX) {
                        lastObstacleX = ob.getX();
                    }

                    // Check collision
                    if (ob.collidesWith(player.getHitBox())) {
                        triggerGameOver();
                        return;
                    }

                    // Check scoring
                    if (ob.checkScore(player.getX())) {
                        currentScore++;
                        audioManager.playPassSound(); // Play random sound from assets/audios/pass
                    }

                    // Remove offscreen
                    if (ob.isOffScreen()) {
                        iterator.remove();
                    }
                }

                // Spawn obstacles: After score 50, balanced 50/50 mix of oscillating vs static pipes
                boolean spawnOscillating = (currentScore >= 50) && (Math.random() < 0.50);

                if (obstacles.isEmpty()) {
                    obstacles.add(new Obstacle(screenWidth, screenHeight, screenWidth + 100, currentObstacleSpeed, spawnOscillating));
                } else if (lastObstacleX < screenWidth - obstacleSpawnDistance) {
                    obstacles.add(new Obstacle(screenWidth, screenHeight, screenWidth + 50, currentObstacleSpeed, spawnOscillating));
                }
                break;

            case PAUSED:
                break;

            case COUNTDOWN:
                countdownTimer -= dt;
                if (countdownTimer <= 0) {
                    currentState = State.PLAYING;
                    audioManager.resumeBgm();
                }
                break;

            case GAME_OVER:
                break;
        }
    }

    private void triggerGameOver() {
        audioManager.stopBgm();
        audioManager.playLoseSound();
        isNewBest = scoreManager.checkAndSaveScore(currentScore);
        if (isNewBest && actionListener != null) {
            final int best = currentScore;
            post(() -> actionListener.onNewHighScore(best));
        }
        currentState = State.GAME_OVER;
    }

    public void startNewGame() {
        audioManager.stopLoseSound();
        currentScore = 0;
        isNewBest = false;
        obstacles.clear();
        backgroundScroller.setSpeedMultiplier(1.0f);
        player.reset();
        player.loadHeadBitmap(activationManager.getActiveHead());
        currentState = State.PLAYING;
    }

    public void pauseGame() {
        if (currentState == State.PLAYING) {
            audioManager.pauseBgm();
            currentState = State.PAUSED;
        }
    }

    public void reloadPlayerHead() {
        if (player != null) {
            player.loadHeadBitmap(activationManager.getActiveHead());
        }
    }

    public State getGameState() {
        return currentState;
    }

    public void drawGame(Canvas canvas) {
        if (canvas == null) return;

        canvas.drawColor(Color.rgb(18, 22, 34));

        switch (currentState) {
            case INTRO:
                drawIntro(canvas);
                break;
            case MENU:
                backgroundScroller.draw(canvas);
                drawMenu(canvas);
                break;
            case PLAYING:
                backgroundScroller.draw(canvas);
                for (Obstacle ob : obstacles) {
                    ob.draw(canvas);
                }
                player.draw(canvas);
                drawPlayingHUD(canvas);
                break;
            case PAUSED:
                backgroundScroller.draw(canvas);
                for (Obstacle ob : obstacles) {
                    ob.draw(canvas);
                }
                player.draw(canvas);
                drawPlayingHUD(canvas);
                drawPausedOverlay(canvas);
                break;
            case COUNTDOWN:
                backgroundScroller.draw(canvas);
                for (Obstacle ob : obstacles) {
                    ob.draw(canvas);
                }
                player.draw(canvas);
                drawPlayingHUD(canvas);
                drawCountdown(canvas);
                break;
            case GAME_OVER:
                backgroundScroller.draw(canvas);
                for (Obstacle ob : obstacles) {
                    ob.draw(canvas);
                }
                player.draw(canvas);
                drawGameOverOverlay(canvas);
                break;
        }
    }

    private void drawIntro(Canvas canvas) {
        int alpha = (int) introAlpha;
        Paint introPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        introPaint.setAlpha(alpha);

        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight * 0.40f;

        // Draw Game Logo
        if (logoBitmap != null) {
            float targetLogoSize = screenHeight * 0.38f;
            float logoScale = targetLogoSize / Math.max(logoBitmap.getWidth(), logoBitmap.getHeight());
            int w = (int) (logoBitmap.getWidth() * logoScale);
            int h = (int) (logoBitmap.getHeight() * logoScale);
            RectF dest = new RectF(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f);
            canvas.drawBitmap(logoBitmap, null, dest, introPaint);
        }

        // Draw Game Title "Urukku Manush"
        textPaint.setTextSize(screenHeight * 0.11f);
        textPaint.setColor(Color.rgb(255, 215, 0));
        textPaint.setAlpha(alpha);

        textStrokePaint.setTextSize(screenHeight * 0.11f);
        textStrokePaint.setStrokeWidth(screenHeight * 0.015f);
        textStrokePaint.setAlpha(alpha);

        float textY = centerY + (screenHeight * 0.28f);
        canvas.drawText("Urukku Manush", centerX, textY, textStrokePaint);
        canvas.drawText("Urukku Manush", centerX, textY, textPaint);

        // Draw "made by Atif"
        textPaint.setTextSize(screenHeight * 0.055f);
        textPaint.setColor(Color.rgb(200, 225, 255));
        textPaint.setAlpha(alpha);

        textStrokePaint.setTextSize(screenHeight * 0.055f);
        textStrokePaint.setStrokeWidth(screenHeight * 0.008f);
        textStrokePaint.setAlpha(alpha);

        float subY = textY + (screenHeight * 0.10f);
        canvas.drawText("made by Atif", centerX, subY, textStrokePaint);
        canvas.drawText("made by Atif", centerX, subY, textPaint);

        // Animated loading indicator
        float progress = (introTimer / INTRO_DURATION);
        float barWidth = screenWidth * 0.25f;
        float barHeight = screenHeight * 0.018f;
        float barY = subY + (screenHeight * 0.06f);
        RectF barBg = new RectF(centerX - barWidth / 2f, barY, centerX + barWidth / 2f, barY + barHeight);
        uiPaint.setColor(Color.rgb(40, 50, 70));
        uiPaint.setAlpha(alpha);
        canvas.drawRoundRect(barBg, 12f, 12f, uiPaint);

        RectF barFill = new RectF(barBg.left, barBg.top, barBg.left + (barWidth * Math.min(1.0f, progress)), barBg.bottom);
        uiPaint.setColor(Color.rgb(0, 229, 255));
        uiPaint.setAlpha(alpha);
        canvas.drawRoundRect(barFill, 12f, 12f, uiPaint);
    }

    private void drawMenu(Canvas canvas) {
        float centerX = screenWidth / 2.0f;

        // Title banner "Urukku Manush" in Press Start 2P pixel font
        textStrokePaint.setTypeface(pressStartFont);
        textPaint.setTypeface(pressStartFont);

        float titleSize = screenHeight * 0.11f;
        textStrokePaint.setTextSize(titleSize);
        textStrokePaint.setStrokeWidth(screenHeight * 0.022f);
        textStrokePaint.setColor(Color.rgb(17, 17, 17)); // #111111

        textPaint.setTextSize(titleSize);
        textPaint.setColor(Color.rgb(244, 181, 27)); // #F4B51B Retro Gold

        float titleY = screenHeight * 0.22f;

        // Drop shadow for title
        textStrokePaint.setColor(Color.rgb(23, 23, 23));
        canvas.drawText("Urukku Manush", centerX + 3f, titleY + 5f, textStrokePaint);

        textStrokePaint.setColor(Color.rgb(17, 17, 17));
        canvas.drawText("Urukku Manush", centerX, titleY, textStrokePaint);
        canvas.drawText("Urukku Manush", centerX, titleY, textPaint);

        // High score badge: "★ BEST SCORE: 89 ★"
        float badgeSize = screenHeight * 0.042f;
        textStrokePaint.setTextSize(badgeSize);
        textStrokePaint.setStrokeWidth(screenHeight * 0.009f);
        textStrokePaint.setColor(Color.rgb(17, 17, 17));

        textPaint.setTextSize(badgeSize);
        textPaint.setColor(Color.rgb(255, 230, 128));

        float badgeY = titleY + screenHeight * 0.085f;
        String bestText = "★ BEST SCORE: " + scoreManager.getHighScore() + " ★";
        canvas.drawText(bestText, centerX, badgeY, textStrokePaint);
        canvas.drawText(bestText, centerX, badgeY, textPaint);

        // Draw bobbing logo or unlocked character on starting menu screen
        float bobOffset = (float) Math.sin(menuBobbingTimer) * (screenHeight * 0.025f);
        float logoH = screenHeight * 0.22f;
        float charX = centerX;
        float charY = screenHeight * 0.46f + bobOffset;

        // Soft aura behind logo or character
        uiPaint.setColor(Color.argb(70, 0, 229, 255));
        canvas.drawCircle(charX, charY, logoH * 0.65f, uiPaint);

        Bitmap displayBmp = (activationManager.isActivated() && player.getHeadBitmap() != null)
                ? player.getHeadBitmap()
                : logoBitmap;

        if (displayBmp != null) {
            float aspect = (float) displayBmp.getWidth() / displayBmp.getHeight();
            float logoW = logoH * aspect;
            menuLogoDestRect.set(charX - logoW / 2f, charY - logoH / 2f, charX + logoW / 2f, charY + logoH / 2f);
            canvas.drawBitmap(displayBmp, null, menuLogoDestRect, null);
        }

        // Draw 4 Retro Arcade Menu Buttons matching UI Spec & Picture 3
        drawArcadeButton(canvas, btnPlay, "PLAY", drawablePlay,
                Color.rgb(44, 203, 99), Color.rgb(112, 229, 141), Color.rgb(22, 115, 58), 1.0f, pressedMenuButton == 1);
        drawArcadeButton(canvas, btnOptions, "SETTINGS", drawableGear,
                Color.rgb(38, 155, 232), Color.rgb(100, 198, 255), Color.rgb(18, 90, 145), 0.90f, pressedMenuButton == 2);
        drawArcadeButton(canvas, btnLeaderboard, "LEADERBOARD", drawableTrophy,
                Color.rgb(244, 181, 27), Color.rgb(255, 216, 77), Color.rgb(154, 106, 0), 0.72f, pressedMenuButton == 3);
        drawArcadeButton(canvas, btnCredits, "CREDITS", drawableCredits,
                Color.rgb(155, 89, 182), Color.rgb(192, 123, 224), Color.rgb(89, 51, 107), 0.92f, pressedMenuButton == 4);
    }

    private void drawArcadeButton(Canvas canvas, RectF rect, String label, Drawable icon,
                                  int bodyColor, int highlightColor, int shadowColor, float textScale, boolean isPressed) {
        float r = 10f;
        float pressOffsetY = isPressed ? 3f : 0f;

        // 1. Button Body Fill
        uiPaint.setColor(bodyColor);
        canvas.drawRoundRect(rect, r, r, uiPaint);

        // 2. Inner 3D slim bottom bevel & top highlight (smooth rounded clip, NO sharp corners)
        buttonClipPath.reset();
        buttonClipPath.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(buttonClipPath);

        if (isPressed) {
            // When pressed: face is depressed into bottom bevel (5%)
            float stripH = rect.height() * 0.05f;
            tempRect.set(rect.left, rect.bottom - stripH, rect.right, rect.bottom);
            uiPaint.setColor(shadowColor);
            canvas.drawRect(tempRect, uiPaint);
        } else {
            // Normal 3D state: slim, subtle bottom shadow bevel (11%) + subtle top highlight (8%)
            float stripH = rect.height() * 0.11f;
            tempRect.set(rect.left, rect.bottom - stripH, rect.right, rect.bottom);
            uiPaint.setColor(shadowColor);
            canvas.drawRect(tempRect, uiPaint);

            float hlH = rect.height() * 0.08f;
            tempRect.set(rect.left, rect.top, rect.right, rect.top + hlH);
            uiPaint.setColor(highlightColor);
            canvas.drawRect(tempRect, uiPaint);
        }
        canvas.restore();

        // 3. Dark outer pixel border (#171717)
        uiStrokePaint.setColor(Color.rgb(23, 23, 23));
        uiStrokePaint.setStrokeWidth(3.5f);
        canvas.drawRoundRect(rect, r, r, uiStrokePaint);

        // 4. Centered Icon + Label group with physical depression offset
        float baseTextSize = rect.height() * 0.28f * textScale;
        textPaint.setTypeface(pressStartFont);
        textPaint.setTextSize(baseTextSize);
        textStrokePaint.setTypeface(pressStartFont);
        textStrokePaint.setTextSize(baseTextSize);
        textStrokePaint.setStrokeWidth(baseTextSize * 0.26f);

        float textW = textPaint.measureText(label);
        float iconSize = (icon != null) ? rect.height() * 0.44f : 0f;
        float gap = (icon != null) ? rect.width() * 0.045f : 0f;
        float totalContentW = iconSize + gap + textW;

        float contentStartX = rect.centerX() - (totalContentW / 2.0f);
        float centerY = rect.centerY() + pressOffsetY;

        if (icon != null) {
            float iconLeft = contentStartX;
            float iconTop = centerY - (iconSize / 2.0f);
            icon.setBounds((int) iconLeft, (int) iconTop, (int) (iconLeft + iconSize), (int) (iconTop + iconSize));
            icon.setTint(Color.WHITE);
            icon.draw(canvas);
        }

        float textX = contentStartX + iconSize + gap + (textW / 2.0f);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = centerY - (fm.ascent + fm.descent) / 2.0f;

        textStrokePaint.setColor(Color.rgb(17, 17, 17));
        textPaint.setColor(Color.WHITE);
        canvas.drawText(label, textX, textY, textStrokePaint);
        canvas.drawText(label, textX, textY, textPaint);
    }

    private void drawPlayingHUD(Canvas canvas) {
        if (scoreManager.isScoreMeterEnabled()) {
            // Top Center: Current Score (Pixel Arcade styling)
            float scoreSize = screenHeight * 0.12f;
            textStrokePaint.setTypeface(pressStartFont);
            textStrokePaint.setTextSize(scoreSize);
            textStrokePaint.setStrokeWidth(scoreSize * 0.22f);
            textStrokePaint.setColor(Color.rgb(23, 23, 23));
            textPaint.setTypeface(pressStartFont);
            textPaint.setTextSize(scoreSize);
            textPaint.setColor(Color.WHITE);

            float scoreY = screenHeight * 0.14f;
            canvas.drawText(String.valueOf(currentScore), screenWidth / 2.0f, scoreY, textStrokePaint);
            canvas.drawText(String.valueOf(currentScore), screenWidth / 2.0f, scoreY, textPaint);

            // Top Left: High Score
            float bestSize = screenHeight * 0.040f;
            leftStrokePaint.setTypeface(pressStartFont);
            leftStrokePaint.setTextSize(bestSize);
            leftStrokePaint.setStrokeWidth(bestSize * 0.22f);
            leftStrokePaint.setColor(Color.rgb(23, 23, 23));
            leftAlignPaint.setTypeface(pressStartFont);
            leftAlignPaint.setTextSize(bestSize);
            leftAlignPaint.setColor(Color.rgb(255, 216, 77)); // Retro gold

            String bestText = "BEST: " + Math.max(currentScore, scoreManager.getHighScore());
            canvas.drawText(bestText, screenHeight * 0.04f, screenHeight * 0.09f, leftStrokePaint);
            canvas.drawText(bestText, screenHeight * 0.04f, screenHeight * 0.09f, leftAlignPaint);
        }

        // Top Right: Mini Retro Arcade Pause Button
        uiPaint.setColor(Color.rgb(255, 240, 199)); // #FFF0C7 Parchment body
        canvas.drawRoundRect(btnPause, 10f, 10f, uiPaint);
        uiStrokePaint.setColor(Color.rgb(23, 23, 23)); // #171717 Dark border
        uiStrokePaint.setStrokeWidth(3.5f);
        canvas.drawRoundRect(btnPause, 10f, 10f, uiStrokePaint);

        // Pause bars ❚❚
        uiPaint.setColor(Color.rgb(23, 23, 23));
        float barW = btnPause.width() * 0.16f;
        float barH = btnPause.height() * 0.46f;
        float gap = btnPause.width() * 0.10f;
        float cx = btnPause.centerX();
        float cy = btnPause.centerY();

        tempRect.set(cx - gap - barW, cy - barH / 2f, cx - gap, cy + barH / 2f);
        canvas.drawRoundRect(tempRect, 3f, 3f, uiPaint);
        tempRect2.set(cx + gap, cy - barH / 2f, cx + gap + barW, cy + barH / 2f);
        canvas.drawRoundRect(tempRect2, 3f, 3f, uiPaint);
    }

    private void drawPausedOverlay(Canvas canvas) {
        overlayPaint.setColor(Color.argb(160, 0, 0, 0));
        canvas.drawRect(0, 0, screenWidth, screenHeight, overlayPaint);

        // 1. Outer Cream Parchment Cabinet (#FFF0C7)
        uiPaint.setColor(Color.rgb(255, 240, 199));
        canvas.drawRoundRect(pausedCard, 20f, 20f, uiPaint);
        uiStrokePaint.setColor(Color.rgb(23, 23, 23));
        uiStrokePaint.setStrokeWidth(4.5f);
        canvas.drawRoundRect(pausedCard, 20f, 20f, uiStrokePaint);

        // 2. Header Box (Retro Arcade Blue #269BE8)
        float hPadX = pausedCard.width() * 0.05f;
        float hPadTop = pausedCard.height() * 0.040f;
        float headerH = pausedCard.height() * 0.150f;
        tempRect.set(pausedCard.left + hPadX, pausedCard.top + hPadTop,
                pausedCard.right - hPadX, pausedCard.top + hPadTop + headerH);

        uiPaint.setColor(Color.rgb(38, 155, 232));
        canvas.drawRoundRect(tempRect, 12f, 12f, uiPaint);
        uiStrokePaint.setColor(Color.rgb(23, 23, 23));
        uiStrokePaint.setStrokeWidth(3f);
        canvas.drawRoundRect(tempRect, 12f, 12f, uiStrokePaint);

        float titleSize = headerH * 0.46f;
        textPaint.setTypeface(pressStartFont);
        textPaint.setTextSize(titleSize);
        textStrokePaint.setTypeface(pressStartFont);
        textStrokePaint.setTextSize(titleSize);
        textStrokePaint.setStrokeWidth(titleSize * 0.22f);
        textStrokePaint.setColor(Color.rgb(23, 23, 23));
        textPaint.setColor(Color.WHITE);

        String pauseStr = "PAUSED";
        textPaint.getTextBounds(pauseStr, 0, pauseStr.length(), textBounds);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textStrokePaint.setTextAlign(Paint.Align.LEFT);

        float drawX = tempRect.centerX() - (textBounds.width() / 2f) - textBounds.left;
        float drawY = tempRect.centerY() - textBounds.exactCenterY();

        canvas.drawText(pauseStr, drawX, drawY, textStrokePaint);
        canvas.drawText(pauseStr, drawX, drawY, textPaint);

        // Restore center alignment for arcade buttons
        textPaint.setTextAlign(Paint.Align.CENTER);
        textStrokePaint.setTextAlign(Paint.Align.CENTER);

        // 3. Action Buttons (Retro Arcade 3D Buttons with slim bevel)
        drawArcadeButton(canvas, btnResume, "RESUME", drawablePlay,
                Color.rgb(44, 203, 99), Color.rgb(112, 229, 141), Color.rgb(22, 115, 58), 0.85f, pressedPauseButton == 1);
        drawArcadeButton(canvas, btnRestart, "RESTART", drawableRefresh,
                Color.rgb(244, 181, 27), Color.rgb(255, 216, 77), Color.rgb(154, 106, 0), 0.82f, pressedPauseButton == 2);

        boolean scoreOn = scoreManager.isScoreMeterEnabled();
        String scoreLabel = scoreOn ? "SCORE: ON" : "SCORE: OFF";
        int scoreBg = scoreOn ? Color.rgb(38, 155, 232) : Color.rgb(107, 114, 128);
        int scoreTop = scoreOn ? Color.rgb(100, 198, 255) : Color.rgb(156, 163, 175);
        int scoreDark = scoreOn ? Color.rgb(18, 90, 145) : Color.rgb(55, 65, 81);
        drawArcadeButton(canvas, btnToggleScore, scoreLabel, drawableTrophy,
                scoreBg, scoreTop, scoreDark, 0.80f, pressedPauseButton == 3);

        drawArcadeButton(canvas, btnMenu, "MAIN MENU", drawableClose,
                Color.rgb(232, 75, 75), Color.rgb(247, 108, 108), Color.rgb(143, 41, 41), 0.78f, pressedPauseButton == 4);
    }

    private void drawCountdown(Canvas canvas) {
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight * 0.48f;

        overlayPaint.setColor(Color.argb(120, 0, 0, 0));
        canvas.drawRect(0, 0, screenWidth, screenHeight, overlayPaint);

        int count = (int) Math.ceil(countdownTimer);
        String countStr = (count <= 0) ? "GO!" : String.valueOf(count);

        float fraction = countdownTimer - (count - 1);
        float scale = 1.0f + (fraction * 0.5f);

        textStrokePaint.setTextSize(screenHeight * 0.26f * scale);
        textStrokePaint.setStrokeWidth(screenHeight * 0.025f * scale);
        textStrokePaint.setColor(Color.rgb(23, 23, 23));
        textPaint.setTextSize(screenHeight * 0.26f * scale);

        if ("GO!".equals(countStr)) {
            textPaint.setColor(Color.rgb(46, 204, 113));
        } else {
            textPaint.setColor(Color.rgb(255, 215, 0));
        }

        canvas.drawText(countStr, centerX, centerY + (screenHeight * 0.08f * scale), textStrokePaint);
        canvas.drawText(countStr, centerX, centerY + (screenHeight * 0.08f * scale), textPaint);
    }

    private void drawGameOverOverlay(Canvas canvas) {
        overlayPaint.setColor(Color.argb(160, 0, 0, 0));
        canvas.drawRect(0, 0, screenWidth, screenHeight, overlayPaint);

        // 1. Outer Cream Parchment Cabinet (#FFF0C7)
        uiPaint.setColor(Color.rgb(255, 240, 199));
        canvas.drawRoundRect(gameOverCard, 20f, 20f, uiPaint);
        uiStrokePaint.setColor(Color.rgb(23, 23, 23));
        uiStrokePaint.setStrokeWidth(4.5f);
        canvas.drawRoundRect(gameOverCard, 20f, 20f, uiStrokePaint);

        // 2. Header Box (Retro Arcade Red #E84B4B)
        float hPadX = gameOverCard.width() * 0.04f;
        float hPadTop = gameOverCard.height() * 0.04f;
        float headerH = gameOverCard.height() * 0.17f;
        tempRect.set(gameOverCard.left + hPadX, gameOverCard.top + hPadTop,
                gameOverCard.right - hPadX, gameOverCard.top + hPadTop + headerH);

        uiPaint.setColor(Color.rgb(232, 75, 75));
        canvas.drawRoundRect(tempRect, 12f, 12f, uiPaint);
        uiStrokePaint.setColor(Color.rgb(23, 23, 23));
        uiStrokePaint.setStrokeWidth(3f);
        canvas.drawRoundRect(tempRect, 12f, 12f, uiStrokePaint);

        float titleSize = headerH * 0.50f;
        textPaint.setTypeface(pressStartFont);
        textPaint.setTextSize(titleSize);
        textStrokePaint.setTypeface(pressStartFont);
        textStrokePaint.setTextSize(titleSize);
        textStrokePaint.setStrokeWidth(titleSize * 0.24f);
        textStrokePaint.setColor(Color.rgb(23, 23, 23));
        textPaint.setColor(Color.WHITE);

        float titleY = tempRect.centerY() + (titleSize * 0.35f);
        canvas.drawText("GAME OVER", tempRect.centerX(), titleY, textStrokePaint);
        canvas.drawText("GAME OVER", tempRect.centerX(), titleY, textPaint);

        // 3. Inner Card for Score & Best (#FDF3DA)
        uiPaint.setColor(Color.rgb(253, 243, 218));
        canvas.drawRoundRect(gameOverStatsCard, 12f, 12f, uiPaint);
        uiStrokePaint.setColor(Color.rgb(216, 185, 106));
        uiStrokePaint.setStrokeWidth(2.5f);
        canvas.drawRoundRect(gameOverStatsCard, 12f, 12f, uiStrokePaint);

        float statsCenterX = gameOverStatsCard.centerX();
        float scoreLabelSize = gameOverStatsCard.height() * 0.17f;
        float scoreValSize = gameOverStatsCard.height() * 0.29f;

        float col1X = gameOverStatsCard.left + (gameOverStatsCard.width() * 0.28f);
        float col2X = gameOverStatsCard.right - (gameOverStatsCard.width() * 0.28f);
        float labelY = gameOverStatsCard.top + (gameOverStatsCard.height() * 0.36f);
        float valY = gameOverStatsCard.top + (gameOverStatsCard.height() * 0.74f);

        // SCORE column
        textPaint.setTypeface(pressStartFont);
        textPaint.setTextSize(scoreLabelSize);
        textPaint.setColor(Color.rgb(90, 80, 70));
        canvas.drawText("SCORE", col1X, labelY, textPaint);

        textPaint.setTextSize(scoreValSize);
        textStrokePaint.setTextSize(scoreValSize);
        textStrokePaint.setStrokeWidth(scoreValSize * 0.22f);
        textStrokePaint.setColor(Color.rgb(23, 23, 23));
        textPaint.setColor(Color.WHITE);
        canvas.drawText(String.valueOf(currentScore), col1X, valY, textStrokePaint);
        canvas.drawText(String.valueOf(currentScore), col1X, valY, textPaint);

        // Divider
        uiPaint.setColor(Color.rgb(216, 185, 106));
        float divTop = gameOverStatsCard.top + (gameOverStatsCard.height() * 0.16f);
        float divBottom = gameOverStatsCard.bottom - (gameOverStatsCard.height() * 0.16f);
        canvas.drawRect(statsCenterX - 1.5f, divTop, statsCenterX + 1.5f, divBottom, uiPaint);

        // BEST column
        textPaint.setTypeface(pressStartFont);
        textPaint.setTextSize(scoreLabelSize);
        textPaint.setColor(Color.rgb(154, 106, 0));
        canvas.drawText("BEST", col2X, labelY, textPaint);

        textPaint.setTextSize(scoreValSize);
        textStrokePaint.setTextSize(scoreValSize);
        textStrokePaint.setStrokeWidth(scoreValSize * 0.22f);
        textStrokePaint.setColor(Color.rgb(23, 23, 23));
        textPaint.setColor(Color.rgb(244, 181, 27));
        canvas.drawText(String.valueOf(scoreManager.getHighScore()), col2X, valY, textStrokePaint);
        canvas.drawText(String.valueOf(scoreManager.getHighScore()), col2X, valY, textPaint);

        // New record banner if applicable
        if (isNewBest) {
            float badgeH = gameOverStatsCard.height() * 0.22f;
            tempRect.set(gameOverStatsCard.left + 16f, gameOverStatsCard.bottom - badgeH - 6f,
                    gameOverStatsCard.right - 16f, gameOverStatsCard.bottom - 6f);
            uiPaint.setColor(Color.rgb(44, 203, 99));
            canvas.drawRoundRect(tempRect, 6f, 6f, uiPaint);
            uiStrokePaint.setColor(Color.rgb(23, 23, 23));
            uiStrokePaint.setStrokeWidth(2f);
            canvas.drawRoundRect(tempRect, 6f, 6f, uiStrokePaint);

            float bTextSize = badgeH * 0.54f;
            textPaint.setTextSize(bTextSize);
            textStrokePaint.setTextSize(bTextSize);
            textStrokePaint.setStrokeWidth(bTextSize * 0.22f);
            textStrokePaint.setColor(Color.rgb(23, 23, 23));
            textPaint.setColor(Color.WHITE);
            canvas.drawText("★ NEW BEST RECORD! ★", tempRect.centerX(), tempRect.centerY() + (bTextSize * 0.35f), textStrokePaint);
            canvas.drawText("★ NEW BEST RECORD! ★", tempRect.centerX(), tempRect.centerY() + (bTextSize * 0.35f), textPaint);
        }

        // 4. Action Buttons (Retro Arcade 3D Buttons with slim bevel and icons)
        drawArcadeButton(canvas, btnGameOverPlayAgain, "PLAY AGAIN", drawablePlay,
                Color.rgb(44, 203, 99), Color.rgb(112, 229, 141), Color.rgb(22, 115, 58), 0.72f, pressedGameOverButton == 1);
        drawArcadeButton(canvas, btnGameOverMenu, "MAIN MENU", drawableClose,
                Color.rgb(38, 155, 232), Color.rgb(100, 198, 255), Color.rgb(18, 90, 145), 0.75f, pressedGameOverButton == 2);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getX();
        float ty = event.getY();
        int action = event.getAction();

        if (currentState == State.MENU) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (btnPlay.contains(tx, ty)) {
                        pressedMenuButton = 1;
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        return true;
                    } else if (btnOptions.contains(tx, ty)) {
                        pressedMenuButton = 2;
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        return true;
                    } else if (btnLeaderboard.contains(tx, ty)) {
                        pressedMenuButton = 3;
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        return true;
                    } else if (btnCredits.contains(tx, ty)) {
                        pressedMenuButton = 4;
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        return true;
                    }
                    pressedMenuButton = 0;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (pressedMenuButton == 1 && !btnPlay.contains(tx, ty)) pressedMenuButton = 0;
                    else if (pressedMenuButton == 2 && !btnOptions.contains(tx, ty)) pressedMenuButton = 0;
                    else if (pressedMenuButton == 3 && !btnLeaderboard.contains(tx, ty)) pressedMenuButton = 0;
                    else if (pressedMenuButton == 4 && !btnCredits.contains(tx, ty)) pressedMenuButton = 0;
                    return true;

                case MotionEvent.ACTION_UP:
                    int clicked = pressedMenuButton;
                    pressedMenuButton = 0;
                    if (clicked == 1 && btnPlay.contains(tx, ty)) {
                        handlePlayClicked();
                        return true;
                    } else if (clicked == 2 && btnOptions.contains(tx, ty)) {
                        audioManager.playClickSound();
                        if (actionListener != null) actionListener.onShowOptionsDialog();
                        return true;
                    } else if (clicked == 3 && btnLeaderboard.contains(tx, ty)) {
                        audioManager.playClickSound();
                        if (actionListener != null) actionListener.onShowLeaderboardDialog();
                        return true;
                    } else if (clicked == 4 && btnCredits.contains(tx, ty)) {
                        audioManager.playClickSound();
                        if (actionListener != null) actionListener.onShowCreditsDialog();
                        return true;
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    pressedMenuButton = 0;
                    return true;
            }
            return true;
        }

        if (currentState == State.PAUSED) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (btnResume.contains(tx, ty)) {
                        pressedPauseButton = 1;
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        return true;
                    } else if (btnRestart.contains(tx, ty)) {
                        pressedPauseButton = 2;
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        return true;
                    } else if (btnToggleScore.contains(tx, ty)) {
                        pressedPauseButton = 3;
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        return true;
                    } else if (btnMenu.contains(tx, ty)) {
                        pressedPauseButton = 4;
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        return true;
                    }
                    pressedPauseButton = 0;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (pressedPauseButton == 1 && !btnResume.contains(tx, ty)) pressedPauseButton = 0;
                    else if (pressedPauseButton == 2 && !btnRestart.contains(tx, ty)) pressedPauseButton = 0;
                    else if (pressedPauseButton == 3 && !btnToggleScore.contains(tx, ty)) pressedPauseButton = 0;
                    else if (pressedPauseButton == 4 && !btnMenu.contains(tx, ty)) pressedPauseButton = 0;
                    return true;

                case MotionEvent.ACTION_UP:
                    int pClicked = pressedPauseButton;
                    pressedPauseButton = 0;
                    if (pClicked == 1 && btnResume.contains(tx, ty)) {
                        audioManager.playClickSound();
                        countdownTimer = 3.0f;
                        currentState = State.COUNTDOWN;
                        return true;
                    } else if (pClicked == 2 && btnRestart.contains(tx, ty)) {
                        audioManager.playClickSound();
                        audioManager.playRandomPlaySound();
                        audioManager.startBgm();
                        startNewGame();
                        return true;
                    } else if (pClicked == 3 && btnToggleScore.contains(tx, ty)) {
                        boolean now = !scoreManager.isScoreMeterEnabled();
                        scoreManager.setScoreMeterEnabled(now);
                        audioManager.playClickSound();
                        return true;
                    } else if (pClicked == 4 && btnMenu.contains(tx, ty)) {
                        audioManager.playClickSound();
                        audioManager.stopBgm();
                        currentState = State.MENU;
                        return true;
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    pressedPauseButton = 0;
                    return true;
            }
            return true;
        }

        if (currentState == State.GAME_OVER) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (btnGameOverPlayAgain.contains(tx, ty)) {
                        pressedGameOverButton = 1;
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        return true;
                    } else if (btnGameOverMenu.contains(tx, ty)) {
                        pressedGameOverButton = 2;
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        return true;
                    }
                    pressedGameOverButton = 0;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (pressedGameOverButton == 1 && !btnGameOverPlayAgain.contains(tx, ty)) pressedGameOverButton = 0;
                    else if (pressedGameOverButton == 2 && !btnGameOverMenu.contains(tx, ty)) pressedGameOverButton = 0;
                    return true;

                case MotionEvent.ACTION_UP:
                    int goClicked = pressedGameOverButton;
                    pressedGameOverButton = 0;
                    if (goClicked == 1 && btnGameOverPlayAgain.contains(tx, ty)) {
                        audioManager.playClickSound();
                        audioManager.playRandomPlaySound();
                        audioManager.startBgm();
                        startNewGame();
                        return true;
                    } else if (goClicked == 2 && btnGameOverMenu.contains(tx, ty)) {
                        audioManager.playClickSound();
                        currentState = State.MENU;
                        return true;
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    pressedGameOverButton = 0;
                    return true;
            }
            return true;
        }

        if (action != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(event);
        }

        switch (currentState) {
            case INTRO:
                audioManager.stopIntroMusic();
                currentState = State.MENU;
                if (!activationManager.isActivated()) {
                    post(() -> {
                        if (actionListener != null) {
                            actionListener.onShowActivationDialog(this::onFirstTimeActivationSuccess);
                        }
                    });
                }
                return true;

            case MENU:
                return true;

            case PLAYING:
                if (btnPause.contains(tx, ty)) {
                    audioManager.playClickSound();
                    pauseGame();
                    return true;
                }
                player.jump();
                audioManager.playJumpSound();
                return true;

            case COUNTDOWN:
                return true;
        }

        return super.onTouchEvent(event);
    }

    private void handlePlayClicked() {
        if (!activationManager.isActivated()) {
            if (actionListener != null) {
                actionListener.onShowActivationDialog(this::onFirstTimeActivationSuccess);
            }
        } else {
            onNormalPlay();
        }
    }

    public void onFirstTimeActivationSuccess() {
        audioManager.stopIntroMusic();
        audioManager.playRandomPlaySound();
        currentScore = 0;
        isNewBest = false;
        obstacles.clear();
        backgroundScroller.setSpeedMultiplier(1.0f);
        player.reset();
        player.loadHeadBitmap(activationManager.getActiveHead());
        countdownTimer = 3.0f;
        currentState = State.COUNTDOWN;
    }

    private void onNormalPlay() {
        audioManager.stopIntroMusic();
        audioManager.playRandomPlaySound();
        audioManager.startBgm();
        startNewGame();
    }
}
