package com.example.safeinbox.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.provider.BlockedNumberContract;

import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class SpamDao {

    private final DBHelper dbHelper;
    private final Context context;

    public SpamDao(Context context) {
        this.context = context;
        this.dbHelper = DBHelper.getInstance(context);
    }

    // ==================== Messages Table Operations ====================

    public long insertMessage(SmsMessage message) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_SENDER, message.getSender());
        values.put(Constants.COL_SENDER_NAME, message.getSenderName());
        values.put(Constants.COL_BODY, message.getBody());
        values.put(Constants.COL_DATE, message.getDate());
        values.put(Constants.COL_IS_SPAM, message.isSpam() ? 1 : 0);
        return db.insertWithOnConflict(Constants.TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void insertMessagesBatch(List<SmsMessage> messages) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (SmsMessage message : messages) {
                ContentValues values = new ContentValues();
                values.put(Constants.COL_SENDER, message.getSender());
                values.put(Constants.COL_SENDER_NAME, message.getSenderName());
                values.put(Constants.COL_BODY, message.getBody());
                values.put(Constants.COL_DATE, message.getDate());
                values.put(Constants.COL_IS_SPAM, message.isSpam() ? 1 : 0);
                db.insertWithOnConflict(Constants.TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<SmsMessage> getMessages(boolean spamOnly) {
        List<SmsMessage> messages = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String selection = Constants.COL_IS_SPAM + " = ?";
        String[] selectionArgs = {spamOnly ? "1" : "0"};

        Cursor cursor = db.query(
                Constants.TABLE_MESSAGES,
                null,
                selection,
                selectionArgs,
                null,
                null,
                Constants.COL_DATE + " DESC"
        );

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_ID));
                    String sender = cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_SENDER));
                    String senderName = cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_SENDER_NAME));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_BODY));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_DATE));
                    boolean isSpam = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_IS_SPAM)) == 1;
                    messages.add(new SmsMessage(id, sender, senderName, body, date, isSpam));
                }
            } finally {
                cursor.close();
            }
        }

        return messages;
    }

    public SmsMessage getMessageById(long messageId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                Constants.TABLE_MESSAGES,
                null,
                Constants.COL_ID + " = ?",
                new String[]{String.valueOf(messageId)},
                null, null, null
        );

        SmsMessage message = null;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_ID));
                    String sender = cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_SENDER));
                    String senderName = cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_SENDER_NAME));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_BODY));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_DATE));
                    boolean isSpam = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_IS_SPAM)) == 1;
                    message = new SmsMessage(id, sender, senderName, body, date, isSpam);
                }
            } finally {
                cursor.close();
            }
        }

        return message;
    }

    public void updateSpamStatus(long messageId, boolean isSpam) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_IS_SPAM, isSpam ? 1 : 0);
        db.update(Constants.TABLE_MESSAGES, values,
                Constants.COL_ID + " = ?",
                new String[]{String.valueOf(messageId)});
    }

    public void clearMessages() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(Constants.TABLE_MESSAGES, null, null);
    }

    public int getMessageCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + Constants.TABLE_MESSAGES, null);
        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }

    public long getLatestMessageTimestamp() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT MAX(" + Constants.COL_DATE + ") FROM " + Constants.TABLE_MESSAGES, null);
        long timestamp = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                timestamp = cursor.getLong(0);
            }
            cursor.close();
        }
        return timestamp;
    }

    // ==================== Spam Numbers Table Operations ====================

    public void addSpamNumber(String number) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_NUMBER, number);
        db.insertWithOnConflict(Constants.TABLE_SPAM_NUMBERS, null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    public boolean isNumberInSpamTable(String number) {
        if (number == null) {
            return false;
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                Constants.TABLE_SPAM_NUMBERS,
                null,
                Constants.COL_NUMBER + " = ?",
                new String[]{number},
                null, null, null
        );

        boolean found = false;
        if (cursor != null) {
            found = cursor.getCount() > 0;
            cursor.close();
        }

        return found;
    }

    // ==================== Module 3: Number Blocking Check ====================

    public boolean isBlockedNumber(String number) {
        if (number == null) {
            return false;
        }

        // Check 1: Local SQLite spam_numbers table
        if (isNumberInSpamTable(number)) {
            return true;
        }

        // Check 2: Android's BlockedNumberContract (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                return BlockedNumberContract.isBlocked(context, number);
            } catch (SecurityException e) {
                // App may not have the required privileges
                return false;
            }
        }

        return false;
    }

    // ==================== Module 7: Feedback Table Operations ====================

    public void insertFeedback(long messageId, String label) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_MESSAGE_ID, messageId);
        values.put(Constants.COL_LABEL, label);
        db.insert(Constants.TABLE_FEEDBACK, null, values);

        // Also update the is_spam flag in the messages table
        boolean isSpam = Constants.LABEL_SPAM.equals(label);
        updateSpamStatus(messageId, isSpam);
    }
}
