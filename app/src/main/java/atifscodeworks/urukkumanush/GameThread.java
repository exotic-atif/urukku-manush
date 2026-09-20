package atifscodeworks.urukkumanush;

import android.graphics.Canvas;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;

public class GameThread extends Thread {
    private static final String TAG = "GameThread";

    private final SurfaceHolder surfaceHolder;
    private final GameView gameView;
    private volatile boolean running = false;

    public GameThread(SurfaceHolder surfaceHolder, GameView gameView) {
        super("GameThread");
        this.surfaceHolder = surfaceHolder;
        this.gameView = gameView;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();

        while (running) {
            long now = System.nanoTime();
            float dt = (now - lastTime) / 1_000_000_000.0f;
            lastTime = now;

            if (dt > 0.05f) {
                dt = 0.05f;
            }

            Canvas canvas = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        canvas = surfaceHolder.lockHardwareCanvas();
                    } catch (Exception ignored) {
                        canvas = surfaceHolder.lockCanvas();
                    }
                } else {
                    canvas = surfaceHolder.lockCanvas();
                }

                if (canvas != null) {
                    synchronized (surfaceHolder) {
                        gameView.update(dt);
                        gameView.drawGame(canvas);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in game loop", e);
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        Log.e(TAG, "Error unlocking canvas", e);
                    }
                }
            }
        }
    }
}
