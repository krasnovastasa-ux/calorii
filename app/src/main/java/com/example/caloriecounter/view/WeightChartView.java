package com.example.caloriecounter.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.caloriecounter.model.WeightLog;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WeightChartView extends View {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM");
    private static final float POINT_RADIUS_OUTER = 8f;
    private static final float POINT_RADIUS_INNER = 4f;
    private static final float TOOLTIP_HEIGHT = 48f;
    private static final float TOOLTIP_PADDING = 32f;
    private static final float TOOLTIP_MIN_WIDTH = 110f;
    private static final float TOOLTIP_OFFSET_Y = 65f;
    private static final int GRID_STEPS = 5;

    private List<WeightLog> logs = new ArrayList<>();

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF chartBounds = new RectF();

    private boolean showTooltip;
    private float tooltipX;
    private float tooltipY;
    private String tooltipText = "";

    private OnLogLongClickListener longClickListener;

    public interface OnLogLongClickListener {
        void onLogLongClick(WeightLog log);
    }

    public WeightChartView(Context context) {
        super(context);
        init();
    }

    public WeightChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WeightChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setOnLogLongClickListener(OnLogLongClickListener listener) {
        this.longClickListener = listener;
    }

    private void init() {
        linePaint.setStrokeWidth(4f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        pointPaint.setStyle(Paint.Style.FILL);

        innerPointPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(0x30888888);
        gridPaint.setStrokeWidth(1f);

        textPaint.setTextSize(28f);

        titlePaint.setTextSize(36f);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);

        tooltipPaint.setColor(0xB3000000);
        tooltipPaint.setStyle(Paint.Style.FILL);

        tooltipTextPaint.setTextSize(25f);
        tooltipTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<WeightLog> newLogs) {
        logs.clear();
        if (newLogs != null) {
            logs.addAll(newLogs);
        }
        showTooltip = false;
        invalidate();
    }

    public void setLogs(List<WeightLog> newLogs) {
        logs = newLogs != null ? new ArrayList<>(newLogs) : new ArrayList<>();
        showTooltip = false;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float padLeft = 95f;
        float padRight = 20f;
        float padTop = 95f;
        float padBottom = 35f;

        chartBounds.set(padLeft, padTop, w - padRight, h - padBottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        applyPalette();

        titlePaint.setTextSize(36f);
        canvas.drawText("График веса", chartBounds.left, 43f, titlePaint);

        if (logs == null || logs.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Нет данных о весе", getWidth() / 2f, getHeight() / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            return;
        }

        double minWeight = Double.MAX_VALUE;
        double maxWeight = -Double.MAX_VALUE;

        for (WeightLog log : logs) {
            minWeight = Math.min(minWeight, log.weight);
            maxWeight = Math.max(maxWeight, log.weight);
        }

        double range = maxWeight - minWeight;
        if (range < 0.1) {
            range = 1.0;
        }

        double padding = range * 0.15;
        minWeight -= padding;
        maxWeight += padding;

        float chartHeight = chartBounds.height();
        float chartWidth = chartBounds.width();

        drawGrid(canvas, minWeight, maxWeight, chartHeight);

        List<Float> pointsX = new ArrayList<>();
        List<Float> pointsY = new ArrayList<>();
        Path path = new Path();

        int count = logs.size();

        for (int i = 0; i < count; i++) {
            float x = count == 1
                    ? chartBounds.left + chartWidth / 2f
                    : chartBounds.left + chartWidth * i / (count - 1f);

            float y = chartBounds.bottom
                    - (float) ((logs.get(i).weight - minWeight)
                    / (maxWeight - minWeight)) * chartHeight;

            pointsX.add(x);
            pointsY.add(y);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        canvas.drawPath(path, linePaint);

        for (int i = 0; i < count; i++) {
            float x = pointsX.get(i);
            float y = pointsY.get(i);

            canvas.drawCircle(x, y, POINT_RADIUS_OUTER, pointPaint);
            canvas.drawCircle(x, y, POINT_RADIUS_INNER, innerPointPaint);
        }

        textPaint.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < count; i++) {
            try {
                LocalDate date = LocalDate.parse(logs.get(i).logDate);
                canvas.drawText(
                        date.format(DATE_FORMAT),
                        pointsX.get(i),
                        chartBounds.bottom + 25f,
                        textPaint);
            } catch (Exception ignored) {
            }
        }

        textPaint.setTextAlign(Paint.Align.RIGHT);

        if (showTooltip) {
            drawTooltip(canvas);
        }
    }

    private void applyPalette() {
        linePaint.setColor(ActivityPalette.accent(getContext()));
        pointPaint.setColor(ActivityPalette.accent(getContext()));
        innerPointPaint.setColor(ActivityPalette.surface(getContext()));
        titlePaint.setColor(ActivityPalette.text(getContext()));
        textPaint.setColor(ActivityPalette.muted(getContext()));
        tooltipTextPaint.setColor(Color.WHITE);
    }

    private void drawGrid(Canvas canvas,
                          double minWeight,
                          double maxWeight,
                          float chartHeight) {
        textPaint.setTextAlign(Paint.Align.RIGHT);

        for (int i = 0; i <= GRID_STEPS; i++) {
            float y = chartBounds.bottom - (chartHeight / GRID_STEPS) * i;

            canvas.drawLine(chartBounds.left, y, chartBounds.right, y, gridPaint);

            double value = minWeight + (maxWeight - minWeight) * i / (double) GRID_STEPS;

            canvas.drawText(
                    String.format(Locale.getDefault(), "%.1f", value),
                    chartBounds.left - 12f,
                    y + 9f,
                    textPaint);
        }
    }

    private void drawTooltip(Canvas canvas) {
        float tooltipWidth = Math.max(
                TOOLTIP_MIN_WIDTH,
                tooltipTextPaint.measureText(tooltipText) + TOOLTIP_PADDING);

        float tooltipLeft = Math.max(
                8f,
                Math.min(tooltipX - tooltipWidth / 2f,
                        getWidth() - tooltipWidth - 8f));

        float tooltipTop = Math.max(
                8f,
                Math.min(tooltipY - TOOLTIP_OFFSET_Y,
                        getHeight() - TOOLTIP_HEIGHT - 8f));

        RectF tooltipRect = new RectF(
                tooltipLeft,
                tooltipTop,
                tooltipLeft + tooltipWidth,
                tooltipTop + TOOLTIP_HEIGHT);

        canvas.drawRoundRect(tooltipRect, 12f, 12f, tooltipPaint);

        canvas.drawText(
                tooltipText,
                tooltipLeft + tooltipWidth / 2f,
                tooltipTop + 32f,
                tooltipTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }

                if (logs == null || logs.isEmpty()) {
                    return true;
                }

                handleTouchDown(event.getX());
                return true;

            case MotionEvent.ACTION_UP:
                showTooltip = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                invalidate();
                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                showTooltip = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                invalidate();
                return true;
        }

        return true;
    }

    private void handleTouchDown(float touchX) {
        float chartWidth = chartBounds.width();
        int count = logs.size();

        int nearestIndex = 0;
        float minDistance = Float.MAX_VALUE;

        for (int i = 0; i < count; i++) {
            float pointX = count == 1
                    ? chartBounds.left + chartWidth / 2f
                    : chartBounds.left + chartWidth * i / (count - 1f);

            float distance = Math.abs(pointX - touchX);
            if (distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }

        double minWeight = Double.MAX_VALUE;
        double maxWeight = -Double.MAX_VALUE;

        for (WeightLog log : logs) {
            minWeight = Math.min(minWeight, log.weight);
            maxWeight = Math.max(maxWeight, log.weight);
        }

        double range = maxWeight - minWeight;
        if (range < 0.1) {
            range = 1.0;
        }

        double padding = range * 0.15;
        minWeight -= padding;
        maxWeight += padding;

        float pointX = count == 1
                ? chartBounds.left + chartWidth / 2f
                : chartBounds.left + chartWidth * nearestIndex / (count - 1f);

        float pointY = chartBounds.bottom
                - (float) ((logs.get(nearestIndex).weight - minWeight)
                / (maxWeight - minWeight)) * chartBounds.height();

        tooltipX = pointX;
        tooltipY = pointY;
        tooltipText = logs.get(nearestIndex).weight + " кг";
        showTooltip = true;

        invalidate();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}