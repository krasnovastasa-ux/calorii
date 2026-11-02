package com.example.caloriecounter.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.graphics.ColorUtils;

public class CalorieRingView extends View {

    private static final float STROKE_WIDTH_DP = 12f;
    private static final float INNER_PADDING_DP = 3f;
    private static final float TRACK_ALPHA = 0.12f;
    private static final float LIGHT_MIX = 0.38f;
    private static final long ANIMATION_DURATION_MS = 650L;
    private static final float START_ANGLE = -90f;
    private static final float FULL_SWEEP = 360f;
    private static final float EPSILON = 0.0001f;
    private static final float FULL_PROGRESS = 0.9999f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    private float progress;
    private float target = -1;
    private ValueAnimator animator;

    public CalorieRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setProgress(int consumed, int goal) {
        float next = goal > 0
                ? Math.max(0, Math.min(1, (float) consumed / goal))
                : 0;

        if (Math.abs(target - next) < EPSILON) {
            return;
        }

        target = next;

        if (animator != null) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(progress, next);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            progress = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float density = getResources().getDisplayMetrics().density;
        float strokeWidth = STROKE_WIDTH_DP * density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = (Math.min(getWidth(), getHeight()) - strokeWidth) / 2f
                - INNER_PADDING_DP * density;

        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        int accent = ActivityPalette.accent(getContext());

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setShader(null);
        paint.setColor(ColorUtils.blendARGB(
                ActivityPalette.surface(getContext()), accent, TRACK_ALPHA));

        canvas.drawOval(bounds, paint);

        if (progress <= 0) {
            return;
        }

        int light = ColorUtils.blendARGB(accent, Color.WHITE, LIGHT_MIX);

        LinearGradient gradient = new LinearGradient(
                cx - radius, cy - radius,
                cx + radius, cy + radius,
                light, accent, Shader.TileMode.CLAMP);
        paint.setShader(gradient);

        if (progress >= FULL_PROGRESS) {
            canvas.drawOval(bounds, paint);
        } else {
            canvas.drawArc(bounds, START_ANGLE, progress * FULL_SWEEP, false, paint);
        }

        paint.setShader(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }
}