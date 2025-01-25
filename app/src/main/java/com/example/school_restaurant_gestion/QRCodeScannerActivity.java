package com.example.school_restaurant_gestion;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class QRCodeScannerActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_code_scanner);

        previewView = findViewById(R.id.previewView);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Vérification des permissions pour la caméra
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Sélection de la caméra arrière
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Configuration du flux
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Configuration de l’analyse d’image
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // Assignation de l’analyseur d’images
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                // Liaison des use cases à CameraX
                Camera camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );

            } catch (Exception e) {
                Toast.makeText(this, "Erreur lors de l'initialisation de la caméra : " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(ImageProxy image) {
        if (image.getFormat() != ImageFormat.YUV_420_888 || isProcessing) {
            image.close();
            return;
        }

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        byte[] yBytes = new byte[yBuffer.remaining()];
        yBuffer.get(yBytes);

        int width = image.getWidth();
        int height = image.getHeight();

        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                yBytes, width, height, 0, 0, width, height, false);

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        MultiFormatReader reader = new MultiFormatReader();

        try {
            Result result = reader.decode(bitmap);
            if (result != null) {
                String qrContent = result.getText();
                runOnUiThread(() -> {
                    Toast.makeText(this, "QR code détecté : " + qrContent, Toast.LENGTH_SHORT).show();
                    handleQRCodeResult(qrContent);
                });
            }
        } catch (Exception e) {
        } finally {
            image.close();
        }
    }

    private void handleQRCodeResult(String qrData) {
        try {
            JSONObject json = new JSONObject(qrData);
            String matricule = json.getString("matricule");

            // Marquer le traitement en cours - bagn multiple api call
            isProcessing = true;
            callDeductionApi(matricule);

        } catch (Exception e) {
            Toast.makeText(this, "QR code invalide.", Toast.LENGTH_SHORT).show();
            isProcessing = false;
        }
    }

    private void callDeductionApi(String matricule) {
        new Thread(() -> {
            try {
                JSONObject requestData = new JSONObject();
                requestData.put("matricule", matricule);

                SharedPreferences sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE);
                String token = sharedPreferences.getString("token", "");

                if (token.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "Token introuvable. Veuillez vous reconnecter.", Toast.LENGTH_SHORT).show());
                    isProcessing = false;
                    return;
                }

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(requestData.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url("http://192.168.1.7:5000/api/deduct")
                        .post(body)
                        .addHeader("Authorization", "Bearer " + token)
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Déduction réussie", Toast.LENGTH_SHORT).show();

                        // Délai avant de quitter l'écran de scan
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            isProcessing = false;

                            startActivity(new Intent(QRCodeScannerActivity.this, MainActivity.class));
                            finish();
                        }, 1000);
                    });
                } else {
                    String errorMessage = response.body() != null ? response.body().string() : "Erreur inconnue";
                    runOnUiThread(() -> Toast.makeText(this, "Erreur API : " + errorMessage, Toast.LENGTH_LONG).show());
                    isProcessing = false;
                }

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Erreur réseau : " + e.getMessage(), Toast.LENGTH_LONG).show());
                isProcessing = false;
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Permission caméra refusée.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}