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

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CaloriesChartView extends View {

    private static final String PERIOD_WEEK = "week";
    private static final String PERIOD_MONTH = "month";
    private static final String PERIOD_YEAR = "year";

    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM.yy");

    private static final int GRID_STEPS = 5;
    private static final int MONTHS_IN_YEAR = 12;
    private static final float POINT_RADIUS_OUTER = 8f;
    private static final float POINT_RADIUS_INNER = 4f;
    private static final float TOOLTIP_HEIGHT = 48f;
    private static final float TOOLTIP_PADDING = 32f;
    private static final float TOOLTIP_MIN_WIDTH = 110f;
    private static final float TOOLTIP_OFFSET_Y = 65f;
    private static final int Y_STEP = 500;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF chartBounds = new RectF();

    private final Map<String, Integer> data = new TreeMap<>();

    private final List<Integer> values = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<String> fullDates = new ArrayList<>();

    private final List<Float> pointsX = new ArrayList<>();
    private final List<Float> pointsY = new ArrayList<>();

    private String period = PERIOD_WEEK;

    private boolean showTooltip;
    private int selectedIndex = -1;

    private float tooltipX;
    private float tooltipY;

    private String tooltipText = "";

    public CaloriesChartView(Context context) {
        super(context);
        init();
    }

    public CaloriesChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CaloriesChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
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
        textPaint.setTextAlign(Paint.Align.RIGHT);

        titlePaint.setTextSize(36f);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);

        tooltipPaint.setColor(0xB3000000);
        tooltipPaint.setStyle(Paint.Style.FILL);

        tooltipTextPaint.setTextSize(25f);
        tooltipTextPaint.setTextAlign(Paint.Align.CENTER);

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setData(Map<String, Integer> newData) {
        data.clear();

        if (newData != null) {
            for (Map.Entry<String, Integer> entry : newData.entrySet()) {
                if (entry.getKey() != null
                        && entry.getValue() != null
                        && entry.getValue() > 0) {
                    data.put(entry.getKey(), entry.getValue());
                }
            }
        }

        showTooltip = false;
        selectedIndex = -1;

        rebuildPoints();
        invalidate();
    }

    public void setPeriod(String newPeriod) {
        if (!PERIOD_WEEK.equals(newPeriod)
                && !PERIOD_MONTH.equals(newPeriod)
                && !PERIOD_YEAR.equals(newPeriod)) {
            return;
        }

        period = newPeriod;

        showTooltip = false;
        selectedIndex = -1;

        rebuildPoints();
        invalidate();
    }

    private void rebuildPoints() {
        values.clear();
        labels.clear();
        fullDates.clear();

        LocalDate today = LocalDate.now();

        if (PERIOD_YEAR.equals(period)) {
            rebuildYearPoints(today);
        } else {
            rebuildDayPoints(today);
        }

        pointsX.clear();
        pointsY.clear();

        showTooltip = false;
        selectedIndex = -1;

        requestLayout();
        invalidate();
    }

    private void rebuildYearPoints(LocalDate today) {
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth firstMonth = currentMonth.minusMonths(MONTHS_IN_YEAR - 1);

        Map<YearMonth, Integer> monthlyData = new TreeMap<>();

        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            try {
                LocalDate date = LocalDate.parse(entry.getKey());
                YearMonth month = YearMonth.from(date);

                if (!month.isBefore(firstMonth) && !month.isAfter(currentMonth)) {
                    monthlyData.merge(month, entry.getValue(), Integer::sum);
                }
            } catch (Exception ignored) {
            }
        }

        for (int i = MONTHS_IN_YEAR - 1; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            Integer calories = monthlyData.get(month);

            if (calories != null && calories > 0) {
                values.add(calories);
                labels.add(month.format(MONTH_FORMAT));
                fullDates.add(month.toString());
            }
        }
    }

    private void rebuildDayPoints(LocalDate today) {
        int days = PERIOD_WEEK.equals(period) ? 7 : 30;
        LocalDate startDate = today.minusDays(days - 1);

        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            Integer calories = data.get(date.toString());

            if (calories != null && calories > 0) {
                values.add(calories);
                labels.add(date.format(LABEL_FORMAT));
                fullDates.add(date.format(FULL_FORMAT));
            }
        }
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
        canvas.drawText("График калорий", chartBounds.left, 43f, titlePaint);

        if (values.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Нет данных о калориях", getWidth() / 2f, getHeight() / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            return;
        }

        int maxValue = 0;
        for (int value : values) {
            maxValue = Math.max(maxValue, value);
        }
        int maxY = Math.max(Y_STEP, ((maxValue + Y_STEP - 1) / Y_STEP) * Y_STEP);

        float chartHeight = chartBounds.height();
        float chartWidth = chartBounds.width();

        drawGrid(canvas, maxY, chartHeight);

        pointsX.clear();
        pointsY.clear();

        int count = values.size();
        Path path = new Path();

        for (int i = 0; i < count; i++) {
            float x = count == 1
                    ? chartBounds.left + chartWidth / 2f
                    : chartBounds.left + chartWidth * i / (count - 1f);

            float y = chartBounds.bottom - (values.get(i) / (float) maxY) * chartHeight;

            pointsX.add(x);
            pointsY.add(y);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        canvas.drawPath(path, linePaint);
        drawPoints(canvas, count);
        drawDateLabels(canvas, count);

        textPaint.setTextAlign(Paint.Align.RIGHT);

        if (showTooltip && selectedIndex >= 0 && selectedIndex < values.size()) {
            tooltipText = values.get(selectedIndex) + " ккал";
            drawTooltip(canvas);
        }
    }

    private void applyPalette() {
        linePaint.setColor(ActivityPalette.accent(getContext()));
        pointPaint.setColor(ActivityPalette.accent(getContext()));
        innerPointPaint.setColor(ActivityPalette.surface(getContext()));
        titlePaint.setColor(ActivityPalette.text(getContext()));
        textPaint.setColor(ActivityPalette.muted(getContext()));
    }

    private void drawGrid(Canvas canvas, int maxY, float chartHeight) {
        textPaint.setTextAlign(Paint.Align.RIGHT);

        for (int i = 0; i <= GRID_STEPS; i++) {
            float y = chartBounds.bottom - (chartHeight / GRID_STEPS) * i;

            canvas.drawLine(chartBounds.left, y, chartBounds.right, y, gridPaint);

            int labelValue = maxY * i / GRID_STEPS;
            canvas.drawText(
                    String.valueOf(labelValue),
                    chartBounds.left - 12f,
                    y + 9f,
                    textPaint);
        }
    }

    private void drawPoints(Canvas canvas, int count) {
        for (int i = 0; i < count; i++) {
            float x = pointsX.get(i);
            float y = pointsY.get(i);

            canvas.drawCircle(x, y, POINT_RADIUS_OUTER, pointPaint);
            canvas.drawCircle(x, y, POINT_RADIUS_INNER, innerPointPaint);
        }
    }

    private void drawDateLabels(Canvas canvas, int count) {
        textPaint.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < count; i++) {
            canvas.drawText(
                    labels.get(i),
                    pointsX.get(i),
                    chartBounds.bottom + 25f,
                    textPaint);
        }
    }

    private void drawTooltip(Canvas canvas) {
        float textWidth = tooltipTextPaint.measureText(tooltipText);
        float tooltipWidth = Math.max(TOOLTIP_MIN_WIDTH, textWidth + TOOLTIP_PADDING);

        float tooltipLeft = Math.max(
                8f,
                Math.min(tooltipX - tooltipWidth / 2f, getWidth() - tooltipWidth - 8f));

        float tooltipTop = Math.max(
                8f,
                Math.min(tooltipY - TOOLTIP_OFFSET_Y, getHeight() - TOOLTIP_HEIGHT - 8f));

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
                if (pointsX.isEmpty()) {
                    return true;
                }
                handleTouchDown(event.getX());
                return true;

            case MotionEvent.ACTION_UP:
                showTooltip = false;
                selectedIndex = -1;
                invalidate();
                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                showTooltip = false;
                selectedIndex = -1;
                invalidate();
                return true;
        }

        return true;
    }

    private void handleTouchDown(float touchX) {
        int nearestIndex = 0;
        float minDistance = Float.MAX_VALUE;

        for (int i = 0; i < pointsX.size(); i++) {
            float distance = Math.abs(pointsX.get(i) - touchX);
            if (distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }

        selectedIndex = nearestIndex;
        tooltipX = pointsX.get(nearestIndex);
        tooltipY = pointsY.get(nearestIndex);
        tooltipText = values.get(nearestIndex) + " ккал";
        showTooltip = true;

        invalidate();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}