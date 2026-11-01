
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CaloriesChartView extends View {

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

    private String period = "week";

    private boolean showTooltip = false;
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
        textPaint.setTextAlign(Paint.Align.RIGHT);

        titlePaint.setColor(Color.parseColor("#25273A"));
        titlePaint.setTextSize(36f);
        titlePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        // Полупрозрачный фон подсказки
        tooltipPaint.setColor(0xB3000000);
        tooltipPaint.setStyle(Paint.Style.FILL);

        tooltipTextPaint.setColor(Color.WHITE);
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
        if (newPeriod == null) return;

        if (!newPeriod.equals("week")
                && !newPeriod.equals("month")
                && !newPeriod.equals("year")) {
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

        DateTimeFormatter labelFormatter =
                DateTimeFormatter.ofPattern("dd.MM");

        DateTimeFormatter fullFormatter =
                DateTimeFormatter.ofPattern("dd.MM.yyyy");

        LocalDate today = LocalDate.now();

        if (period.equals("year")) {

            YearMonth currentMonth = YearMonth.from(today);

            Map<YearMonth, Integer> monthlyData = new TreeMap<>();

            for (Map.Entry<String, Integer> entry : data.entrySet()) {
                try {
                    LocalDate date = LocalDate.parse(entry.getKey());
                    YearMonth month = YearMonth.from(date);

                    YearMonth firstMonth = currentMonth.minusMonths(11);

                    if (!month.isBefore(firstMonth)
                            && !month.isAfter(currentMonth)) {

                        monthlyData.put(
                                month,
                                monthlyData.getOrDefault(month, 0)
                                        + entry.getValue()
                        );
                    }

                } catch (Exception ignored) {
                }
            }

            for (int i = 11; i >= 0; i--) {
                YearMonth month = currentMonth.minusMonths(i);

                Integer calories = monthlyData.get(month);

                if (calories != null && calories > 0) {
                    values.add(calories);
                    labels.add(month.format(
                            DateTimeFormatter.ofPattern("MM.yy")
                    ));
                    fullDates.add(month.toString());
                }
            }

        } else {

            int days = period.equals("week") ? 7 : 30;

            LocalDate startDate = today.minusDays(days - 1);

            for (int i = 0; i < days; i++) {
                LocalDate date = startDate.plusDays(i);

                String dateKey = date.toString();
                Integer calories = data.get(dateKey);

                if (calories != null && calories > 0) {
                    values.add(calories);
                    labels.add(date.format(labelFormatter));
                    fullDates.add(date.format(fullFormatter));
                }
            }
        }

        pointsX.clear();
        pointsY.clear();

        showTooltip = false;
        selectedIndex = -1;

        requestLayout();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Больше места слева для четырёхзначных чисел
        float padLeft = 95f;

        float padRight = 20f;

        // Дополнительное расстояние после заголовка
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

        // Заголовок графика
        titlePaint.setTextSize(36f);
        canvas.drawText(
                "График калорий",
                chartBounds.left,
                43f,
                titlePaint
        );

        if (values.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(
                    "Нет данных о калориях",
                    getWidth() / 2f,
                    getHeight() / 2f,
                    textPaint
            );
            textPaint.setTextAlign(Paint.Align.RIGHT);
            return;
        }

        int maxValue = 0;

        for (int value : values) {
            maxValue = Math.max(maxValue, value);
        }

        int maxY = Math.max(500, ((maxValue + 499) / 500) * 500);

        float chartHeight = chartBounds.height();
        float chartWidth = chartBounds.width();

        // Горизонтальная сетка и подписи слева
        for (int i = 0; i <= 5; i++) {
            float y = chartBounds.bottom
                    - (chartHeight / 5f) * i;

            canvas.drawLine(
                    chartBounds.left,
                    y,
                    chartBounds.right,
                    y,
                    gridPaint
            );

            int labelValue = maxY * i / 5;

            textPaint.setTextAlign(Paint.Align.RIGHT);

            canvas.drawText(
                    String.valueOf(labelValue),
                    chartBounds.left - 12f,
                    y + 9f,
                    textPaint
            );
        }

        pointsX.clear();
        pointsY.clear();

        int count = values.size();

        Path path = new Path();

        for (int i = 0; i < count; i++) {

            float x;

            if (count == 1) {
                x = chartBounds.left + chartWidth / 2f;
            } else {
                x = chartBounds.left
                        + chartWidth * i / (count - 1f);
            }

            float y = chartBounds.bottom
                    - (values.get(i) / (float) maxY) * chartHeight;

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

        // Подписи дат снизу
        textPaint.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < count; i++) {
            float x = pointsX.get(i);

            canvas.drawText(
                    labels.get(i),
                    x,
                    chartBounds.bottom + 25f,
                    textPaint
            );
        }

        textPaint.setTextAlign(Paint.Align.RIGHT);

        // Подсказка выбранной точки
        if (showTooltip && selectedIndex >= 0
                && selectedIndex < values.size()) {

            tooltipText = values.get(selectedIndex) + " ккал";

            float textWidth = tooltipTextPaint.measureText(tooltipText);

            float tooltipWidth = Math.max(110f, textWidth + 32f);
            float tooltipHeight = 48f;

            // Не даём подсказке выйти за левую или правую границу
            float tooltipLeft = Math.max(
                    8f,
                    Math.min(
                            tooltipX - tooltipWidth / 2f,
                            getWidth() - tooltipWidth - 8f
                    )
            );

            float tooltipRight = tooltipLeft + tooltipWidth;

            // Не даём подсказке выйти за верхнюю или нижнюю границу
            float tooltipTop = Math.max(
                    8f,
                    Math.min(
                            tooltipY - 65f,
                            getHeight() - tooltipHeight - 8f
                    )
            );

            float tooltipBottom = tooltipTop + tooltipHeight;

            RectF tooltipRect = new RectF(
                    tooltipLeft,
                    tooltipTop,
                    tooltipRight,
                    tooltipBottom
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

                if (pointsX.isEmpty()) {
                    return true;
                }

                float touchX = event.getX();

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

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
