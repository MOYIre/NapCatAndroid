package com.napcat.android;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "NapCatAndroid";
    private TextView statusText;
    private Button startServiceButton;
    
    // We'll control the service instead of managing NodeRunner directly in the activity
    private boolean serviceBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.d(TAG, "NapCatAndroid started");
        
        // Initialize UI components
        statusText = findViewById(R.id.status);
        startServiceButton = findViewById(R.id.start_service);
        
        // Set up button click listener
        startServiceButton.setOnClickListener(v -> {
            if (!serviceBound) {
                startNapCatService();
            } else {
                stopNapCatService();
            }
        });
        
        // Initialize the interface
        updateServiceStatus();
    }

    private void startNapCatService() {
        Log.d(TAG, "Starting NapCat service...");
        
        // Start the service using intent
        Intent serviceIntent = new Intent(this, NapCatService.class);
        startService(serviceIntent);
        
        serviceBound = true;
        updateServiceStatus();
        Toast.makeText(this, "Starting NapCat service...", Toast.LENGTH_SHORT).show();
        
        // Update status after a short delay to allow service to start
        new android.os.Handler().postDelayed(this::updateServiceStatus, 1000);
    }

    private void stopNapCatService() {
        Log.d(TAG, "Stopping NapCat service...");
        
        // Stop the service using intent
        Intent serviceIntent = new Intent(this, NapCatService.class);
        stopService(serviceIntent);
        
        serviceBound = false;
        updateServiceStatus();
        Toast.makeText(this, "NapCat service stopped", Toast.LENGTH_SHORT).show();
    }

    private void updateServiceStatus() {
        // In a real implementation, we would check the actual service status
        // For now, we'll just update the UI based on our local state
        if (serviceBound) {
            statusText.setText("Service is running (check notification)");
            startServiceButton.setText("Stop Service");
        } else {
            statusText.setText("Service not running");
            startServiceButton.setText("Start Service");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't stop the service when activity is destroyed - let it run in background
        // The service will continue running to maintain the QQ connection
    }
}