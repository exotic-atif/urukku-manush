package atifscodeworks.urukkumanush;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import java.io.InputStream;

public class Player {
    private static final String TAG = "Player";

    private final Context context;
    private Bitmap headBitmap;
    private Bitmap scaledBitmap;

    private float x;
    private float y;
    private float velocityY = 0f;
    private float rotationAngle = 0f;

    private float gravity;
    private float jumpVelocity;
    private float maxFallVelocity;
    private float playerWidth;
    private float playerHeight;
    private float halfHitWidth;
    private float halfHitHeight;

    private int screenWidth;
    private int screenHeight;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix drawMatrix = new Matrix();
    private final RectF hitBox = new RectF();

    public Player(Context context) {
        this.context = context;
    }

    public void init(int width, int height, String headAssetName) {
        this.screenWidth = width;
        this.screenHeight = height;

        // Player height scaled to screen height
        this.playerHeight = height * 0.14f;
        // Default 3:2 ratio
        this.playerWidth = this.playerHeight * (512f / 344f);

        this.x = width * 0.22f;
        this.y = height * 0.45f;
        this.velocityY = 0f;
        this.rotationAngle = 0f;

        // Tight hitbox: 56% width, 62% height to remove empty whitespace
        this.halfHitWidth = (this.playerWidth * 0.56f) / 2.0f;
        this.halfHitHeight = (this.playerHeight * 0.62f) / 2.0f;

        // Physics
        this.gravity = height * 2.2f;
        this.jumpVelocity = -height * 0.75f;
        this.maxFallVelocity = height * 1.4f;

        loadHeadBitmap(headAssetName);
    }

    public void loadHeadBitmap(String headAssetName) {
        try (InputStream is = context.getAssets().open("imgs/" + headAssetName)) {
            headBitmap = BitmapFactory.decodeStream(is);
            if (headBitmap != null && playerHeight > 0) {
                float aspect = (float) headBitmap.getWidth() / headBitmap.getHeight();
                playerWidth = playerHeight * aspect;
                this.halfHitWidth = (this.playerWidth * 0.56f) / 2.0f;
                this.halfHitHeight = (this.playerHeight * 0.62f) / 2.0f;
                scaledBitmap = Bitmap.createScaledBitmap(headBitmap, (int) playerWidth, (int) playerHeight, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading head bitmap: " + headAssetName, e);
            if (!"head_1.png".equals(headAssetName)) {
                loadHeadBitmap("head_1.png");
            }
        }
    }

    public void jump() {
        velocityY = jumpVelocity;
        rotationAngle = -28f;
    }

    public void update(float dt) {
        velocityY += gravity * dt;
        if (velocityY > maxFallVelocity) {
            velocityY = maxFallVelocity;
        }

        y += velocityY * dt;

        float targetAngle = (velocityY < 0) ? -28f : Math.min(75f, (velocityY / maxFallVelocity) * 75f);
        rotationAngle += (targetAngle - rotationAngle) * Math.min(1.0f, dt * 10f);

        // Keep inside screen bounds
        if (y - halfHitHeight < 0) {
            y = halfHitHeight;
            velocityY = 0;
        }

        // Tight hitbox removing the empty whitespace around head
        hitBox.set(x - halfHitWidth, y - halfHitHeight, x + halfHitWidth, y + halfHitHeight);
    }

    public boolean checkGroundCollision() {
        return (y + halfHitHeight >= screenHeight);
    }

    public void reset() {
        this.y = screenHeight * 0.45f;
        this.velocityY = 0f;
        this.rotationAngle = 0f;
    }

    public void draw(Canvas canvas) {
        drawAt(canvas, x, y, rotationAngle);
    }

    public void drawAt(Canvas canvas, float drawX, float drawY, float rotation) {
        if (scaledBitmap == null || scaledBitmap.isRecycled()) return;

        drawMatrix.reset();
        drawMatrix.postTranslate(-playerWidth / 2.0f, -playerHeight / 2.0f);
        drawMatrix.postRotate(rotation);
        drawMatrix.postTranslate(drawX, drawY);

        canvas.drawBitmap(scaledBitmap, drawMatrix, paint);
    }

    public RectF getHitBox() {
        return hitBox;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getPlayerWidth() {
        return playerWidth;
    }

    public float getPlayerHeight() {
        return playerHeight;
    }
}
