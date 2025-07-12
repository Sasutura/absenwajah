package com.sasutura.absenwajah;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FaceEmbedder {

    private Interpreter interpreter;
    private static final int IMAGE_SIZE = 112;
    private static final int NUM_EMBEDDINGS = 192;
    private static final float MEAN = 127.5f;
    private static final float STD = 128.0f;

    public FaceEmbedder(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(FileUtil.loadMappedFile(context, "mobile_face_net.tflite"), options);
        Log.d("FaceEmbedder", "Model berhasil dimuat.");
    }

    public float[] getFaceEmbedding(Bitmap faceBitmap) {
        if (faceBitmap == null) return null;

        Bitmap resized = Bitmap.createScaledBitmap(faceBitmap, IMAGE_SIZE, IMAGE_SIZE, true);
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(IMAGE_SIZE * IMAGE_SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[IMAGE_SIZE * IMAGE_SIZE];
        resized.getPixels(intValues, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);

        for (int i = 0; i < intValues.length; i++) {
            int val = intValues[i];
            inputBuffer.putFloat(((val >> 16 & 0xFF) - MEAN) / STD);
            inputBuffer.putFloat(((val >> 8 & 0xFF) - MEAN) / STD);
            inputBuffer.putFloat(((val & 0xFF) - MEAN) / STD);
        }

        float[][] output = new float[1][NUM_EMBEDDINGS];
        interpreter.run(inputBuffer, output);

        // Normalisasi L2
        float[] embedding = output[0];
        float sum = 0f;
        for (float v : embedding) sum += v * v;
        float norm = (float) Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= norm;
            }
        }
        return embedding;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}
