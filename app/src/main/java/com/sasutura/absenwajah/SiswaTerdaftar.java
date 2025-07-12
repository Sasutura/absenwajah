package com.sasutura.absenwajah;

import java.io.Serializable; // Penting untuk passing object antar Activity via Intent
import java.util.Arrays; // Untuk debugging jika perlu melihat isi array

/**
 * Kelas model untuk merepresentasikan data siswa yang terdaftar,
 * termasuk embedding wajahnya.
 */
public class SiswaTerdaftar implements Serializable { // Implement Serializable agar bisa dikirim antar Activity

    private String id; // ID unik siswa (misal: NIM, NISN)
    private String nama;
    private String kelas; // <<<<<< INI FIELD BARU UNTUK KELAS
    private float[] embedding; // Embedding wajah yang sudah dirata-ratakan (ukuran 192 dimensi)

    // <<<<<< KONSTRUKTOR YANG DIPERBARUI UNTUK MENERIMA 'kelas' >>>>>>
    public SiswaTerdaftar(String id, String nama, String kelas, float[] embedding) {
        this.id = id;
        this.nama = nama;
        this.kelas = kelas; // <<<<<< INISIALISASI FIELD KELAS
        this.embedding = embedding;
    }

    // --- Getter Methods ---
    public String getId() {
        return id;
    }

    public String getNama() {
        return nama;
    }

    // <<<<<< GETTER YANG DIPERBAIKI UNTUK 'kelas' >>>>>>
    public String getKelas() {
        return kelas; // Mengembalikan nilai dari field 'kelas', BUKAN 'nama'
    }

    public float[] getEmbedding() {
        return embedding;
    }

    // --- Opsional: Setter Methods jika diperlukan untuk modifikasi data ---
    // Jika Anda ingin bisa mengubah kelas setelah objek dibuat:
    // public void setKelas(String kelas) {
    //     this.kelas = kelas;
    // }

    @Override
    public String toString() {
        // Hindari mencetak seluruh array embedding ke log/string secara langsung, bisa sangat panjang
        // Cukup tampilkan ID, nama, dan kelas untuk representasi sederhana
        return "SiswaTerdaftar{" +
                "id='" + id + '\'' +
                ", nama='" + nama + '\'' +
                ", kelas='" + kelas + '\'' + // <<<<<< Tambahkan 'kelas' ke toString
                '}';
    }

    // --- Metode untuk membandingkan objek SiswaTerdaftar (opsional tapi bagus) ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SiswaTerdaftar that = (SiswaTerdaftar) o;
        return id.equals(that.id); // Siswa dianggap sama jika ID-nya sama
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}