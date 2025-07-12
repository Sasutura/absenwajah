plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services) // <-- Gunakan alias yang baru (tanpa apply false)
}

android {
    namespace = "com.sasutura.absenwajah"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sasutura.absenwajah"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Jika Anda menggunakan GPU delegate, tambahkan ini
    aaptOptions {
        noCompress ("tflite") // Mencegah kompresi file tflite
    }
}

dependencies {

    // Dependensi yang sudah ada di proyek Anda:
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout) // Sudah ada di bawah juga, bisa dihapus salah satu. Saya biarkan untuk saat ini.
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation ("com.google.code.gson:gson:2.10.1")

    val camerax_version = "1.3.3" // Versi terbaru stabil per Juli 2025
    implementation ("androidx.camera:camera-core:${camerax_version}")
    implementation ("androidx.camera:camera-camera2:${camerax_version}")
    implementation ("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation ("androidx.camera:camera-view:${camerax_version}")

//    implementation ("androidx.constraintlayout:constraintlayout:2.1.4") // Duplikat dengan yang di atas, bisa dihapus salah satu.

    implementation ("com.google.mlkit:face-detection:16.1.5")

    implementation ("com.google.guava:guava:33.0.0-android")


    // ==========================================================
    // Tambahkan bagian ini untuk TensorFlow Lite dan Java 8 Desugaring
    // ==========================================================

    // TensorFlow Lite (untuk menjalankan model .tflite)
    implementation ("org.tensorflow:tensorflow-lite:2.15.0") // Gunakan versi stabil terbaru
    implementation ("org.tensorflow:tensorflow-lite-gpu:2.15.0") // Opsional: Untuk akselerasi GPU, sangat disarankan!
    implementation ("org.tensorflow:tensorflow-lite-support:0.4.0")
    // Untuk dukungan Java 8 (jika belum ada dan muncul error tentang API desugaring)

    coreLibraryDesugaring ("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(platform("com.google.firebase:firebase-bom:33.0.0")) // Gunakan versi terbaru
    // Tambahkan dependensi untuk Firebase Cloud Firestore
    implementation("com.google.firebase:firebase-firestore-ktx")
}