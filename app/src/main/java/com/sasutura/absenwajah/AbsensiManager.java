package com.sasutura.absenwajah;

import android.content.Context;
import android.graphics.Bitmap; // Import ini akan dihapus jika tidak lagi digunakan
import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class AbsensiManager {

    private static AbsensiManager instance;
    // HAPUS: private FaceRecognitionModel faceModel;
    private List<SiswaTerdaftar> daftarSiswa = new CopyOnWriteArrayList<>();

    private static final float MATCH_THRESHOLD = 0.88f;

    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;

    // Callback interface untuk mengirimkan status duplikat ke AbsensiActivity
    public interface AbsenDuplicateCheckCallback {
        void onDuplicateFound(String nis, String nama, String timestamp);
        void onNoDuplicateFound(SiswaTerdaftar siswa);
        void onError(String message);
    }

    private AbsensiManager(Context context) {
        // HAPUS: faceModel = new FaceRecognitionModel(context.getApplicationContext(), "mobile_face_net.tflite");
        Log.d("AbsensiManager", "AbsensiManager diinisialisasi.");

        db = FirebaseFirestore.getInstance();
        loadStudentsFromFirestore();
    }

    public static synchronized AbsensiManager getInstance(Context context) {
        if (instance == null) {
            instance = new AbsensiManager(context.getApplicationContext());
        }
        return instance;
    }

    private void loadStudentsFromFirestore() {
        if (firestoreListener != null) {
            firestoreListener.remove();
        }

        firestoreListener = db.collection("wajah_terdaftar")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w("AbsensiManager", "Listen failed.", e);
                        return;
                    }

                    if (snapshots != null) {
                        daftarSiswa.clear();
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            try {
                                String id = doc.getString("nis");
                                String nama = doc.getString("nama");
                                String kelas = doc.getString("kelas");
                                List<Double> storedEmbeddingList = (List<Double>) doc.get("embedding");

                                if (id != null && nama != null && kelas != null && storedEmbeddingList != null && storedEmbeddingList.size() == 192) {
                                    float[] embedding = toFloatArray(storedEmbeddingList);
                                    SiswaTerdaftar siswa = new SiswaTerdaftar(id, nama, kelas, embedding);
                                    daftarSiswa.add(siswa);
                                    Log.d("AbsensiManager", "Siswa dimuat dari Firestore: " + siswa.getNama() + " (" + siswa.getId() + ") Kelas: " + siswa.getKelas());
                                } else {
                                    Log.w("AbsensiManager", "Data siswa tidak lengkap atau embedding tidak valid dari Firestore: " + doc.getId());
                                }
                            } catch (ClassCastException cce) {
                                Log.e("AbsensiManager", "Casting embedding dari Firestore gagal untuk " + doc.getId() + ": " + cce.getMessage());
                            }
                        }
                        Log.i("AbsensiManager", "Total siswa terdaftar dari Firestore: " + daftarSiswa.size());
                    }
                });
    }

    public void addSiswa(SiswaTerdaftar siswa) {
        if (siswa.getId() == null || siswa.getId().isEmpty()) {
            Log.e("AbsensiManager", "ID Siswa (NIS) tidak boleh kosong saat menyimpan ke Firestore.");
            return;
        }

        List<Double> embeddingList = floatArrayToDoubleList(siswa.getEmbedding());

        Map<String, Object> data = new HashMap<>();
        data.put("nama", siswa.getNama());
        data.put("nis", siswa.getId());
        data.put("kelas", siswa.getKelas());
        data.put("embedding", embeddingList);

        db.collection("wajah_terdaftar")
                .document(siswa.getId())
                .set(data)
                .addOnSuccessListener(aVoid -> Log.d("AbsensiManager", "Siswa berhasil disimpan ke Firestore: " + siswa.getNama()))
                .addOnFailureListener(e -> Log.e("AbsensiManager", "Gagal menyimpan siswa ke Firestore: " + e.getMessage()));
    }


    // GANTI: Ubah parameter pertama dari Bitmap menjadi float[]
    public void absen(float[] newFaceEmbedding, AbsenDuplicateCheckCallback callback) {
        // HAPUS: Pemeriksaan bitmap karena sekarang menerima float[] langsung
        // if (absenFaceBitmap == null) {
        //     callback.onError("Bitmap wajah absen null.");
        //     return;
        // }

        // HAPUS: Logika mendapatkan embedding dari bitmap
        // float[] newFaceEmbedding = faceModel.getEmbedding(absenFaceBitmap);
        if (newFaceEmbedding == null) { // Pemeriksaan ini tetap relevan jika embedding bisa null dari pemanggil
            callback.onError("Embedding wajah absen null.");
            return;
        }
        if (newFaceEmbedding.length != 192) {
            callback.onError("Ukuran embedding tidak sesuai (expected 192, got " + newFaceEmbedding.length + ")");
            return;
        }

        Log.d("AbsensiManager", "Embedding wajah absen diterima. Ukuran: " + newFaceEmbedding.length);

        SiswaTerdaftar matchedSiswa = null;
        float maxSimilarity = -1.0f;

        if (daftarSiswa.isEmpty()) {
            callback.onError("Daftar siswa kosong di AbsensiManager. Pastikan data dimuat dari Firestore.");
            return;
        }

        for (SiswaTerdaftar siswa : daftarSiswa) {
            float[] registeredEmbedding = siswa.getEmbedding();
            if (registeredEmbedding == null || registeredEmbedding.length != newFaceEmbedding.length) {
                Log.w("AbsensiManager", "Embedding siswa terdaftar tidak valid atau beda ukuran: " + siswa.getNama());
                continue;
            }

            float similarity = getCosineSimilarity(newFaceEmbedding, registeredEmbedding);

            Log.d("AbsensiManager", "Membandingkan wajah absen dengan " + siswa.getNama() + " (" + siswa.getId() + "): Kesamaan = " + String.format("%.4f", similarity));

            if (similarity > maxSimilarity && similarity >= MATCH_THRESHOLD) {
                maxSimilarity = similarity;
                matchedSiswa = siswa;
            }
        }

        if (matchedSiswa != null) {
            Log.i("AbsensiManager", "Wajah cocok dengan: " + matchedSiswa.getNama() + " (Kesamaan tertinggi: " + String.format("%.4f", maxSimilarity) + ")");

            // >>>>>> Lakukan pemeriksaan duplikat di sini <<<<<<
            checkDuplicateAbsence(matchedSiswa, callback); // Panggil metode pemeriksaan duplikat
        } else {
            Log.i("AbsensiManager", "Wajah tidak cocok dengan siswa manapun (tidak mencapai ambang batas " + MATCH_THRESHOLD + ").");
            callback.onNoDuplicateFound(null); // Kirim null jika tidak ada yang cocok
        }
    }

    /**
     * Memeriksa apakah siswa sudah absen pada hari ini di kelas yang sama.
     * Menggunakan struktur absensi/{tanggal}/{kelas_siswa}/{NIS_siswa}.
     */
    private void checkDuplicateAbsence(SiswaTerdaftar siswa, AbsenDuplicateCheckCallback callback) {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = sdfDate.format(new Date());

        // Pastikan nama kelas bersih untuk digunakan sebagai ID koleksi
        String safeKelasSiswa = siswa.getKelas().replace(" ", "_").trim(); // Menambahkan .trim() jika ada spasi di awal/akhir

        // Langsung coba ambil dokumen dengan ID = NIS di jalur baru: absensi/{tanggal}/{kelas_siswa}/{NIS_siswa}
        db.collection("absensi")
                .document(currentDate)
                .collection(safeKelasSiswa) // Sub-koleksi kelas
                .document(siswa.getId())   // Dokumen dengan ID NIS siswa
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Siswa sudah absen hari ini di kelas ini
                        String namaDuplikat = documentSnapshot.getString("nama");
                        Date waktuDuplikat = documentSnapshot.getDate("waktu_absensi");

                        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.getDefault());
                        SimpleDateFormat sdfDateTime = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

                        String formattedTime = (waktuDuplikat != null) ? sdfTime.format(waktuDuplikat) : "Tidak diketahui";
                        String formattedDate = (waktuDuplikat != null) ? sdfDateTime.format(waktuDuplikat) : "Tidak diketahui";

                        callback.onDuplicateFound(siswa.getId(), namaDuplikat, formattedTime + " tanggal " + formattedDate);
                    } else {
                        // Siswa belum absen hari ini, lanjutkan untuk mencatat
                        recordAbsenceLog(siswa.getId(), siswa.getNama(), siswa.getKelas());
                        callback.onNoDuplicateFound(siswa); // Kirim siswa yang berhasil absen
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AbsensiManager", "Error checking duplicate absence: " + e.getMessage());
                    callback.onError("Gagal memeriksa absensi duplikat: " + e.getMessage());
                });
    }

    /**
     * Mencatat log absensi ke koleksi Firestore "absensi"
     * dengan struktur absensi/{tanggal}/{kelas_siswa}/{NIS_siswa}.
     */
    private void recordAbsenceLog(String nis, String namaSiswa, String kelasSiswa) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = sdf.format(new Date());

        DocumentReference dateDocRef = db.collection("absensi").document(currentDate);

        // Pastikan nama kelas tidak memiliki karakter yang tidak valid untuk ID dokumen
        // Mengganti spasi dengan underscore adalah pendekatan yang aman
        String safeKelasSiswa = kelasSiswa.replace(" ", "_").trim(); // Menambahkan .trim() untuk berjaga-jaga

        // Referensi ke dokumen NIS di dalam sub-koleksi kelas
        // absensi/{tanggal}/{kelas_siswa}/{NIS_siswa}
        DocumentReference classAbsenceDocRef = dateDocRef.collection(safeKelasSiswa).document(nis);

        Map<String, Object> logData = new HashMap<>();
        logData.put("nis", nis);
        logData.put("nama", namaSiswa);
        logData.put("kelas", kelasSiswa); // Simpan nama kelas asli sebagai field juga
        logData.put("waktu_absensi", FieldValue.serverTimestamp());
        logData.put("status", "Hadir");
        logData.put("jenis_absen", "Masuk"); // Penting jika nanti ada "Pulang"

        // Menggunakan set() akan menimpa jika dokumen dengan NIS ini sudah ada di jalur ini
        classAbsenceDocRef.set(logData)
                .addOnSuccessListener(aVoid -> Log.d("AbsensiManager", "Log absensi berhasil disimpan untuk NIS: " + nis + " pada tanggal " + currentDate + " di kelas " + kelasSiswa))
                .addOnFailureListener(e -> Log.e("AbsensiManager", "Gagal menyimpan log absensi untuk NIS: " + nis + ": " + e.getMessage()));
    }


    private float[] toFloatArray(List<Double> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i).floatValue();
        }
        return array;
    }

    private List<Double> floatArrayToDoubleList(float[] array) {
        List<Double> list = new ArrayList<>();
        for (float v : array) {
            list.add((double) v);
        }
        return list;
    }

    private float getCosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return -1f;
        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0f || normB == 0f) {
            return 0f;
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    public void cleanup() {
        if (firestoreListener != null) {
            firestoreListener.remove();
            Log.d("AbsensiManager", "Firestore listener dihapus.");
        }
    }
}