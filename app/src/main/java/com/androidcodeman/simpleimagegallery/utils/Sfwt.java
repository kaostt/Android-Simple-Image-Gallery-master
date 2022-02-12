package com.androidcodeman.simpleimagegallery.utils;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;

import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;

public class Sfwt {


    Interpreter interpreter;


    public boolean checkSfwt(String datapath, AssetManager asset) throws IOException {
        //Se lee
        BitmapFactory.Options options = new BitmapFactory.Options();
        final Bitmap bitmapI = BitmapFactory.decodeFile(datapath, options);

        CustomModelDownloadConditions conditions = new CustomModelDownloadConditions.Builder()
                .requireWifi()  // Also possible: .requireCharging() and .requireDeviceIdle()
                .build();


        AssetFileDescriptor fileDescriptor = asset.openFd("saved_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        interpreter = new Interpreter(fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength));


        Bitmap bitmap = Bitmap.createScaledBitmap(bitmapI, 224, 224, true);
        ByteBuffer input = ByteBuffer.allocateDirect(224 * 224 * 3 * 4).order(ByteOrder.nativeOrder());
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 224; x++) {
                int px = bitmap.getPixel(x, y);

                // Get channel values from the pixel value.
                int r = Color.red(px);
                int g = Color.green(px);
                int b = Color.blue(px);

                // Normalize channel values to [-1.0, 1.0]. This requirement depends
                // on the model. For example, some models might require values to be
                // normalized to the range [0.0, 1.0] instead.
                float rf = (r - 127) / 255.0f;
                float gf = (g - 127) / 255.0f;
                float bf = (b - 127) / 255.0f;

                input.putFloat(rf);
                input.putFloat(gf);
                input.putFloat(bf);
            }
        }

        int bufferSize = 1000 * java.lang.Float.SIZE / java.lang.Byte.SIZE;
        ByteBuffer modelOutput = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
        interpreter.run(input, modelOutput);

        modelOutput.rewind();
        FloatBuffer probabilities = modelOutput.asFloatBuffer();
        String type = null;
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(asset.open("class_labels.txt")));

            float maxProbabilities = 0;
            for (int i = 0; i < 5; i++) {
                String label = reader.readLine();
                float probability = probabilities.get(i);
                if (probability > maxProbabilities) {
                    maxProbabilities = probability;
                    type = label;
                }

            }

            Log.i("TAG", String.format("%s", type));
        } catch (IOException e) {
            // File not found?
        }

        if (type.equals("porn") || type.equals("sexy")) {
            return true;
        } else {
            return false;
        }
    }
}
