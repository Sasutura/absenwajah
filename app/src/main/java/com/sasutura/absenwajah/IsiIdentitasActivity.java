package com.sasutura.absenwajah;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IsiIdentitasActivity extends AppCompatActivity {

    private EditText editNama, editNis, editKelas;
    private Button btnSimpan;
    private float[] embedding;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_isi_identitas);

        editNama = findViewById(R.id.edit_nama);
        editNis = findViewById(R.id.edit_nis);
        editKelas = findViewById(R.id.edit_kelas);
        btnSimpan = findViewById(R.id.button_simpan);

        embedding = getIntent().getFloatArrayExtra("embedding");

        if (embedding == null || embedding.length != 192) {
            Toast.makeText(this, "Embedding tidak valid", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        btnSimpan.setOnClickListener(v -> simpanData());
    }

    private void simpanData() {
        String nama = editNama.getText().toString().trim();
        String nis = editNis.getText().toString().trim();
        String kelas = editKelas.getText().toString().trim();

        if (nama.isEmpty() || nis.isEmpty() || kelas.isEmpty()) {
            Toast.makeText(this, "Mohon lengkapi semua kolom", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("wajah_terdaftar")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        List<Double> stored = (List<Double>) doc.get("embedding");
                        if (stored == null || stored.size() != 192) continue;

                        float[] storedEmbedding = toFloatArray(stored);
                        double similarity = getCosineSimilarity(embedding, storedEmbedding);

                        if (similarity > 0.92) {
                            String existingNama = doc.getString("nama");
                            String existingNis = doc.getString("nis");

                            Toast.makeText(this, "⚠️ Wajah sudah terdaftar sebagai " + existingNama + " (NIS: " + existingNis + ")", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }

                    db.collection("wajah_terdaftar")
                            .document(nis)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    String namaTerdaftar = documentSnapshot.getString("nama");
                                    Toast.makeText(this, "⚠️ NIS " + nis + " sudah terdaftar atas nama: " + namaTerdaftar, Toast.LENGTH_LONG).show();
                                } else {
                                    Map<String, Object> data = new HashMap<>();
                                    data.put("nama", nama);
                                    data.put("nis", nis);
                                    data.put("kelas", kelas);
                                    data.put("embedding", floatArrayToList(embedding));

                                    db.collection("wajah_terdaftar")
                                            .document(nis)
                                            .set(data)
                                            .addOnSuccessListener(unused -> {
                                                Toast.makeText(this, "✅ Data berhasil disimpan!", Toast.LENGTH_SHORT).show();
                                                finish();
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(this, "❌ Gagal menyimpan data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                Log.e("FIREBASE", "Gagal simpan", e);
                                            });
                                }
                            });
                });
    }

    private List<Float> floatArrayToList(float[] array) {
        List<Float> list = new ArrayList<>();
        for (float v : array) {
            list.add(v);
        }
        return list;
    }

    private float[] toFloatArray(List<Double> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i).floatValue();
        }
        return array;
    }

    private float getCosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return -1f;
        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
}
