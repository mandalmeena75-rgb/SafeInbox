package com.example.safeinbox.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.utils.ContactUtils;

import java.util.ArrayList;
import java.util.List;

public class SmsReader {

    private static final String TAG = "SmsReader";
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
        if (!hasReadSmsPermission()) return messages;

        Uri uri = Uri.parse("content://sms/inbox");
        String[] projection = {"address", "body", "date"};
        String selection = "date > ?";
        String[] selectionArgs = {String.valueOf(timestamp)};
        String sortOrder = "date DESC";

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);

            if (cursor != null) {
                int addressIndex = cursor.getColumnIndexOrThrow("address");
                int bodyIndex    = cursor.getColumnIndexOrThrow("body");
                int dateIndex    = cursor.getColumnIndexOrThrow("date");

                while (cursor.moveToNext()) {
                    try {
                        String senderRaw = cursor.getString(addressIndex);
                        String sender = ContactUtils.normalizeSender(senderRaw);
                        String body   = cursor.getString(bodyIndex);
                        long date     = cursor.getLong(dateIndex);

                        if (body == null || body.trim().isEmpty()) continue;

                        String contactName = ContactUtils.getContactName(context, senderRaw);

                        SmsMessage sms = new SmsMessage(
                                sender,
                                body.trim(),
                                date
                        );
                        sms.setSenderName(contactName);
                        messages.add(sms);
                    } catch (Exception e) {
                        Log.w(TAG, "Error reading SMS row", e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "readMessagesSince error", e);
        } finally {
            if (cursor != null) cursor.close();
        }

        return messages;
    }

    public List<SmsMessage> readMmsMessagesSince(long timestamp) {
        List<SmsMessage> messages = new ArrayList<>();
        if (!hasReadSmsPermission()) return messages;

        Uri uri = Uri.parse("content://mms/inbox");
        String selection = "date > ?";
        String[] selectionArgs = {String.valueOf(timestamp / 1000)}; // MMS uses seconds

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, selection, selectionArgs, "date DESC");

            if (cursor != null) {
                int idIndex   = cursor.getColumnIndex("_id");
                int dateIndex = cursor.getColumnIndex("date");

                // If columns don't exist, skip MMS entirely
                if (idIndex < 0 || dateIndex < 0) {
                    Log.w(TAG, "MMS columns not found, skipping");
                    return messages;
                }

                while (cursor.moveToNext()) {
                    try {
                        long mmsId = cursor.getLong(idIndex);
                        long date  = cursor.getLong(dateIndex) * 1000; // Convert to ms

                        String body      = getMmsText(mmsId);
                        String senderRaw = getMmsAddress(mmsId);
                        String sender    = ContactUtils.normalizeSender(senderRaw);

                        if (body != null && !body.trim().isEmpty()) {
                            SmsMessage mms = new SmsMessage(
                                    sender,
                                    body.trim(),
                                    date
                            );
                            mms.setSenderName(ContactUtils.getContactName(context, senderRaw));
                            messages.add(mms);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error reading MMS row", e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "readMmsMessagesSince error", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return messages;
    }

    private String getMmsText(long mmsId) {
        StringBuilder sb = new StringBuilder();
        Cursor cursor = null;
        try {
            String selectionPart = "mid=" + mmsId;
            Uri uri = Uri.parse("content://mms/part");
            cursor = context.getContentResolver().query(uri, null, selectionPart, null, null);

            if (cursor != null) {
                int ctIndex   = cursor.getColumnIndex("ct");
                int textIndex = cursor.getColumnIndex("text");
                int idIndex   = cursor.getColumnIndex("_id");
                int dataIndex = cursor.getColumnIndex("_data");

                if (ctIndex < 0 || textIndex < 0) return "";

                while (cursor.moveToNext()) {
                    try {
                        String ct = cursor.getString(ctIndex);
                        if ("text/plain".equals(ct)) {
                            String text = cursor.getString(textIndex);
                            if (text != null) {
                                sb.append(text);
                            } else {
                                // Fallback: try reading from stream
                                String partId = cursor.getString(idIndex);
                                if (partId != null) {
                                    sb.append(readMmsPartStream(partId));
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error reading MMS part", e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getMmsText error", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return sb.toString();
    }

    private String readMmsPartStream(String partId) {
        StringBuilder sb = new StringBuilder();
        Uri partUri = Uri.parse("content://mms/part/" + partId);
        try (java.io.InputStream is = context.getContentResolver().openInputStream(partUri);
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading MMS stream", e);
        }
        return sb.toString();
    }

    private String getMmsAddress(long mmsId) {
        Cursor cursor = null;
        try {
            Uri uri = Uri.parse("content://mms/" + mmsId + "/addr");
            cursor = context.getContentResolver().query(uri, null, "type=137", null, null);

            if (cursor != null) {
                int addrIndex = cursor.getColumnIndex("address");
                if (addrIndex >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(addrIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getMmsAddress error", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }
}
