package com.sasutura.absenwajah;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Button btnRekamWajah = findViewById(R.id.btn_rekam_wajah);
        Button btnAbsensi = findViewById(R.id.btn_absensi);

        btnRekamWajah.setOnClickListener(v -> {
            // Pindah ke EnrollmentActivity untuk rekam wajah
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            startActivity(intent);
        });

        btnAbsensi.setOnClickListener(v -> {
            // Pindah ke AbsensiActivity untuk absen
            Intent intent = new Intent(HomeActivity.this, AbsensiActivity.class);
            startActivity(intent);
        });
    }
}