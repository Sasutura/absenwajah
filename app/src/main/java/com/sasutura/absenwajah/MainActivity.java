package com.sasutura.absenwajah;

import android.Manifest;
import android.content.Intent;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import com.google.gson.Gson; // Import Gson
import com.google.gson.reflect.TypeToken; // Import TypeToken

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private PreviewView previewView;
    private FaceOverlayView faceOverlayView;
    private TextView instructionTextView;
    private Button switchCameraButton;

    private FaceDetector faceDetector;
    private FaceEmbedder faceEmbedder;

    private int cameraLensFacing = CameraSelector.LENS_FACING_FRONT;
    private int poseStep = 0;
    private final int TOTAL_POSES = 5;
    private final float[][] poseEmbeddings = new float[TOTAL_POSES][]; // Untuk menyimpan 5 embedding terpisah
    private boolean isPoseLocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Pastikan ini mengarah ke layout yang benar, mungkin activity_registrasi

        try {
            faceEmbedder = new FaceEmbedder(this);
        } catch (IOException e) {
            Toast.makeText(this, "Gagal memuat model.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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
                ? CameraSelector.LENS_FACING_BACK // Gunakan back jika kamera depan. Jika ingin switch ke front, ganti jadi LENS_FACING_FRONT
                : CameraSelector.LENS_FACING_FRONT; // Jika sebelumnya belakang, switch ke depan.
        poseStep = 0;
        isPoseLocked = false;
        // Bersihkan array poseEmbeddings saat toggle kamera
        for (int i = 0; i < TOTAL_POSES; i++) {
            poseEmbeddings[i] = null;
        }
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

        if (isPoseLocked) { // Jika sedang memproses atau menunggu delay, abaikan frame lain
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

                    boolean validPose = false;
                    String currentPoseInstruction = "";

                    // Logika validasi pose yang lebih ketat untuk pendaftaran
                    switch (poseStep) {
                        case 0: // Lurus ke depan
                            validPose = Math.abs(yaw) < 15 && Math.abs(pitch) < 15;
                            currentPoseInstruction = "lurus ke depan";
                            break;
                        case 1: // Agak menoleh ke kiri
                            validPose = yaw < -20 && Math.abs(pitch) < 15; // Contoh: yaw < -20 (lebih kiri)
                            currentPoseInstruction = "sedikit ke kiri";
                            break;
                        case 2: // Agak menoleh ke kanan
                            validPose = yaw > 20 && Math.abs(pitch) < 15; // Contoh: yaw > 20 (lebih kanan)
                            currentPoseInstruction = "sedikit ke kanan";
                            break;
                        case 3: // Agak mendongak
                            validPose = pitch < -20 && Math.abs(yaw) < 15; // Contoh: pitch < -20 (lebih dongak)
                            currentPoseInstruction = "sedikit ke atas";
                            break;
                        case 4: // Agak menunduk
                            validPose = pitch > 20 && Math.abs(yaw) < 15; // Contoh: pitch > 20 (lebih nunduk)
                            currentPoseInstruction = "sedikit ke bawah";
                            break;
                        default:
                            validPose = false;
                            break;
                    }

                    if (!validPose) {
                        instructionTextView.setText("📸 Pose " + (poseStep + 1) + ": Arahkan wajah " + currentPoseInstruction);
                        imageProxy.close();
                        return;
                    }

                    // Wajah valid dan pose sesuai
                    isPoseLocked = true; // Kunci pemrosesan sampai frame ini selesai dan delay
                    instructionTextView.setText("✅ Pose " + (poseStep + 1) + " terekam!");

                    try {
                        Bitmap fullBitmap = ImageUtils.toBitmap(mediaImage);
                        Rect boundingBox = face.getBoundingBox();

                        if (boundingBox.width() < 100 || boundingBox.height() < 100) {
                            instructionTextView.setText("📏 Dekatkan wajah ke kamera.\nUlangi pose ke-" + (poseStep + 1));
                            isPoseLocked = false;
                            imageProxy.close();
                            return;
                        }

                        // Rotasi dan crop bitmap
                        Matrix matrix = new Matrix();
                        // Mirroring untuk kamera depan (jika kameraLensFacing adalah LENS_FACING_FRONT)
                        if (cameraLensFacing == CameraSelector.LENS_FACING_FRONT) {
                            matrix.postScale(-1, 1, fullBitmap.getWidth() / 2f, fullBitmap.getHeight() / 2f);
                        }
                        // Rotasi berdasarkan informasi gambar dari kamera
                        matrix.postRotate(rotation, fullBitmap.getWidth() / 2f, fullBitmap.getHeight() / 2f);

                        // Buat bitmap yang sudah dirotasi/mirror
                        Bitmap rotatedBitmap = Bitmap.createBitmap(fullBitmap, 0, 0,
                                fullBitmap.getWidth(), fullBitmap.getHeight(), matrix, true);

                        // Pastikan boundingBox disesuaikan dengan bitmap yang sudah dirotasi/mirror
                        // Ini adalah bagian yang paling tricky. Jika cropping salah, pastikan koordinat boundingBox
                        // diremap dengan benar setelah rotasi/mirror atau lakukan cropping pada bitmap original
                        // sebelum rotasi jika rotasinya hanya untuk display.
                        // Untuk saat ini, kita asumsikan boundingBox dari ML Kit sudah relatif terhadap InputImage
                        // dan kita coba adaptasinya.
                        Rect padded = new Rect(
                                Math.max(0, boundingBox.left - (int) (0.15 * boundingBox.width())),
                                Math.max(0, boundingBox.top - (int) (0.15 * boundingBox.height())),
                                Math.min(rotatedBitmap.getWidth(), boundingBox.right + (int) (0.15 * boundingBox.width())),
                                Math.min(rotatedBitmap.getHeight(), boundingBox.bottom + (int) (0.15 * boundingBox.height()))
                        );

                        // Pastikan padding tidak melebihi batas bitmap
                        padded.left = Math.max(0, padded.left);
                        padded.top = Math.max(0, padded.top);
                        padded.right = Math.min(rotatedBitmap.getWidth(), padded.right);
                        padded.bottom = Math.min(rotatedBitmap.getHeight(), padded.bottom);

                        if (padded.width() <= 0 || padded.height() <= 0) {
                            instructionTextView.setText("❌ Area wajah tidak valid untuk cropping.\nUlangi pose ke-" + (poseStep + 1));
                            isPoseLocked = false;
                            imageProxy.close();
                            return;
                        }

                        Bitmap cropped = Bitmap.createBitmap(rotatedBitmap, padded.left, padded.top,
                                padded.width(), padded.height());

                        float[] embedding = faceEmbedder.getFaceEmbedding(cropped);
                        if (embedding == null || embedding.length != 192) {
                            instructionTextView.setText("❌ Gagal membaca wajah.\nUlangi pose ke-" + (poseStep + 1));
                            isPoseLocked = false;
                            imageProxy.close();
                            return;
                        }

                        poseEmbeddings[poseStep] = embedding; // Simpan embedding untuk pose saat ini

                        if (poseStep == TOTAL_POSES - 1) {
                            instructionTextView.setText("✅ Semua pose terekam!");
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                ArrayList<float[]> allPoseEmbeddingsList = new ArrayList<>();
                                for (float[] emb : poseEmbeddings) {
                                    allPoseEmbeddingsList.add(emb);
                                }
                                goToIdentityForm(allPoseEmbeddingsList); // Kirim semua embedding
                            }, 1000);
                        } else {
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                poseStep++;
                                isPoseLocked = false;
                                updateInstructionText();
                            }, 1000); // Tunggu 1 detik sebelum minta pose berikutnya
                        }

                    } catch (Exception e) {
                        instructionTextView.setText("❌ Terjadi kesalahan saat proses wajah.\nUlangi pose ke-" + (poseStep + 1));
                        Log.e("FaceCapture", "Error: ", e);
                        isPoseLocked = false;
                    } finally {
                        imageProxy.close();
                    }
                })
                .addOnFailureListener(e -> {
                    instructionTextView.setText("❌ Gagal deteksi wajah.");
                    isPoseLocked = false; // Reset lock agar bisa mencoba lagi
                    imageProxy.close();
                });
    }

    // Mengubah parameter untuk menerima ArrayList<float[]>
    private void goToIdentityForm(ArrayList<float[]> embeddings) {
        Intent intent = new Intent(this, IsiIdentitasActivity.class);
        // Mengkonversi ArrayList<float[]> menjadi ArrayList<String> JSON untuk dikirim melalui Intent
        ArrayList<String> embeddingsJsonList = new ArrayList<>();
        Gson gson = new Gson(); // Membutuhkan library Gson

        for (float[] emb : embeddings) {
            embeddingsJsonList.add(gson.toJson(emb));
        }
        intent.putStringArrayListExtra("all_embeddings_json", embeddingsJsonList);
        startActivity(intent);
        finish(); // Selesaikan MainActivity setelah beralih
    }

    private void updateInstructionText() {
        String[] poses = {
                "lurus ke depan", "sedikit ke kiri", "sedikit ke kanan", "sedikit ke atas", "sedikit ke bawah"
        };
        // Pastikan poseStep tidak melebihi batas array
        if (poseStep < poses.length) {
            instructionTextView.setText("📸 Pose " + (poseStep + 1) + ": Arahkan wajah " + poses[poseStep]);
        } else {
            instructionTextView.setText("Selesai mengumpulkan pose.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}