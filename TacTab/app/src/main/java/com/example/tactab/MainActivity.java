package com.example.tactab;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.tactab.databinding.ActivityMainBinding;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.concurrent.ExecutionException;

//new imports

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import android.graphics.RectF;
import android.speech.tts.TextToSpeech;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    // Camera preview container
    private PreviewView cameraPreview;

    //new
    private ObjectDetector objectDetector;
    private TextToSpeech tts;
    private long lastSpokenTime = 0;
    private static final long SPEECH_INTERVAL_MS = 1000;
    private float focalLengthPx = 800f;

    private Interpreter midasInterpreter;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup view binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup toolbar and navigation drawer
        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(view ->
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null)
                        .setAnchorView(R.id.fab).show()
        );

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        // Initialize camera preview container
        cameraPreview = new PreviewView(this);
        cameraPreview.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));


        // Request camera permission
        requestCameraPermission();

//        tts = new TextToSpeech(this, status -> {
//            if (status == TextToSpeech.SUCCESS) {
//                tts.setLanguage(Locale.US);
//            }
//        });

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported!");
                } else {
                    Log.d("TTS", "TTS initialized successfully");
                }
            } else {
                Log.e("TTS", "Initialization failed");
            }
        });


        loadObjectDetector();
        loadMiDaS();
    }

    private void loadObjectDetector() {
        try {
            ObjectDetector.ObjectDetectorOptions options =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setMaxResults(5)
                            .setScoreThreshold(0.5f)
                            .build();

            objectDetector = ObjectDetector.createFromFileAndOptions(
                    this, "detect.tflite", options);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadMiDaS() {
        try {
            MappedByteBuffer buffer = FileUtil.loadMappedFile(this, "midas_v2.tflite");
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            midasInterpreter = new Interpreter(buffer, options);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private float[][] runMiDaS(Bitmap bitmap) {
        // Resize to model input size (depends on which MiDaS version you use, e.g., 256x256)
        Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true);

        TensorImage inputImage = TensorImage.fromBitmap(inputBitmap);

        float[][][][] output = new float[1][256][256][1]; // adjust if model outputs differently
        midasInterpreter.run(inputImage.getBuffer(), output);

        // Collapse to 2D depth map2
        float[][] depthMap = new float[256][256];
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                depthMap[y][x] = output[0][y][x][0];
            }
        }
        return depthMap;
    }

    private void analyzeImage(ImageProxy imageProxy) {
        if (objectDetector == null || midasInterpreter == null) {
            imageProxy.close();
            return;
        }

        Bitmap bitmap = toBitmap(imageProxy);
        if (bitmap == null) {
            imageProxy.close();
            return;
        }

        TensorImage tensorImage = TensorImage.fromBitmap(bitmap);

        List<Detection> results = objectDetector.detect(tensorImage);

        // Run MiDaS for depth
        float[][] depthMap = runMiDaS(bitmap);

        handleDetections(results, tensorImage.getWidth(), tensorImage.getHeight(), depthMap);

        imageProxy.close();
    }


    private void handleDetections(List<Detection> results, int imageWidth, int imageHeight, float[][] depthMap) {
        long now = System.currentTimeMillis();
        if (now - lastSpokenTime < SPEECH_INTERVAL_MS) return;

        for (Detection detection : results) {
            if (detection.getCategories().isEmpty()) continue;

            String label = detection.getCategories().get(0).getLabel();
            float score = detection.getCategories().get(0).getScore();
            if (score < 0.5f) continue;

            RectF box = detection.getBoundingBox();
            float boxHeightPx = box.height();

            // Direction
            float centerX = (box.left + box.right) / 2f;
            String direction;
            if (centerX < imageWidth / 3f) direction = "left";
            else if (centerX > imageWidth * 2f / 3f) direction = "right";
            else direction = "center";

            // Distance estimation - midas
            float medianDepth = getMedianDepth(depthMap, box, imageWidth, imageHeight);

            Log.d("TTS", "Detected: " + label + " with depth=" + medianDepth);

            String distanceCategory;
            if (medianDepth < 0.8f) distanceCategory = "close";
            else if (medianDepth < 1.5f) distanceCategory = "medium";
            else distanceCategory = "far";

            String message = label + " " + distanceCategory + " " + direction;

            speak(message);
            lastSpokenTime = now;
            break; // only announce one per cycle
        }
    }

    private void speak(String text) {
        if (tts != null) {
            if (tts.isSpeaking()) tts.stop();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "det_id");
        }
    }

    private float getMedianDepth(float[][] depthMap, RectF box, int imageWidth, int imageHeight) {
        int modelSize = 256; // match your MiDaS input size
        int left = (int)(box.left / imageWidth * modelSize);
        int top = (int)(box.top / imageHeight * modelSize);
        int right = (int)(box.right / imageWidth * modelSize);
        int bottom = (int)(box.bottom / imageHeight * modelSize);

        List<Float> values = new ArrayList<>();
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                values.add(depthMap[y][x]);
            }
        }

        if (values.isEmpty()) return 999f;

        Collections.sort(values);
        return values.get(values.size() / 2); // median
    }

    private Bitmap toBitmap(ImageProxy imageProxy) {
        @SuppressWarnings("UnsafeOptInUsageError")
        Image image = imageProxy.getImage();
        if (image == null) return null;

        YuvToRgbConverter converter = new YuvToRgbConverter(this);
        Bitmap bitmap = Bitmap.createBitmap(
                imageProxy.getWidth(),
                imageProxy.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        converter.yuvToRgb(imageProxy, bitmap);
        return bitmap;
    }




    /** Request camera permission at runtime */
    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            startCamera();
        }
    }

    /** Handle permission request result */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Snackbar.make(binding.getRoot(), "Camera permission is required", Snackbar.LENGTH_LONG).show();
            }
        }
    }

    /** Initialize CameraX preview */
    private void startCamera() {
        // Get CameraProvider
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {

                // Retrieve cameraProvider from the future
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Build the camera preview
                Preview preview = new Preview.Builder().build();

                // Set the surface provider to our PreviewView
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                // Select back camera as default
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind any previous use cases
                cameraProvider.unbindAll();

                // Bind preview use case to the lifecycle
                cameraProvider.bindToLifecycle(
                        this,           // LifecycleOwner
                        cameraSelector, // Camera selector
                        preview         // Use case(s)
                );

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setTargetResolution(new Size(640, 480))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        imageProxy -> analyzeImage(imageProxy)
                );

                // Bind both preview + analysis
                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );


                /**
                 * TODO 1: Integrate Object Detection
                 * - Load a TensorFlow Lite object detection model (MobileNet SSD or YOLOv8 Tiny)
                 * - Convert camera frames to Bitmap or TensorImage
                 * - Pass each frame to the model
                 * - Receive list of detected objects (label, bounding box, confidence)
                 */

                /**
                 * TODO 2: Approximate Distance Estimation
                 * - For each detected object:
                 *     - Get bounding box height in pixels
                 *     - Use known object height (e.g., 1.7 m for person)
                 *     - Use formula: distance = known_height * focal_length / bounding_box_height
                 * - Optional: Use depth estimation models (MiDaS) for better accuracy
                 */

                /**
                 * TODO 3: Direction Determination
                 * - For each detected object:
                 *     - Get bounding box center X coordinate
                 *     - Compare to frame width:
                 *         - left third → "left"
                 *         - right third → "right"
                 *         - middle → "center"
                 */

                /**
                 * TODO 4: Audio Feedback
                 * - Create a string: "<object> <distance> meters ahead <direction>"
                 * - Use Android TextToSpeech to speak it
                 * - Throttle updates (1–2 times per second) to avoid overwhelming user
                 * - Optional: Combine nearby objects to avoid repetition
                 */

                /**
                 * TODO 5: Haptic Feedback (Optional)
                 * - Vibrate phone for dangerous objects (e.g., < 2 meters)
                 * - Short pulse for medium distance, longer pulse for very close
                 */

                /**
                 * TODO 6: Navigation Integration (Optional)
                 * - Combine object detection with GPS/Map directions
                 * - Guide user safely along route while avoiding obstacles
                 */

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
