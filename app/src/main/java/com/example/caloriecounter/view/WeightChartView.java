package com.example.caloriecounter.view;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import com.example.caloriecounter.model.WeightLog;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class WeightChartView extends View {
    private List<WeightLog> logs;
    private Paint linePaint, pointPaint, gridPaint, textPaint, tooltipPaint;
    private RectF chartBounds;
    private float tooltipX, tooltipY;
    private String tooltipText;
    private boolean showTooltip;

    public interface OnLogLongClickListener {
        void onLongClick(WeightLog log);
    }
    private OnLogLongClickListener onLogLongClick;

    public void setOnLogLongClickListener(OnLogLongClickListener listener) {
        this.onLogLongClick = listener;
    }

    public WeightChartView(Context ctx, @Nullable AttributeSet attrs) {
        super(ctx, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0xFF6C63FF);
        linePaint.setStrokeWidth(4f);
        linePaint.setStyle(Paint.Style.STROKE);

        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setColor(0xFFFFFFFF);
        pointPaint.setStrokeWidth(4f);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0x30888888);
        gridPaint.setStrokeWidth(1f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF666666);
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipPaint.setColor(0xCC000000);

        chartBounds = new RectF();
    }

    public void setData(List<WeightLog> logs) {
        this.logs = logs;
        showTooltip = false;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int pad = 60;
        chartBounds.set(pad, pad, w - pad, h - 30f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (logs == null || logs.size() < 1) return;

        double minW = Double.MAX_VALUE, maxW = Double.MIN_VALUE;
        for (WeightLog l : logs) {
            minW = Math.min(minW, l.weight);
            maxW = Math.max(maxW, l.weight);
        }
        double range = maxW - minW;
        if (range == 0) range = 10;
        double padRange = range * 0.1;
        minW -= padRange;
        maxW += padRange;
        range = maxW - minW;

        int n = logs.size();
        float stepX = chartBounds.width() / (n - 1);
        float[] pointsX = new float[n];
        float[] pointsY = new float[n];

        for (int i = 0; i <= 4; i++) {
            float y = chartBounds.top + (chartBounds.height() * i / 4f);
            canvas.drawLine(chartBounds.left, y, chartBounds.right, y, gridPaint);
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", maxW - (range * i / 4f)),
                    chartBounds.left - 10, y + 8, textPaint);
        }

        Path path = new Path();
        for (int i = 0; i < n; i++) {
            pointsX[i] = chartBounds.left + (i * stepX);
            pointsY[i] = chartBounds.top + chartBounds.height() -
                    (float)((logs.get(i).weight - minW) / range * chartBounds.height());
            if (i == 0) path.moveTo(pointsX[i], pointsY[i]);
            else path.lineTo(pointsX[i], pointsY[i]);

            if (n > 2 && i % 2 == 0) {
                LocalDate d = LocalDate.parse(logs.get(i).logDate);
                canvas.drawText(d.format(DateTimeFormatter.ofPattern("dd.MM")),
                        pointsX[i], chartBounds.bottom + 20, textPaint);
            }
        }
        canvas.drawPath(path, linePaint);

        for (int i = 0; i < n; i++) {
            canvas.drawCircle(pointsX[i], pointsY[i], 8f, linePaint);
            canvas.drawCircle(pointsX[i], pointsY[i], 4f, pointPaint);
        }

        if (showTooltip && tooltipText != null) {
            canvas.drawRect(tooltipX - 50, tooltipY - 60, tooltipX + 50, tooltipY - 20, tooltipPaint);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(32f);
            canvas.drawText(tooltipText, tooltipX, tooltipY - 35, textPaint);
            textPaint.setColor(0xFF666666);
            textPaint.setTextSize(28f);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && logs != null) {
            tooltipX = event.getX();
            int idx = Math.round((tooltipX - chartBounds.left) /
                    (chartBounds.width() / Math.max(1, logs.size() - 1)));

            if (idx >= 0 && idx < logs.size()) {
                WeightLog log = logs.get(idx);

                if (event.getPointerCount() > 1) {
                    if (onLogLongClick != null) onLogLongClick.onLongClick(log);
                    return true;
                }

                double minW = Double.MAX_VALUE, maxW = Double.MIN_VALUE;
                for(WeightLog l : logs){
                    minW = Math.min(minW, l.weight);
                    maxW = Math.max(maxW, l.weight);
                }
                double range = Math.max(1, (maxW - minW) * 1.2);
                tooltipY = chartBounds.top + chartBounds.height() -
                        (float)((log.weight - (minW - range * 0.1)) / range * chartBounds.height());
                tooltipText = log.weight + " kg";
                showTooltip = true;
                invalidate();
                return true;
            }
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            showTooltip = false;
            invalidate();
        }
        return super.onTouchEvent(event);
    }
}