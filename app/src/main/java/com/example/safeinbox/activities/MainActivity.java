package com.example.safeinbox.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.safeinbox.R;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.detection.SpamDetector;
import com.example.safeinbox.models.SmsModel;
import com.example.safeinbox.sms.SmsReader;
import com.example.safeinbox.utils.Constants;

import java.util.List;

public class MainActivity extends Activity {

    private ProgressBar progressBar;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.text_status);

        if (hasRequiredPermissions()) {
            processMessages();
        } else {
            requestRequiredPermissions();
        }
    }

    private boolean hasRequiredPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRequiredPermissions() {
        statusText.setText("Requesting permissions...");
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
                },
                Constants.PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                processMessages();
            } else {
                progressBar.setVisibility(View.GONE);
                statusText.setText("SMS permissions are required. Please grant them in Settings.");
                Toast.makeText(this, "SMS permission is required to use this app",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void processMessages() {
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Scanning and classifying messages...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Step 1: Read all SMS from device inbox
                SmsReader reader = new SmsReader(MainActivity.this);
                List<SmsModel> messages = reader.readInboxMessages();

                // Step 2: Classify each message using the SpamDetector
                SpamDetector detector = new SpamDetector(MainActivity.this);
                SpamDao dao = new SpamDao(MainActivity.this);

                // Clear old data and re-scan
                dao.clearMessages();

                for (SmsModel message : messages) {
                    boolean isSpam = detector.isSpam(message.getSender(), message.getBody());
                    message.setSpam(isSpam);
                    long id = dao.insertMessage(message);
                    message.setId(id);
                }

                // Step 3: Navigate to Inbox on the UI thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(MainActivity.this, InboxActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });
            }
        }).start();
    }
}
