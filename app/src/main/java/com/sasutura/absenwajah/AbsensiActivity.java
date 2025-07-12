package com.sasutura.absenwajah;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class AbsensiActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private PreviewView previewView;
    private FaceOverlayView faceOverlayView;
    private TextView instructionTextView;
    private Button switchCameraButton;

    private FaceDetector faceDetector;
    private FaceEmbedder faceEmbedder;
    private AbsensiManager absensiManager;

    private boolean isVerifying = false;
    private int cameraLensFacing = CameraSelector.LENS_FACING_FRONT;
    private final List<float[]> embeddingBuffer = new ArrayList<>();
    private static final int REQUIRED_EMBEDDINGS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_absensi);

        try {
            faceEmbedder = new FaceEmbedder(this);
        } catch (IOException e) {
            Toast.makeText(this, "Gagal memuat model pengenal wajah", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        absensiManager = AbsensiManager.getInstance(getApplicationContext());

        previewView = findViewById(R.id.previewView);
        faceOverlayView = findViewById(R.id.faceOverlayView);
        instructionTextView = findViewById(R.id.instructionTextView);
        switchCameraButton = findViewById(R.id.switchCameraButton);
        switchCameraButton.setOnClickListener(v -> toggleCamera());

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();

        faceDetector = FaceDetection.getClient(options);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void toggleCamera() {
        cameraLensFacing = (cameraLensFacing == CameraSelector.LENS_FACING_FRONT)
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;
        isVerifying = false;
        embeddingBuffer.clear();
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::processImageProxy);

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(cameraLensFacing)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                faceOverlayView.setCameraLensFacing(cameraLensFacing);
                updateInstructionText();

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Gagal memulai kamera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        InputImage image = InputImage.fromMediaImage(mediaImage, rotation);

        if (isVerifying) {
            imageProxy.close();
            return;
        }

        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        instructionTextView.setText("❌ Wajah tidak terdeteksi.");
                        imageProxy.close();
                        return;
                    }

                    Face face = faces.get(0);
                    float yaw = face.getHeadEulerAngleY();
                    float pitch = face.getHeadEulerAngleX();

                    boolean validPose = Math.abs(yaw) < 15 && Math.abs(pitch) < 15;
                    if (!validPose) {
                        instructionTextView.setText("📏 Hadapkan wajah lebih lurus ke kamera.");
                        imageProxy.close();
                        return;
                    }

                    isVerifying = true;
                    instructionTextView.setText("✅ Wajah terdeteksi.");

                    try {
                        Bitmap fullBitmap = ImageUtils.toBitmap(mediaImage);
                        Rect boundingBox = face.getBoundingBox();

                        if (boundingBox.width() < 100 || boundingBox.height() < 100) {
                            instructionTextView.setText("📏 Dekatkan wajah ke kamera.");
                            resetAbsenUI(1000);
                            imageProxy.close();
                            return;
                        }

                        Matrix matrix = new Matrix();
                        if (cameraLensFacing == CameraSelector.LENS_FACING_FRONT) {
                            matrix.postScale(-1, 1, boundingBox.centerX(), boundingBox.centerY());
                        }

                        Bitmap rotatedBitmap = Bitmap.createBitmap(fullBitmap, 0, 0,
                                fullBitmap.getWidth(), fullBitmap.getHeight(), matrix, true);

                        Rect padded = new Rect(
                                Math.max(0, boundingBox.left - (int) (0.15 * boundingBox.width())),
                                Math.max(0, boundingBox.top - (int) (0.15 * boundingBox.height())),
                                Math.min(rotatedBitmap.getWidth(), boundingBox.right + (int) (0.15 * boundingBox.width())),
                                Math.min(rotatedBitmap.getHeight(), boundingBox.bottom + (int) (0.15 * boundingBox.height()))
                        );

                        Bitmap cropped = Bitmap.createBitmap(rotatedBitmap, padded.left, padded.top,
                                padded.width(), padded.height());

                        float[] embedding = faceEmbedder.getFaceEmbedding(cropped);
                        if (embedding == null || embedding.length != 192) {
                            instructionTextView.setText("❌ Gagal membaca wajah.");
                            resetAbsenUI(1500);
                            imageProxy.close();
                            return;
                        }

                        embeddingBuffer.add(embedding);
                        instructionTextView.setText("⏳ Memindai wajah... " + embeddingBuffer.size() + "/" + REQUIRED_EMBEDDINGS);

                        if (embeddingBuffer.size() < REQUIRED_EMBEDDINGS) {
                            resetAbsenUI(800);
                            imageProxy.close();
                            return;
                        }

                        float[] average = averageEmbeddings(embeddingBuffer);
                        embeddingBuffer.clear();

                        absensiManager.absen(average, new AbsensiManager.AbsenDuplicateCheckCallback() {
                            @Override
                            public void onDuplicateFound(String nis, String nama, String timestamp) {
                                instructionTextView.setText("⚠️ Sudah absen pukul " + timestamp);
                                resetAbsenUI(2000);
                            }

                            @Override
                            public void onNoDuplicateFound(SiswaTerdaftar siswa) {
                                if (siswa != null) {
                                    instructionTextView.setText("✅ " + siswa.getNama() + " berhasil absen!");
                                } else {
                                    instructionTextView.setText("❌ Wajah tidak dikenali.");
                                }
                                resetAbsenUI(1500);
                            }

                            @Override
                            public void onError(String message) {
                                instructionTextView.setText("❌ Error: " + message);
                                resetAbsenUI(2000);
                            }
                        });
                    } catch (Exception e) {
                        instructionTextView.setText("❌ Terjadi kesalahan.");
                        resetAbsenUI(1500);
                    } finally {
                        imageProxy.close();
                    }
                })
                .addOnFailureListener(e -> {
                    instructionTextView.setText("❌ Deteksi wajah gagal.");
                    resetAbsenUI(1500);
                    imageProxy.close();
                });
    }

    private void resetAbsenUI(long delayMillis) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isVerifying = false;
            embeddingBuffer.clear();
            updateInstructionText();
            faceOverlayView.setBorderColor(Color.BLUE);
        }, delayMillis);
    }

    private void updateInstructionText() {
        instructionTextView.setText("📸 Posisikan wajah lurus ke kamera.");
    }

    private float[] averageEmbeddings(List<float[]> embeddings) {
        int size = embeddings.get(0).length;
        float[] avg = new float[size];
        for (float[] emb : embeddings) {
            for (int i = 0; i < size; i++) {
                avg[i] += emb[i];
            }
        }
        for (int i = 0; i < size; i++) {
            avg[i] /= embeddings.size();
        }
        return avg;
    }
}
