package com.sasutura.absenwajah; // Ganti ini dengan package aplikasi Anda yang sebenarnya!

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class CustomScannerOverlayView extends View {

    private Paint overlayPaint; // Untuk area gelap transparan di luar kotak
    private Paint borderPaint;  // Untuk garis border hijau
    private Path path;          // Digunakan jika ingin bentuk non-persegi (misal lingkaran)
    private Rect scannerRect;   // Posisi dan ukuran kotak fokus

    // === Tambahkan atau pastikan variabel ini ada ===
    private int currentBorderColor; // Variabel untuk menyimpan warna border saat ini

    public CustomScannerOverlayView(Context context) {
        super(context);
        init();
    }

    public CustomScannerOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomScannerOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Inisialisasi Paint untuk overlay gelap
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(Color.BLACK);
        overlayPaint.setAlpha(150); // Opasitas 150 dari 255 (sekitar 58% gelap). Sesuaikan ini!
        overlayPaint.setStyle(Paint.Style.FILL);
        overlayPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        // Inisialisasi Paint untuk border
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStrokeWidth(5f); // Ketebalan border
        borderPaint.setStyle(Paint.Style.STROKE); // Hanya garis, bukan mengisi area

        // === Inisialisasi warna border awal ===
        currentBorderColor = Color.GREEN; // Set warna default, misalnya hijau
        borderPaint.setColor(currentBorderColor); // Terapkan warna awal ke Paint

        path = new Path(); // Inisialisasi Path
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int centerX = w / 2;
        int centerY = h / 2;
        int boxWidth = (int) (w * 0.7);
        int boxHeight = (int) (h * 0.6);

        float targetAspectRatio = 1.0f / 1.2f;
        if ((float)boxWidth / boxHeight > targetAspectRatio) {
            boxWidth = (int) (boxHeight * targetAspectRatio);
        } else if ((float)boxWidth / boxHeight < targetAspectRatio) {
            boxHeight = (int) (boxWidth / targetAspectRatio);
        }

        scannerRect = new Rect(
                centerX - boxWidth / 2,
                centerY - boxHeight / 2,
                centerX + boxWidth / 2,
                centerY + boxHeight / 2
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int saveLayer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);

        canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);

        if (scannerRect != null) {
            canvas.drawRect(scannerRect, overlayPaint);
        }

        canvas.restoreToCount(saveLayer);

        if (scannerRect != null) {
            canvas.drawRect(scannerRect, borderPaint);
        }
    }

    // Getter untuk mendapatkan Rect dari kotak scanner
    public Rect getScannerRect() {
        return scannerRect;
    }

    // === Tambahkan atau pastikan metode setter ini ada ===
    public void setBorderColor(int color) {
        if (this.currentBorderColor != color) { // Hanya update jika warna berbeda
            this.currentBorderColor = color;
            borderPaint.setColor(color);
            invalidate(); // PENTING: Perintahkan View untuk menggambar ulang dirinya
        }
    }
}