package com.example.safeinbox.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import androidx.core.content.ContextCompat;

import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.utils.ContactUtils;

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

    public List<SmsMessage> readInboxMessages() {
        List<SmsMessage> messages = new ArrayList<>();
        messages.addAll(readMessagesSince(0));
        messages.addAll(readMmsMessagesSince(0));
        return messages;
    }

    public List<SmsMessage> readMessagesSince(long timestamp) {
        List<SmsMessage> messages = new ArrayList<>();

        if (!hasReadSmsPermission()) {
            return messages;
        }

        Uri uri = Uri.parse("content://sms/inbox");
        String[] projection = {"address", "body", "date"};
        String selection = "date > ?";
        String[] selectionArgs = {String.valueOf(timestamp)};
        String sortOrder = "date DESC";

        Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);

        if (cursor != null) {
            try {
                int addressIndex = cursor.getColumnIndexOrThrow("address");
                int bodyIndex = cursor.getColumnIndexOrThrow("body");
                int dateIndex = cursor.getColumnIndexOrThrow("date");

                while (cursor.moveToNext()) {
                    String sender = cursor.getString(addressIndex);
                    String body = cursor.getString(bodyIndex);
                    long date = cursor.getLong(dateIndex);

                    String contactName = ContactUtils.getContactName(context, sender);

                    SmsMessage sms = new SmsMessage(
                            sender != null ? sender : "Unknown",
                            body != null ? body : "",
                            date
                    );
                    sms.setSenderName(contactName);
                    messages.add(sms);
                }
            } finally {
                cursor.close();
            }
        }

        return messages;
    }

    public List<SmsMessage> readMmsMessagesSince(long timestamp) {
        List<SmsMessage> messages = new ArrayList<>();
        if (!hasReadSmsPermission()) return messages;

        Uri uri = Uri.parse("content://mms/inbox");
        String selection = "date > ?";
        String[] selectionArgs = {String.valueOf(timestamp / 1000)}; // MMS uses seconds
        
        Cursor cursor = context.getContentResolver().query(uri, null, selection, selectionArgs, "date DESC");

        if (cursor != null) {
            try {
                int idIndex = cursor.getColumnIndexOrThrow("_id");
                int dateIndex = cursor.getColumnIndexOrThrow("date");

                while (cursor.moveToNext()) {
                    long mmsId = cursor.getLong(idIndex);
                    long date = cursor.getLong(dateIndex) * 1000; // Convert to ms

                    String body = getMmsText(mmsId);
                    String sender = getMmsAddress(mmsId);

                    if (body != null && !body.isEmpty()) {
                        SmsMessage mms = new SmsMessage(
                                sender != null ? sender : "Unknown",
                                body,
                                date
                        );
                        mms.setSenderName(ContactUtils.getContactName(context, sender));
                        messages.add(mms);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return messages;
    }

    private String getMmsText(long mmsId) {
        String selectionPart = "mid=" + mmsId;
        Uri uri = Uri.parse("content://mms/part");
        Cursor cursor = context.getContentResolver().query(uri, null, selectionPart, null, null);
        StringBuilder sb = new StringBuilder();

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String ct = cursor.getString(cursor.getColumnIndexOrThrow("ct"));
                    if ("text/plain".equals(ct)) {
                        String data = cursor.getString(cursor.getColumnIndexOrThrow("_data"));
                        if (data != null) {
                            // Text is stored in a file or in the 'text' column depending on platform
                            sb.append(cursor.getString(cursor.getColumnIndexOrThrow("text")));
                        } else {
                            sb.append(cursor.getString(cursor.getColumnIndexOrThrow("text")));
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return sb.toString();
    }

    private String getMmsAddress(long mmsId) {
        Uri uri = Uri.parse("content://mms/" + mmsId + "/addr");
        // type 137 is 'FROM'
        Cursor cursor = context.getContentResolver().query(uri, null, "type=137", null, null);
        String address = null;

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                }
            } finally {
                cursor.close();
            }
        }
        return address;
    }
}
