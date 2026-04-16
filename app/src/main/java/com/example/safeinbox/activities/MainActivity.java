package com.example.safeinbox.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.safeinbox.R;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.detection.SpamDetector;
import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.sms.SmsReader;
import com.example.safeinbox.utils.Constants;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView statusText;
    private CardView rcsCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.text_status);
        rcsCard = findViewById(R.id.card_rcs);

        if (hasRequiredPermissions()) {
            checkAndProceed();
        } else {
            requestRequiredPermissions();
        }
    }

    private void checkAndProceed() {
        new Thread(() -> {
            SpamDao dao = new SpamDao(this);
            int count = dao.getMessageCount();
            
            runOnUiThread(() -> {
                if (count > 0) {
                    // Already have messages, show RCS setup if needed or go to Inbox
                    if (!isNotificationServiceEnabled()) {
                        showRcsSetup();
                    } else {
                        navigateToInbox();
                    }
                } else {
                    // First time, scan all
                    processMessages();
                }
            });
        }).start();
    }

    private boolean hasRequiredPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_MMS)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRequiredPermissions() {
        statusText.setText("Requesting permissions...");
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.RECEIVE_MMS,
                        Manifest.permission.READ_CONTACTS
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
                checkAndProceed();
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
                List<SmsMessage> messages = reader.readInboxMessages();

                // Step 2: Classify each message using the SpamDetector
                SpamDetector detector = new SpamDetector(MainActivity.this);
                SpamDao dao = new SpamDao(MainActivity.this);

                // Clear old data and re-scan
                dao.clearMessages();

                for (SmsMessage message : messages) {
                    boolean isSpam = detector.isSpam(message.getSender(), message.getBody());
                    message.setSpam(isSpam);
                    // Name lookup is already handled inside reader.readInboxMessages()
                }

                // Step 2: Batch insert all classified messages (Superfast)
                dao.insertMessagesBatch(messages);

                // Step 3: Navigate to Inbox on the UI thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        navigateToInbox();
                    }
                });
            }
        }).start();
    }

    private void navigateToInbox() {
        Intent intent = new Intent(MainActivity.this, InboxActivity.class);
        startActivity(intent);
        finish();
    }

    private void showRcsSetup() {
        progressBar.setVisibility(View.GONE);
        statusText.setText("Scan completed.");
        rcsCard.setVisibility(View.VISIBLE);

        findViewById(R.id.btn_enable_rcs).setOnClickListener(v -> {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivity(intent);
            
            // Allow user to continue to inbox after clicking
            Button enableBtn = (Button) findViewById(R.id.btn_enable_rcs);
            enableBtn.setText("Continue to Inbox");
            enableBtn.setOnClickListener(v2 -> navigateToInbox());
        });
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = android.provider.Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(name);
                if (cn != null && pkgName.equals(cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
