package com.example.school_restaurant_gestion;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.PlanarYUVLuminanceSource;

import org.json.JSONObject;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_code_scanner);

        previewView = findViewById(R.id.previewView);
        cameraExecutor = Executors.newSingleThreadExecutor();

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
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                cameraProvider.unbindAll();

                Camera camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        imageAnalysis
                );

            } catch (Exception e) {
                Toast.makeText(this, "Erreur lors du démarrage de la caméra.", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(ImageProxy image) {
        try {
            if (image.getFormat() == ImageFormat.YUV_420_888)
            {
                // Conversion de l'image en un tableau de bytes
                byte[] data = image.getPlanes()[0].getBuffer().array();
                int width = image.getWidth();
                int height = image.getHeight();

                // Création de la source d'image pour ZXing
                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                        data, width, height, 0, 0, width, height, false);

                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                MultiFormatReader reader = new MultiFormatReader();

                try {
                    Result result = reader.decode(bitmap);

                    if (result != null) {
                        runOnUiThread(() -> handleQRCodeResult(result.getText()));
                    }
                } catch (Exception e) {
                    // Si l'image ne contient pas de QR code valide, do nothing
                }
            }
        } catch (Exception e) {
            // Gestion des erreurs lors de l'analyse de l'image
        } finally {
            image.close();
        }
    }

    private void handleQRCodeResult(String qrData) {
        try {
            JSONObject json = new JSONObject(qrData);
            String matricule = json.getString("matricule");
            int deductionAmount = json.getInt("deductionAmount");

            callDeductionApi(matricule, deductionAmount);

        } catch (Exception e) {
            Toast.makeText(this, "QR code invalide.", Toast.LENGTH_SHORT).show();
        }
    }

    private void callDeductionApi(String matricule, int deductionAmount) {
        new Thread(() -> {
            try {
                JSONObject requestData = new JSONObject();
                requestData.put("matricule", matricule);
                requestData.put("deductionAmount", deductionAmount);

                SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                String token = sharedPreferences.getString("token", "");

                if (token.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "Token introuvable. Veuillez vous reconnecter.", Toast.LENGTH_SHORT).show());
                    return;
                }

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(requestData.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url("http://10.0.2.2:5000/api/deduct")
                        .post(body)
                        .addHeader("Authorization", "Bearer " + token)
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(this, "Déduction réussie", Toast.LENGTH_SHORT).show());
                } else {
                    String errorMessage = response.body() != null ? response.body().string() : "Erreur inconnue";
                    runOnUiThread(() -> Toast.makeText(this, "Erreur API : " + errorMessage, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Erreur réseau : " + e.getMessage(), Toast.LENGTH_LONG).show());
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
