package com.inout.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.inout.app.databinding.ActivityEmployeeQrScanBinding;
import com.inout.app.utils.EncryptionHelper;
import com.inout.app.utils.FirebaseManager;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.camera.core.ExperimentalGetImage
public class EmployeeQrScanActivity extends AppCompatActivity {

    private static final String TAG = "EmployeeQrScanActivity";
    private static final int PERMISSION_REQUEST_CAMERA = 1001;

    private ActivityEmployeeQrScanBinding binding;
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private boolean isProcessing = false;

    // NEW: Launcher for picking an image from Gallery
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        processGalleryImage(imageUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEmployeeQrScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Initialize Barcode Scanner
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        scanner = BarcodeScanning.getClient(options);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
        }

        // NEW: Listener for Gallery Upload Button
        binding.btnUploadQr.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> processImageProxy(imageProxy));

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void processImageProxy(ImageProxy imageProxy) {
        if (isProcessing || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty()) {
                        String rawValue = barcodes.get(0).getRawValue();
                        if (rawValue != null) {
                            handleScannedQr(rawValue);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Camera QR analysis failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    /**
     * NEW: Processes a QR code from a static image selected in the gallery.
     */
    private void processGalleryImage(Uri uri) {
        if (isProcessing) return;
        
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            binding.progressBar.setVisibility(View.VISIBLE);
            
            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty()) {
                            String rawValue = barcodes.get(0).getRawValue();
                            if (rawValue != null) {
                                handleScannedQr(rawValue);
                            }
                        } else {
                            binding.progressBar.setVisibility(View.GONE);
                            Toast.makeText(this, "No QR code found in this image.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Failed to read image.", Toast.LENGTH_SHORT).show();
                    });
        } catch (IOException e) {
            Log.e(TAG, "Gallery image load failed", e);
        }
    }

    private void handleScannedQr(String encryptedPayload) {
        if (isProcessing) return;
        isProcessing = true;

        runOnUiThread(() -> {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.tvStatus.setText("Processing registration...");
        });

        // 1. Decrypt the payload
        String decryptedJson = EncryptionHelper.getInstance(this).decryptQrPayload(encryptedPayload);

        if (decryptedJson == null) {
            resetScan("Invalid QR Code. Decryption failed.");
            return;
        }

        try {
            // 2. Parse the payload Wrapper
            JSONObject wrapper = new JSONObject(decryptedJson);
            
            String firebaseConfigStr = wrapper.getString("firebaseConfig");
            String companyName = wrapper.getString("companyName");
            String projectId = wrapper.getString("projectId");

            // 3. Save Configuration locally
            boolean success = FirebaseManager.setConfiguration(this, firebaseConfigStr, companyName, projectId);

            if (success) {
                // 4. Initialize Firebase
                FirebaseManager.initialize(this);
                
                runOnUiThread(() -> {
                    Toast.makeText(EmployeeQrScanActivity.this, "Successfully connected to " + companyName, Toast.LENGTH_LONG).show();
                    startActivity(new Intent(EmployeeQrScanActivity.this, LoginActivity.class));
                    finish();
                });
            } else {
                resetScan("Configuration error. Data might be corrupt.");
            }

        } catch (Exception e) {
            Log.e(TAG, "JSON Parsing error", e);
            resetScan("Unsupported QR format.");
        }
    }

    private void resetScan(String errorMsg) {
        runOnUiThread(() -> {
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            binding.progressBar.setVisibility(View.GONE);
            binding.tvStatus.setText(R.string.scan_qr_title);
            isProcessing = false; 
        });
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera access is needed for live scanning.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}