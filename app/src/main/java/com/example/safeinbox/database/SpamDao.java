package com.example.safeinbox.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.provider.BlockedNumberContract;

import com.example.safeinbox.models.SmsModel;
import com.example.safeinbox.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class SpamDao {

    private final DBHelper dbHelper;
    private final Context context;

    public SpamDao(Context context) {
        this.context = context;
        this.dbHelper = new DBHelper(context);
    }

    // ==================== Messages Table Operations ====================

    public long insertMessage(SmsModel message) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_SENDER, message.getSender());
        values.put(Constants.COL_BODY, message.getBody());
        values.put(Constants.COL_DATE, message.getDate());
        values.put(Constants.COL_IS_SPAM, message.isSpam() ? 1 : 0);
        long id = db.insert(Constants.TABLE_MESSAGES, null, values);
        db.close();
        return id;
    }

    public List<SmsModel> getMessages(boolean spamOnly) {
        List<SmsModel> messages = new ArrayList<>();
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
                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_BODY));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_DATE));
                    boolean isSpam = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_IS_SPAM)) == 1;
                    messages.add(new SmsModel(id, sender, body, date, isSpam));
                }
            } finally {
                cursor.close();
            }
        }

        db.close();
        return messages;
    }

    public SmsModel getMessageById(long messageId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                Constants.TABLE_MESSAGES,
                null,
                Constants.COL_ID + " = ?",
                new String[]{String.valueOf(messageId)},
                null, null, null
        );

        SmsModel message = null;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_ID));
                    String sender = cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_SENDER));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_BODY));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_DATE));
                    boolean isSpam = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_IS_SPAM)) == 1;
                    message = new SmsModel(id, sender, body, date, isSpam);
                }
            } finally {
                cursor.close();
            }
        }

        db.close();
        return message;
    }

    public void updateSpamStatus(long messageId, boolean isSpam) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_IS_SPAM, isSpam ? 1 : 0);
        db.update(Constants.TABLE_MESSAGES, values,
                Constants.COL_ID + " = ?",
                new String[]{String.valueOf(messageId)});
        db.close();
    }

    public void clearMessages() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(Constants.TABLE_MESSAGES, null, null);
        db.close();
    }

    // ==================== Spam Numbers Table Operations ====================

    public void addSpamNumber(String number) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Constants.COL_NUMBER, number);
        db.insertWithOnConflict(Constants.TABLE_SPAM_NUMBERS, null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
        db.close();
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

        db.close();
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
        db.close();

        // Also update the is_spam flag in the messages table
        boolean isSpam = Constants.LABEL_SPAM.equals(label);
        updateSpamStatus(messageId, isSpam);
    }
}
