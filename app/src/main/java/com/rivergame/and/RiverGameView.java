package com.rivergame.and;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * A self-contained River Dash game view. Touch or drag the boat between the river banks,
 * collecting warm lanterns while avoiding rocks.
 */
public final class RiverGameView extends View {
    private static final int MENU = 0;
    private static final int PLAYING = 1;
    private static final int GAME_OVER = 2;

    private static final int SKY_TOP = Color.rgb(7, 24, 42);
    private static final int SKY_BOTTOM = Color.rgb(16, 74, 94);
    private static final int WATER_TOP = Color.rgb(14, 104, 128);
    private static final int WATER_BOTTOM = Color.rgb(5, 48, 72);
    private static final int GOLD = Color.rgb(252, 211, 77);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path path = new Path();
    private final RectF actionBounds = new RectF();
    private final Random random = new Random();
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final SharedPreferences preferences;
    private final Bitmap googlePlayBitmap;

    private int state = MENU;
    private float width;
    private float height;
    private final float density;
    private float riverLeft;
    private float riverRight;
    private float boatX;
    private float boatY;
    private int score;
    private int bestScore;
    private float spawnTimer;
    private float elapsed;
    private long lastFrameNanos;
    private boolean frameScheduled;
    private boolean active;
    private boolean pressedAction;

    public RiverGameView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        preferences = context.getSharedPreferences("river_dash", Context.MODE_PRIVATE);
        bestScore = preferences.getInt("best_score", 0);
        try (InputStream badge = context.getAssets().open("googleplay.png")) {
            googlePlayBitmap = BitmapFactory.decodeStream(badge);
        } catch (IOException error) {
            throw new IllegalStateException("Could not load googleplay.png", error);
        }
        setFocusable(true);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    @Override
    protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight) {
        width = newWidth;
        height = newHeight;
        riverLeft = width * 0.11f;
        riverRight = width * 0.89f;
        boatY = height * 0.78f;
        boatX = oldWidth == 0 ? width * 0.5f : boatX * newWidth / oldWidth;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawSky(canvas);
        if (state == MENU) {
            drawMenu(canvas);
        } else {
            drawRiver(canvas);
            drawHud(canvas);
            drawObstacles(canvas);
            drawBoat(canvas);
            if (state == GAME_OVER) {
                drawGameOver(canvas);
            }
        }

        if (state == PLAYING && active) {
            scheduleNextFrame();
        }
    }

    private void drawSky(Canvas canvas) {
        paint.setShader(new LinearGradient(0, 0, 0, height, SKY_TOP, SKY_BOTTOM, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(125, 255, 255, 255));
        for (int i = 0; i < 20; i++) {
            float x = (i * 97f + 32f) % Math.max(width, 1f);
            float y = 28f + (i * 47f) % Math.max(height * 0.35f, 1f);
            canvas.drawCircle(x, y, (i % 3 == 0 ? 2.2f : 1.2f) * density, paint);
        }
    }

    private void drawMenu(Canvas canvas) {
        float center = width * 0.5f;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        textPaint.setTextSize(Math.min(42f * density, width * 0.12f));
        textPaint.setColor(Color.WHITE);
        canvas.drawText("RIVER DASH", center, height * 0.28f, textPaint);

        float badgeWidth = Math.min(Math.min(width * 0.58f, 190f * density), height * 0.34f);
        float badgeHeight = badgeWidth * googlePlayBitmap.getHeight() / googlePlayBitmap.getWidth();
        float lowestBadgeTop = height - badgeHeight - 16f * density;
        float buttonHeight = 64f * density;
        float spaceBeforeBadge = 72f * density;
        float buttonTop = Math.min(height * 0.47f, lowestBadgeTop - spaceBeforeBadge - buttonHeight);
        actionBounds.set(width * 0.12f, buttonTop, width * 0.88f, buttonTop + buttonHeight);
        drawRoundedRect(canvas, actionBounds.left, actionBounds.top, actionBounds.right,
                actionBounds.bottom, buttonHeight / 2,
                Color.rgb(251, 191, 36));
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextSize(19f * density);
        textPaint.setColor(Color.rgb(8, 26, 43));
        canvas.drawText("START RUN", center, buttonTop + 41f * density, textPaint);

        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(14f * density);
        textPaint.setColor(Color.argb(205, 231, 250, 247));
        canvas.drawText("Drag to steer  •  Lantern +10", center, actionBounds.bottom + 27f * density,
                textPaint);
        canvas.drawText("Rock ends the run", center, actionBounds.bottom + 48f * density, textPaint);

        float badgeTop = Math.min(lowestBadgeTop, Math.max(height * 0.74f,
                actionBounds.bottom + spaceBeforeBadge));
        RectF badgeBounds = new RectF(center - badgeWidth / 2, badgeTop,
                center + badgeWidth / 2, badgeTop + badgeHeight);
        paint.setAlpha(255);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(googlePlayBitmap, null, badgeBounds, paint);
    }

    private void drawRiver(Canvas canvas) {
        paint.setShader(new LinearGradient(0, 0, 0, height, WATER_TOP, WATER_BOTTOM, Shader.TileMode.CLAMP));
        canvas.drawRect(riverLeft, 0, riverRight, height, paint);
        paint.setShader(null);

        paint.setColor(Color.argb(80, 196, 252, 239));
        paint.setStrokeWidth(2f * density);
        paint.setStyle(Paint.Style.STROKE);
        for (int row = 0; row < 10; row++) {
            float y = 116f * density + row * 90f * density + (elapsed * 90f * density) % (90f * density);
            float start = riverLeft + 22f * density + (row % 2) * 18f * density;
            path.reset();
            path.moveTo(start, y);
            path.quadTo(start + 44f * density, y - 8f * density, start + 86f * density, y);
            path.quadTo(start + 132f * density, y + 8f * density, start + 174f * density, y);
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(Color.rgb(32, 91, 78));
        canvas.drawRect(0, 0, riverLeft, height, paint);
        canvas.drawRect(riverRight, 0, width, height, paint);
        paint.setColor(Color.rgb(51, 121, 89));
        for (int i = 0; i < 14; i++) {
            float x = i % 2 == 0 ? riverLeft - 12f * density : riverRight + 12f * density;
            float y = 85f * density + (i * 101f + elapsed * 26f) % Math.max(height, 1f);
            canvas.drawCircle(x, y, 14f * density, paint);
            paint.setColor(Color.rgb(32, 91, 78));
            canvas.drawRect(x - 2f * density, y, x + 2f * density, y + 26f * density, paint);
            paint.setColor(Color.rgb(51, 121, 89));
        }
    }

    private void drawHud(Canvas canvas) {
        paint.setColor(Color.argb(150, 5, 24, 40));
        canvas.drawRect(0, 0, width, 76f * density, paint);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextSize(13f * density);
        textPaint.setColor(Color.argb(190, 217, 251, 245));
        canvas.drawText("SCORE", 22f * density, 27f * density, textPaint);
        textPaint.setTextSize(26f * density);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(String.valueOf(score), 22f * density, 57f * density, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(13f * density);
        textPaint.setColor(Color.argb(190, 217, 251, 245));
        canvas.drawText("BEST  " + bestScore, width - 22f * density, 35f * density, textPaint);
        canvas.drawText("FLOW  " + (1 + (int) (elapsed / 12f)), width - 22f * density, 57f * density, textPaint);
    }

    private void drawObstacles(Canvas canvas) {
        for (Obstacle obstacle : obstacles) {
            if (obstacle.lantern) {
                paint.setShader(new RadialGradient(obstacle.x, obstacle.y, 34f * density,
                        Color.argb(160, 252, 211, 77), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawCircle(obstacle.x, obstacle.y, 34f * density, paint);
                paint.setShader(null);
                paint.setColor(GOLD);
                canvas.drawCircle(obstacle.x, obstacle.y, 12f * density, paint);
                paint.setColor(Color.rgb(255, 247, 191));
                canvas.drawCircle(obstacle.x - 3f * density, obstacle.y - 3f * density, 4f * density, paint);
                paint.setColor(Color.rgb(128, 81, 32));
                canvas.drawRect(obstacle.x - 9f * density, obstacle.y - 15f * density,
                        obstacle.x + 9f * density, obstacle.y - 10f * density, paint);
                canvas.drawRect(obstacle.x - 2f * density, obstacle.y - 21f * density,
                        obstacle.x + 2f * density, obstacle.y - 15f * density, paint);
            } else {
                paint.setColor(Color.rgb(66, 68, 77));
                canvas.drawCircle(obstacle.x, obstacle.y, 22f * density, paint);
                paint.setColor(Color.rgb(91, 96, 104));
                canvas.drawCircle(obstacle.x - 7f * density, obstacle.y - 6f * density, 9f * density, paint);
                paint.setColor(Color.rgb(38, 53, 62));
                canvas.drawCircle(obstacle.x + 9f * density, obstacle.y + 7f * density, 8f * density, paint);
            }
        }
    }

    private void drawBoat(Canvas canvas) {
        float bob = (float) Math.sin(elapsed * 5.0f) * 2.2f * density;
        float x = boatX;
        float y = boatY + bob;
        paint.setColor(Color.argb(90, 0, 20, 30));
        canvas.drawOval(new RectF(x - 34f * density, y + 16f * density,
                x + 34f * density, y + 29f * density), paint);
        path.reset();
        path.moveTo(x - 34f * density, y - 8f * density);
        path.lineTo(x + 34f * density, y - 8f * density);
        path.lineTo(x + 21f * density, y + 19f * density);
        path.quadTo(x, y + 30f * density, x - 21f * density, y + 19f * density);
        path.close();
        paint.setColor(Color.rgb(246, 139, 84));
        canvas.drawPath(path, paint);
        paint.setColor(Color.rgb(255, 185, 102));
        canvas.drawRoundRect(new RectF(x - 19f * density, y - 19f * density,
                x + 19f * density, y + 3f * density), 8f * density, 8f * density, paint);
        paint.setColor(Color.rgb(36, 59, 69));
        canvas.drawCircle(x, y - 24f * density, 9f * density, paint);
        paint.setColor(Color.rgb(250, 205, 106));
        canvas.drawRect(x - 13f * density, y - 32f * density,
                x + 13f * density, y - 27f * density, paint);
    }

    private void drawGameOver(Canvas canvas) {
        paint.setColor(Color.argb(205, 3, 18, 31));
        canvas.drawRect(0, 0, width, height, paint);
        float center = width * 0.5f;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextSize(34f * density);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("CURRENT ENDS", center, height * 0.34f, textPaint);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(16f * density);
        textPaint.setColor(Color.argb(220, 217, 251, 245));
        canvas.drawText("You gathered " + score + " points", center, height * 0.40f, textPaint);
        canvas.drawText("Best run  " + bestScore, center, height * 0.44f, textPaint);
        float buttonTop = height * 0.53f;
        actionBounds.set(width * 0.18f, buttonTop, width * 0.82f, buttonTop + 62f * density);
        drawRoundedRect(canvas, actionBounds.left, actionBounds.top, actionBounds.right,
                actionBounds.bottom, 31f * density, GOLD);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextSize(18f * density);
        textPaint.setColor(Color.rgb(8, 26, 43));
        canvas.drawText("RUN AGAIN", center, buttonTop + 40f * density, textPaint);
    }

    private void drawRoundedRect(Canvas canvas, float left, float top, float right, float bottom,
                                 float radius, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
    }

    private void startGame() {
        state = PLAYING;
        score = 0;
        elapsed = 0f;
        spawnTimer = 0.2f;
        obstacles.clear();
        boatX = width * 0.5f;
        lastFrameNanos = System.nanoTime();
        scheduleNextFrame();
        invalidate();
    }

    void setActive(boolean active) {
        this.active = active;
        lastFrameNanos = System.nanoTime();
        pressedAction = false;
        invalidate();
    }

    private void endGame() {
        state = GAME_OVER;
        if (score > bestScore) {
            bestScore = score;
            preferences.edit().putInt("best_score", bestScore).apply();
        }
        invalidate();
    }

    private void update(float deltaSeconds) {
        elapsed += deltaSeconds;
        spawnTimer -= deltaSeconds;
        float speed = (240f + Math.min(elapsed * 11f, 250f)) * density;
        if (spawnTimer <= 0f) {
            spawnTimer = Math.max(0.42f, 0.95f - elapsed * 0.008f);
            float safeWidth = riverRight - riverLeft - 52f * density;
            float x = riverLeft + 26f * density + random.nextFloat() * Math.max(safeWidth, 1f);
            obstacles.add(new Obstacle(x, -28f * density, random.nextFloat() > 0.43f));
        }
        float boatRadius = 31f * density;
        Iterator<Obstacle> iterator = obstacles.iterator();
        while (iterator.hasNext()) {
            Obstacle obstacle = iterator.next();
            obstacle.y += speed * deltaSeconds;
            float hitDistance = (obstacle.lantern ? 24f : 28f) * density + boatRadius * 0.45f;
            if (Math.abs(obstacle.x - boatX) < hitDistance && Math.abs(obstacle.y - boatY) < hitDistance) {
                if (obstacle.lantern) {
                    score += 10;
                    iterator.remove();
                    continue;
                }
                endGame();
                return;
            }
            if (obstacle.y > height + 42f * density) {
                iterator.remove();
            }
        }
    }

    private void scheduleNextFrame() {
        if (!frameScheduled) {
            frameScheduled = true;
            postOnAnimation(() -> {
                frameScheduled = false;
                if (state != PLAYING || !active) {
                    return;
                }
                long now = System.nanoTime();
                float delta = (now - lastFrameNanos) / 1_000_000_000f;
                lastFrameNanos = now;
                update(Math.min(delta, 0.05f));
                invalidate();
            });
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (state == PLAYING && action != MotionEvent.ACTION_CANCEL) {
            boatX = clamp(event.getX(), riverLeft + 33f * density, riverRight - 33f * density);
            invalidate();
        } else if (action == MotionEvent.ACTION_DOWN) {
            pressedAction = actionBounds.contains(event.getX(), event.getY());
        } else if (action == MotionEvent.ACTION_UP) {
            if (pressedAction && actionBounds.contains(event.getX(), event.getY())) {
                performClick();
            }
            pressedAction = false;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            pressedAction = false;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (state == PLAYING) {
            return false;
        }
        startGame();
        return true;
    }

    private float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Obstacle {
        float x;
        float y;
        boolean lantern;

        Obstacle(float x, float y, boolean lantern) {
            this.x = x;
            this.y = y;
            this.lantern = lantern;
        }
    }
}
