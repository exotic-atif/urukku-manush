package atifscodeworks.urukkumanush;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;

import java.io.InputStream;

public class BackgroundScroller {
    private static final String TAG = "BackgroundScroller";

    private final Context context;
    private Bitmap originalBitmap;
    private Bitmap flippedBitmap;

    private int screenWidth;
    private int screenHeight;
    private int tileWidth;
    private int tileHeight;

    private float scrollX = 0f;
    private float baseScrollSpeed;
    private float currentScrollSpeed;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    public BackgroundScroller(Context context) {
        this.context = context;
    }

    public void init(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        this.baseScrollSpeed = width * 0.15f;
        this.currentScrollSpeed = baseScrollSpeed;

        try (InputStream is = context.getAssets().open("imgs/bg.png")) {
            Bitmap raw = BitmapFactory.decodeStream(is);
            if (raw != null) {
                float scale = (float) height / raw.getHeight();
                tileWidth = (int) Math.ceil(raw.getWidth() * scale);
                tileHeight = height;

                originalBitmap = Bitmap.createScaledBitmap(raw, tileWidth, tileHeight, true);

                // Horizontally flipped version
                Matrix matrix = new Matrix();
                matrix.postScale(-1f, 1f);
                flippedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, tileWidth, tileHeight, matrix, true);

                if (raw != originalBitmap) {
                    raw.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading bg.png from assets", e);
        }
    }

    public void update(float dt) {
        if (tileWidth <= 0) return;
        scrollX += currentScrollSpeed * dt;
        float cycleLength = 2.0f * tileWidth;
        if (scrollX >= cycleLength) {
            scrollX -= cycleLength;
        }
    }

    public void draw(Canvas canvas) {
        if (originalBitmap == null || flippedBitmap == null || tileWidth <= 0) return;

        float startX = -scrollX;
        while (startX > 0) {
            startX -= (2.0f * tileWidth);
        }

        int tileIndex = 0;
        float currentX = startX;
        while (currentX < screenWidth) {
            Bitmap toDraw = (tileIndex % 2 == 0) ? originalBitmap : flippedBitmap;
            canvas.drawBitmap(toDraw, currentX, 0, paint);
            currentX += tileWidth;
            tileIndex++;
        }
    }

    public void setSpeedMultiplier(float multiplier) {
        this.currentScrollSpeed = baseScrollSpeed * multiplier;
    }
}
