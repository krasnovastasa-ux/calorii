
package com.example.caloriecounter.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.example.caloriecounter.model.WeightLog;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Оставь здесь тот же import WeightLog,
// который был в твоём исходном файле.

public class WeightChartView extends View {

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

    private boolean showTooltip = false;

    private float tooltipX;
    private float tooltipY;

    private String tooltipText = "";

    private OnLogLongClickListener longClickListener;

    public interface OnLogLongClickListener {
        void onLogLongClick(WeightLog log);
    }

    public void setOnLogLongClickListener(OnLogLongClickListener listener) {
        this.longClickListener = listener;
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

    private void init() {
        linePaint.setColor(Color.parseColor("#7563F6"));
        linePaint.setStrokeWidth(4f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        pointPaint.setColor(Color.parseColor("#7563F6"));
        pointPaint.setStyle(Paint.Style.FILL);

        innerPointPaint.setColor(Color.WHITE);
        innerPointPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(0x30888888);
        gridPaint.setStrokeWidth(1f);

        textPaint.setColor(Color.GRAY);
        textPaint.setTextSize(28f);

        titlePaint.setColor(Color.parseColor("#25273A"));
        titlePaint.setTextSize(36f);
        titlePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        // Полупрозрачный фон подсказки
        tooltipPaint.setColor(0xB3000000);
        tooltipPaint.setStyle(Paint.Style.FILL);

        tooltipTextPaint.setColor(Color.WHITE);
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

        chartBounds.set(
                padLeft,
                padTop,
                w - padRight,
                h - padBottom
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        titlePaint.setTextSize(36f);

        canvas.drawText(
                "График веса",
                chartBounds.left,
                43f,
                titlePaint
        );

        if (logs == null || logs.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);

            canvas.drawText(
                    "Нет данных о весе",
                    getWidth() / 2f,
                    getHeight() / 2f,
                    textPaint
            );

            textPaint.setTextAlign(Paint.Align.RIGHT);
            return;
        }

        // Находим минимальный и максимальный вес
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

        // Горизонтальная сетка и подписи веса
        textPaint.setTextAlign(Paint.Align.RIGHT);

        for (int i = 0; i <= 5; i++) {
            float y = chartBounds.bottom - (chartHeight / 5f) * i;

            canvas.drawLine(
                    chartBounds.left,
                    y,
                    chartBounds.right,
                    y,
                    gridPaint
            );

            double value = minWeight
                    + (maxWeight - minWeight) * i / 5.0;

            canvas.drawText(
                    String.format(Locale.getDefault(), "%.1f", value),
                    chartBounds.left - 12f,
                    y + 9f,
                    textPaint
            );
        }

        List<Float> pointsX = new ArrayList<>();
        List<Float> pointsY = new ArrayList<>();

        Path path = new Path();

        int count = logs.size();

        // Рассчитываем координаты точек
        for (int i = 0; i < count; i++) {
            float x;

            if (count == 1) {
                x = chartBounds.left + chartWidth / 2f;
            } else {
                x = chartBounds.left + chartWidth * i / (count - 1f);
            }

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

        // Линия графика
        canvas.drawPath(path, linePaint);

        // Точки графика
        for (int i = 0; i < count; i++) {
            float x = pointsX.get(i);
            float y = pointsY.get(i);

            canvas.drawCircle(x, y, 8f, pointPaint);
            canvas.drawCircle(x, y, 4f, innerPointPaint);
        }

        // Подписи дат
        textPaint.setTextAlign(Paint.Align.CENTER);

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("dd.MM");

        for (int i = 0; i < count; i++) {
            try {
                LocalDate date = LocalDate.parse(logs.get(i).logDate);

                canvas.drawText(
                        date.format(formatter),
                        pointsX.get(i),
                        chartBounds.bottom + 25f,
                        textPaint
                );

            } catch (Exception ignored) {
            }
        }

        textPaint.setTextAlign(Paint.Align.RIGHT);

        // Подсказка выбранной точки
        if (showTooltip) {
            float tooltipWidth = Math.max(
                    110f,
                    tooltipTextPaint.measureText(tooltipText) + 32f
            );

            float tooltipHeight = 48f;

            float tooltipLeft = Math.max(
                    8f,
                    Math.min(
                            tooltipX - tooltipWidth / 2f,
                            getWidth() - tooltipWidth - 8f
                    )
            );

            float tooltipTop = Math.max(
                    8f,
                    Math.min(
                            tooltipY - 65f,
                            getHeight() - tooltipHeight - 8f
                    )
            );

            RectF tooltipRect = new RectF(
                    tooltipLeft,
                    tooltipTop,
                    tooltipLeft + tooltipWidth,
                    tooltipTop + tooltipHeight
            );

            canvas.drawRoundRect(
                    tooltipRect,
                    12f,
                    12f,
                    tooltipPaint
            );

            canvas.drawText(
                    tooltipText,
                    tooltipLeft + tooltipWidth / 2f,
                    tooltipTop + 32f,
                    tooltipTextPaint
            );
        }
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

                float touchX = event.getX();

                int nearestIndex = 0;
                float minDistance = Float.MAX_VALUE;
                float chartWidth = chartBounds.width();

                for (int i = 0; i < logs.size(); i++) {
                    float pointX;

                    if (logs.size() == 1) {
                        pointX = chartBounds.left + chartWidth / 2f;
                    } else {
                        pointX = chartBounds.left
                                + chartWidth * i / (logs.size() - 1f);
                    }

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

                float pointX;

                if (logs.size() == 1) {
                    pointX = chartBounds.left + chartWidth / 2f;
                } else {
                    pointX = chartBounds.left
                            + chartWidth * nearestIndex / (logs.size() - 1f);
                }

                float pointY = chartBounds.bottom
                        - (float) ((logs.get(nearestIndex).weight - minWeight)
                        / (maxWeight - minWeight)) * chartBounds.height();

                tooltipX = pointX;
                tooltipY = pointY;
                tooltipText = logs.get(nearestIndex).weight + " кг";
                showTooltip = true;

                invalidate();
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

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}

