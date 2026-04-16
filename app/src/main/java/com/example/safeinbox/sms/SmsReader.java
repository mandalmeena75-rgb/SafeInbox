package com.example.safeinbox.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import androidx.core.content.ContextCompat;

import com.example.safeinbox.models.SmsModel;

import java.util.ArrayList;
import java.util.List;

public class SmsReader {

    private final Context context;

    public SmsReader(Context context) {
        this.context = context;
    }

    public boolean hasReadSmsPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public List<SmsModel> readInboxMessages() {
        List<SmsModel> messages = new ArrayList<>();

        if (!hasReadSmsPermission()) {
            return messages;
        }

        Uri uri = Uri.parse("content://sms/inbox");
        String[] projection = {"address", "body", "date"};
        String sortOrder = "date DESC";

        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, sortOrder);

        if (cursor != null) {
            try {
                int addressIndex = cursor.getColumnIndexOrThrow("address");
                int bodyIndex = cursor.getColumnIndexOrThrow("body");
                int dateIndex = cursor.getColumnIndexOrThrow("date");

                while (cursor.moveToNext()) {
                    String sender = cursor.getString(addressIndex);
                    String body = cursor.getString(bodyIndex);
                    long date = cursor.getLong(dateIndex);

                    SmsModel sms = new SmsModel(
                            sender != null ? sender : "Unknown",
                            body != null ? body : "",
                            date
                    );
                    messages.add(sms);
                }
            } finally {
                cursor.close();
            }
        }

        return messages;
    }
}
