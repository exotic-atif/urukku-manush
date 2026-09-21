package atifscodeworks.urukkumanush;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

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

    // Menu state variables
    private float menuBobbingTimer = 0f;
    private final RectF btnPlay = new RectF();
    private final RectF btnOptions = new RectF();
    private final RectF btnCredits = new RectF();

    // Playing state variables
    private int currentScore = 0;
    private boolean isNewBest = false;
    private float baseObstacleSpeed;
    private float obstacleSpawnDistance;
    private final RectF btnPause = new RectF();

    // Paused state variables
    private final RectF btnResume = new RectF();
    private final RectF btnRestart = new RectF();
    private final RectF btnMenu = new RectF();

    // Countdown state variables
    private float countdownTimer = 3.0f;

    // Game Over state variables
    private final RectF btnGameOverPlayAgain = new RectF();
    private final RectF btnGameOverMenu = new RectF();
    private final RectF gameOverCard = new RectF();

    // Reusable cached RectFs and Paints to avoid GC allocation during rendering
    private final RectF tempRect = new RectF();
    private final RectF tempRect2 = new RectF();
    private final RectF menuLogoDestRect = new RectF();

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

        // Setup Paints with Google Font DotGothic16
        Typeface dotGothic = null;
        try {
            dotGothic = Typeface.createFromAsset(getContext().getAssets(), "fonts/DotGothic16-Regular.ttf");
        } catch (Exception e) {
            Log.e(TAG, "Error loading DotGothic16 font", e);
        }
        if (dotGothic == null) {
            dotGothic = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
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
        float btnW = screenWidth * 0.22f;
        float btnH = screenHeight * 0.16f;
        float centerY = screenHeight * 0.72f;

        // Menu buttons (Play, Options, Credits)
        float totalMenuW = (btnW * 3) + (screenWidth * 0.04f * 2);
        float startX = (screenWidth - totalMenuW) / 2.0f;
        float spacing = screenWidth * 0.04f;

        btnPlay.set(startX, centerY - (btnH / 2f), startX + btnW, centerY + (btnH / 2f));
        btnOptions.set(startX + btnW + spacing, centerY - (btnH / 2f), startX + btnW * 2 + spacing, centerY + (btnH / 2f));
        btnCredits.set(startX + (btnW + spacing) * 2, centerY - (btnH / 2f), startX + (btnW + spacing) * 2 + btnW, centerY + (btnH / 2f));

        // In-game Pause button (top right)
        float pauseBtnSize = screenHeight * 0.13f;
        float margin = screenHeight * 0.04f;
        btnPause.set(screenWidth - margin - pauseBtnSize, margin, screenWidth - margin, margin + pauseBtnSize);

        // Paused menu buttons (Resume, Restart, Main Menu)
        float pBtnW = screenWidth * 0.24f;
        float pBtnH = screenHeight * 0.15f;
        float pSpacing = screenHeight * 0.03f;
        float pCenterY = screenHeight * 0.52f;

        btnResume.set((screenWidth - pBtnW) / 2f, pCenterY - pBtnH * 1.5f - pSpacing, (screenWidth + pBtnW) / 2f, pCenterY - pBtnH * 0.5f - pSpacing);
        btnRestart.set((screenWidth - pBtnW) / 2f, pCenterY - (pBtnH / 2f), (screenWidth + pBtnW) / 2f, pCenterY + (pBtnH / 2f));
        btnMenu.set((screenWidth - pBtnW) / 2f, pCenterY + (pBtnH / 2f) + pSpacing, (screenWidth + pBtnW) / 2f, pCenterY + pBtnH * 1.5f + pSpacing);

        // Game Over card & buttons
        float cardW = screenWidth * 0.55f;
        float cardH = screenHeight * 0.68f;
        gameOverCard.set((screenWidth - cardW) / 2f, (screenHeight - cardH) / 2f, (screenWidth + cardW) / 2f, (screenHeight + cardH) / 2f);

        float goBtnW = cardW * 0.40f;
        float goBtnH = cardH * 0.22f;
        float goBtnY = gameOverCard.bottom - goBtnH - (cardH * 0.10f);
        float goGap = cardW * 0.08f;
        float goTotalBtnW = (goBtnW * 2) + goGap;
        float goStartX = gameOverCard.left + (cardW - goTotalBtnW) / 2f;

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

        // Title banner "Urukku Manush"
        textStrokePaint.setTextSize(screenHeight * 0.14f);
        textStrokePaint.setStrokeWidth(screenHeight * 0.02f);
        textPaint.setTextSize(screenHeight * 0.14f);
        textPaint.setColor(Color.rgb(255, 215, 0));

        float titleY = screenHeight * 0.24f;
        canvas.drawText("Urukku Manush", centerX, titleY, textStrokePaint);
        canvas.drawText("Urukku Manush", centerX, titleY, textPaint);

        // High score badge
        textPaint.setTextSize(screenHeight * 0.055f);
        textPaint.setColor(Color.rgb(255, 235, 150));
        textStrokePaint.setTextSize(screenHeight * 0.055f);
        textStrokePaint.setStrokeWidth(screenHeight * 0.008f);
        float badgeY = titleY + screenHeight * 0.09f;
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

        // Draw 3 Menu Buttons
        drawStyledButton(canvas, btnPlay, "PLAY", Color.rgb(46, 204, 113), Color.rgb(39, 174, 96));
        drawStyledButton(canvas, btnOptions, "SETTINGS", Color.rgb(52, 152, 219), Color.rgb(41, 128, 185));
        drawStyledButton(canvas, btnCredits, "CREDITS", Color.rgb(155, 89, 182), Color.rgb(142, 68, 173));
    }

    private void drawPlayingHUD(Canvas canvas) {
        // Top Center: Current Score
        textStrokePaint.setTextSize(screenHeight * 0.14f);
        textStrokePaint.setStrokeWidth(screenHeight * 0.018f);
        textPaint.setTextSize(screenHeight * 0.14f);
        textPaint.setColor(Color.WHITE);

        float scoreY = screenHeight * 0.15f;
        canvas.drawText(String.valueOf(currentScore), screenWidth / 2.0f, scoreY, textStrokePaint);
        canvas.drawText(String.valueOf(currentScore), screenWidth / 2.0f, scoreY, textPaint);

        // Top Left: High Score
        leftStrokePaint.setTextSize(screenHeight * 0.05f);
        leftStrokePaint.setStrokeWidth(screenHeight * 0.007f);
        leftAlignPaint.setTextSize(screenHeight * 0.05f);
        leftAlignPaint.setColor(Color.rgb(255, 215, 0));

        String bestText = "BEST: " + Math.max(currentScore, scoreManager.getHighScore());
        canvas.drawText(bestText, screenHeight * 0.04f, screenHeight * 0.10f, leftStrokePaint);
        canvas.drawText(bestText, screenHeight * 0.04f, screenHeight * 0.10f, leftAlignPaint);

        // Top Right: Pause button
        uiPaint.setColor(Color.argb(190, 20, 24, 38));
        canvas.drawRoundRect(btnPause, 16f, 16f, uiPaint);
        uiStrokePaint.setColor(Color.rgb(0, 229, 255));
        uiStrokePaint.setStrokeWidth(4f);
        canvas.drawRoundRect(btnPause, 16f, 16f, uiStrokePaint);

        // Draw ⏸ pause symbol without object allocation
        uiPaint.setColor(Color.WHITE);
        float barW = btnPause.width() * 0.18f;
        float barH = btnPause.height() * 0.50f;
        float gap = btnPause.width() * 0.14f;
        float cx = btnPause.centerX();
        float cy = btnPause.centerY();

        tempRect.set(cx - gap - barW, cy - barH / 2f, cx - gap, cy + barH / 2f);
        canvas.drawRoundRect(tempRect, 4f, 4f, uiPaint);
        tempRect2.set(cx + gap, cy - barH / 2f, cx + gap + barW, cy + barH / 2f);
        canvas.drawRoundRect(tempRect2, 4f, 4f, uiPaint);
    }

    private void drawPausedOverlay(Canvas canvas) {
        overlayPaint.setColor(Color.argb(190, 10, 12, 20));
        canvas.drawRect(0, 0, screenWidth, screenHeight, overlayPaint);

        float centerX = screenWidth / 2.0f;

        textStrokePaint.setTextSize(screenHeight * 0.13f);
        textStrokePaint.setStrokeWidth(screenHeight * 0.015f);
        textPaint.setTextSize(screenHeight * 0.13f);
        textPaint.setColor(Color.rgb(0, 229, 255));

        canvas.drawText("PAUSED", centerX, screenHeight * 0.22f, textStrokePaint);
        canvas.drawText("PAUSED", centerX, screenHeight * 0.22f, textPaint);

        drawStyledButton(canvas, btnResume, "RESUME", Color.rgb(46, 204, 113), Color.rgb(39, 174, 96));
        drawStyledButton(canvas, btnRestart, "RESTART", Color.rgb(230, 126, 34), Color.rgb(211, 84, 0));
        drawStyledButton(canvas, btnMenu, "MAIN MENU", Color.rgb(52, 152, 219), Color.rgb(41, 128, 185));
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
        overlayPaint.setColor(Color.argb(200, 10, 12, 20));
        canvas.drawRect(0, 0, screenWidth, screenHeight, overlayPaint);

        uiPaint.setColor(Color.rgb(28, 33, 50));
        canvas.drawRoundRect(gameOverCard, 28f, 28f, uiPaint);
        uiStrokePaint.setColor(Color.rgb(231, 76, 60));
        uiStrokePaint.setStrokeWidth(6f);
        canvas.drawRoundRect(gameOverCard, 28f, 28f, uiStrokePaint);

        float centerX = gameOverCard.centerX();

        textStrokePaint.setTextSize(screenHeight * 0.11f);
        textStrokePaint.setStrokeWidth(screenHeight * 0.015f);
        textPaint.setTextSize(screenHeight * 0.11f);
        textPaint.setColor(Color.rgb(231, 76, 60));

        float titleY = gameOverCard.top + (screenHeight * 0.14f);
        canvas.drawText("GAME OVER", centerX, titleY, textStrokePaint);
        canvas.drawText("GAME OVER", centerX, titleY, textPaint);

        textStrokePaint.setTextSize(screenHeight * 0.075f);
        textStrokePaint.setStrokeWidth(screenHeight * 0.01f);
        textPaint.setTextSize(screenHeight * 0.075f);
        textPaint.setColor(Color.WHITE);

        float scoreY = titleY + (screenHeight * 0.11f);
        canvas.drawText("SCORE: " + currentScore, centerX, scoreY, textStrokePaint);
        canvas.drawText("SCORE: " + currentScore, centerX, scoreY, textPaint);

        textPaint.setTextSize(screenHeight * 0.065f);
        textPaint.setColor(Color.rgb(255, 215, 0));
        float bestY = scoreY + (screenHeight * 0.09f);
        canvas.drawText("BEST: " + scoreManager.getHighScore(), centerX, bestY, textStrokePaint);
        canvas.drawText("BEST: " + scoreManager.getHighScore(), centerX, bestY, textPaint);

        if (isNewBest) {
            textPaint.setTextSize(screenHeight * 0.045f);
            textPaint.setColor(Color.rgb(46, 204, 113));
            canvas.drawText("★ NEW BEST RECORD! ★", centerX, bestY + (screenHeight * 0.06f), textPaint);
        }

        drawStyledButton(canvas, btnGameOverPlayAgain, "PLAY AGAIN", Color.rgb(46, 204, 113), Color.rgb(39, 174, 96));
        drawStyledButton(canvas, btnGameOverMenu, "MAIN MENU", Color.rgb(52, 152, 219), Color.rgb(41, 128, 185));
    }

    private void drawStyledButton(Canvas canvas, RectF rect, String label, int topColor, int bottomColor) {
        tempRect.set(rect.left, rect.top + 6f, rect.right, rect.bottom + 6f);
        uiPaint.setColor(Color.argb(90, 0, 0, 0));
        canvas.drawRoundRect(tempRect, 18f, 18f, uiPaint);

        uiPaint.setColor(topColor);
        canvas.drawRoundRect(rect, 18f, 18f, uiPaint);

        uiStrokePaint.setColor(Color.argb(180, 255, 255, 255));
        uiStrokePaint.setStrokeWidth(3f);
        canvas.drawRoundRect(rect, 18f, 18f, uiStrokePaint);

        float textSize = rect.height() * 0.40f;
        textStrokePaint.setTextSize(textSize);
        textStrokePaint.setStrokeWidth(textSize * 0.15f);
        textPaint.setTextSize(textSize);
        textPaint.setColor(Color.WHITE);

        float textY = rect.centerY() + (textSize * 0.35f);
        canvas.drawText(label, rect.centerX(), textY, textStrokePaint);
        canvas.drawText(label, rect.centerX(), textY, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(event);
        }

        float tx = event.getX();
        float ty = event.getY();

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
                if (btnPlay.contains(tx, ty)) {
                    handlePlayClicked();
                    return true;
                } else if (btnOptions.contains(tx, ty)) {
                    audioManager.playClickSound(); // Menu click SFX
                    if (actionListener != null) {
                        actionListener.onShowOptionsDialog();
                    }
                    return true;
                } else if (btnCredits.contains(tx, ty)) {
                    audioManager.playClickSound(); // Menu click SFX
                    if (actionListener != null) {
                        actionListener.onShowCreditsDialog();
                    }
                    return true;
                }
                return true;

            case PLAYING:
                if (btnPause.contains(tx, ty)) {
                    audioManager.playClickSound(); // Pause click SFX
                    pauseGame();
                    return true;
                }
                player.jump();
                audioManager.playJumpSound();
                return true;

            case PAUSED:
                if (btnResume.contains(tx, ty)) {
                    audioManager.playClickSound(); // Resume click SFX
                    countdownTimer = 3.0f;
                    currentState = State.COUNTDOWN;
                    return true;
                } else if (btnRestart.contains(tx, ty)) {
                    audioManager.playClickSound(); // Restart click SFX
                    audioManager.playRandomPlaySound();
                    audioManager.startBgm();
                    startNewGame();
                    return true;
                } else if (btnMenu.contains(tx, ty)) {
                    audioManager.playClickSound(); // Menu click SFX
                    audioManager.stopBgm();
                    currentState = State.MENU;
                    return true;
                }
                return true;

            case COUNTDOWN:
                return true;

            case GAME_OVER:
                if (btnGameOverPlayAgain.contains(tx, ty)) {
                    audioManager.playClickSound(); // Play Again click SFX
                    audioManager.playRandomPlaySound();
                    audioManager.startBgm();
                    startNewGame();
                    return true;
                } else if (btnGameOverMenu.contains(tx, ty)) {
                    audioManager.playClickSound(); // Menu click SFX
                    currentState = State.MENU;
                    return true;
                }
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
