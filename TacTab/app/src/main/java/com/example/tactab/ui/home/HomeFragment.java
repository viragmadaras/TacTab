package com.example.tactab.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.tactab.databinding.FragmentHomeBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private PreviewView previewView;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize PreviewView
        previewView = binding.previewView;

        // Request camera permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }

        return root;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            }
        }
    }

    /** Initialize CameraX preview */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);

                /**
                 * TODO 1: Integrate Object Detection
                 * - Load a TensorFlow Lite model (MobileNet SSD / YOLOv8 Tiny) #Documentatia de la Yolo/ Youtube Tutorial
                 * - Convert camera frames to Bitmap or TensorImage ## Check StackOverflow/ YT / Chatgpt
                 * - Pass frames to the model
                 * - Receive detected objects (label, bounding box, confidence)
                 *
                 *
                 *
                 *
                 *
                 *
                 *
                 *
                 * TODO 2: Approximate Distance Estimation
                 * - For each object:
                 *     distance = known_object_height * focal_length / bounding_box_height
                 *
                 * TODO 3: Direction Determination
                 * - Use bounding box center X coordinate
                 *     left third → "left"
                 *     right third → "right"
                 *     middle → "center"
                 *
                 * TODO 4: Audio Feedback
                 * - Create string: "<object> <distance> meters ahead <direction>"
                 * - Use TextToSpeech to speak it
                 * - Update 1–2 times/sec
                 *
                 * TODO 5: Haptic Feedback
                 * - Vibrate phone for objects closer than 2 meters
                 *
                 * TODO 6: Navigation Integration
                 * - Combine object detection with GPS/Map directions
                 */

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
