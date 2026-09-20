package atifscodeworks.urukkumanush;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

public class Obstacle {
    private static final Random RANDOM = new Random();

    public static class PipeTheme {
        public final int bodyColor;
        public final int lightColor;
        public final int darkColor;
        public final int borderColor;

        public PipeTheme(int bodyColor, int lightColor, int darkColor, int borderColor) {
            this.bodyColor = bodyColor;
            this.lightColor = lightColor;
            this.darkColor = darkColor;
            this.borderColor = borderColor;
        }
    }

    private static final PipeTheme[] THEMES = new PipeTheme[]{
            // Classic Emerald
            new PipeTheme(Color.rgb(46, 204, 113), Color.rgb(169, 223, 191), Color.rgb(39, 174, 96), Color.rgb(20, 90, 50)),
            // Cyber Cyan
            new PipeTheme(Color.rgb(0, 229, 255), Color.rgb(224, 247, 250), Color.rgb(0, 176, 255), Color.rgb(0, 96, 100)),
            // Solar Gold
            new PipeTheme(Color.rgb(243, 156, 18), Color.rgb(250, 215, 160), Color.rgb(214, 137, 16), Color.rgb(126, 81, 9)),
            // Royal Purple
            new PipeTheme(Color.rgb(155, 89, 182), Color.rgb(232, 218, 239), Color.rgb(142, 68, 173), Color.rgb(74, 35, 90)),
            // Crimson Flame
            new PipeTheme(Color.rgb(231, 76, 60), Color.rgb(250, 219, 216), Color.rgb(192, 57, 43), Color.rgb(100, 30, 22))
    };

    private float x;
    private float width;
    private float speed;

    private float baseGapY;
    private float gapY;
    private float gapHeight;

    // Up and down oscillation (for score >= 50)
    private boolean oscillating = false;
    private float oscillationTimer = 0f;
    private float oscillationSpeed = 1.8f;
    private float oscillationAmplitude;

    private int screenWidth;
    private int screenHeight;

    private boolean scored = false;
    private final PipeTheme theme;

    private final RectF topPipeBody = new RectF();
    private final RectF topPipeCap = new RectF();
    private final RectF bottomPipeBody = new RectF();
    private final RectF bottomPipeCap = new RectF();

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Obstacle(int screenWidth, int screenHeight, float startX, float speed, boolean oscillating) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.x = startX;
        this.speed = speed;
        this.oscillating = oscillating;

        this.theme = THEMES[RANDOM.nextInt(THEMES.length)];

        // Dynamic width: between 7.5% and 10.5% of screen width
        float widthPercent = 0.075f + (RANDOM.nextFloat() * 0.03f);
        this.width = screenWidth * widthPercent;

        // Dynamic gap height: between 30% and 40% of screen height
        float gapPercent = 0.30f + (RANDOM.nextFloat() * 0.10f);
        this.gapHeight = screenHeight * gapPercent;

        // Oscillation amplitude (smooth vertical float)
        this.oscillationAmplitude = screenHeight * 0.09f;
        this.oscillationSpeed = 1.6f + (RANDOM.nextFloat() * 0.6f);
        this.oscillationTimer = RANDOM.nextFloat() * (float) (Math.PI * 2);

        // Gap position: keep reasonable clearance from top and bottom taking oscillation into account
        float minGapY = screenHeight * 0.24f + (gapHeight / 2.0f);
        float maxGapY = screenHeight * 0.76f - (gapHeight / 2.0f);
        this.baseGapY = minGapY + (RANDOM.nextFloat() * Math.max(10f, (maxGapY - minGapY)));
        this.gapY = baseGapY;

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Math.max(3f, screenHeight * 0.005f));
        borderPaint.setColor(theme.borderColor);

        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(theme.lightColor);
        highlightPaint.setAlpha(120);

        updateRects();
    }

    private void updateRects() {
        float capHeight = screenHeight * 0.05f;
        float capExtraWidth = width * 0.15f;

        float topBottom = gapY - (gapHeight / 2.0f);
        topPipeBody.set(x, 0, x + width, Math.max(0, topBottom - capHeight));
        topPipeCap.set(x - capExtraWidth, Math.max(0, topBottom - capHeight), x + width + capExtraWidth, topBottom);

        float bottomTop = gapY + (gapHeight / 2.0f);
        bottomPipeCap.set(x - capExtraWidth, bottomTop, x + width + capExtraWidth, bottomTop + capHeight);
        bottomPipeBody.set(x, bottomTop + capHeight, x + width, screenHeight);
    }

    public void update(float dt) {
        x -= speed * dt;

        if (oscillating) {
            oscillationTimer += dt * oscillationSpeed;
            gapY = baseGapY + (float) Math.sin(oscillationTimer) * oscillationAmplitude;
        }

        updateRects();
    }

    public void draw(Canvas canvas) {
        // Gradient shader for 3D pipe look
        LinearGradient gradient = new LinearGradient(
                x, 0, x + width, 0,
                new int[]{theme.lightColor, theme.bodyColor, theme.darkColor},
                new float[]{0.0f, 0.4f, 1.0f},
                Shader.TileMode.CLAMP
        );
        fillPaint.setShader(gradient);

        // Draw top pipe
        if (topPipeBody.bottom > 0) {
            canvas.drawRect(topPipeBody, fillPaint);
            canvas.drawRect(topPipeBody, borderPaint);
        }
        if (topPipeCap.bottom > 0) {
            canvas.drawRoundRect(topPipeCap, 8f, 8f, fillPaint);
            canvas.drawRoundRect(topPipeCap, 8f, 8f, borderPaint);
        }

        // Draw bottom pipe
        if (bottomPipeCap.top < screenHeight) {
            canvas.drawRoundRect(bottomPipeCap, 8f, 8f, fillPaint);
            canvas.drawRoundRect(bottomPipeCap, 8f, 8f, borderPaint);
        }
        if (bottomPipeBody.top < screenHeight) {
            canvas.drawRect(bottomPipeBody, fillPaint);
            canvas.drawRect(bottomPipeBody, borderPaint);
        }

        fillPaint.setShader(null);
    }

    public boolean collidesWith(RectF playerBounds) {
        return RectF.intersects(topPipeBody, playerBounds) ||
                RectF.intersects(topPipeCap, playerBounds) ||
                RectF.intersects(bottomPipeCap, playerBounds) ||
                RectF.intersects(bottomPipeBody, playerBounds);
    }

    public boolean isOffScreen() {
        return (x + width + (width * 0.2f) < 0);
    }

    public boolean checkScore(float playerX) {
        if (!scored && playerX > (x + (width / 2.0f))) {
            scored = true;
            return true;
        }
        return false;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void setOscillating(boolean oscillating) {
        this.oscillating = oscillating;
    }

    public float getX() {
        return x;
    }
}
