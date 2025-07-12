package com.sasutura.absenwajah;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import androidx.camera.core.CameraSelector;

public class FaceOverlayView extends View {
    private int currentCameraLensFacing = CameraSelector.LENS_FACING_FRONT;
    private Paint targetPaint;
    private float targetSizeRatio = 0.6f; // 60% dari lebar/tinggi view
    private int borderColor = Color.YELLOW; // Default: kuning

    private RectF targetCircleRect; // Untuk menyimpan area target

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public int imageWidth = 0;
    public int imageHeight = 0;
    private int rotationDegrees = 0;

    public void setPreviewSize(int width, int height, int rotationDegrees) {
        this.imageWidth = width;
        this.imageHeight = height;
        this.rotationDegrees = rotationDegrees;
        invalidate(); // gambar ulang
    }


    public void setCameraLensFacing(@CameraSelector.LensFacing int lensFacing) {
        this.currentCameraLensFacing = lensFacing;
        invalidate();
    }
    private void init() {
        targetPaint = new Paint();
        targetPaint.setColor(borderColor);
        targetPaint.setStyle(Paint.Style.STROKE);
        targetPaint.setStrokeWidth(8f);
        targetPaint.setAntiAlias(true);
    }

    public void setBorderColor(int color) {
        this.borderColor = color;
        targetPaint.setColor(color);
        invalidate();
    }

    public void setTargetSizeRatio(float ratio) {
        this.targetSizeRatio = ratio;
        targetCircleRect = null; // Reset
        invalidate();
    }

    public RectF getTargetCircleRect() {
        if (targetCircleRect == null) {
            calculateTargetRect();
        }
        return targetCircleRect;
    }

    private void calculateTargetRect() {
        float width = getWidth();
        float height = getHeight();

        float size = Math.min(width, height) * targetSizeRatio;
        float left = (width - size) / 2f;
        float top = (height - size) / 2f;
        targetCircleRect = new RectF(left, top, left + size, top + size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (targetCircleRect == null) {
            calculateTargetRect();
        }

        // Gambar lingkaran target
        canvas.drawOval(targetCircleRect, targetPaint);
    }
}
