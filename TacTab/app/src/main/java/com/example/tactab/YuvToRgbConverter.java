package com.example.tactab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class YuvToRgbConverter {

    private final Context context;

    public YuvToRgbConverter(Context context) {
        this.context = context;
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public void yuvToRgb(@NonNull ImageProxy imageProxy, @NonNull Bitmap outputBitmap) {
        Image image = imageProxy.getImage();
        if (image == null) return;

        // Get YUV data from ImageProxy
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.getWidth(),
                imageProxy.getHeight(),
                null
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
                new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()),
                100,
                out
        );
        byte[] jpegBytes = out.toByteArray();

        Bitmap temp = android.graphics.BitmapFactory.decodeByteArray(
                jpegBytes, 0, jpegBytes.length
        );

        if (temp != null) {
            // Copy into the output bitmap
            new android.graphics.Canvas(outputBitmap).drawBitmap(temp, 0f, 0f, null);
        }
    }
}
