package com.example.safeinbox.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.safeinbox.utils.Constants;

import java.util.HashMap;
import java.util.Map;

public class MLDao {

    private final DBHelper dbHelper;

    public MLDao(Context context) {
        this.dbHelper = DBHelper.getInstance(context);
    }

    public void saveWordCounts(Map<String, Integer> spamCounts, Map<String, Integer> hamCounts) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // For simplicity, we just clear and re-insert, or upsert.
            // On a large vocab, upsert is better.
            for (String word : spamCounts.keySet()) {
                ContentValues values = new ContentValues();
                values.put(Constants.COL_WORD, word);
                values.put(Constants.COL_SPAM_COUNT, spamCounts.get(word));
                values.put(Constants.COL_HAM_COUNT, hamCounts.getOrDefault(word, 0));
                db.insertWithOnConflict(Constants.TABLE_WORD_COUNTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            // Ensure words only in hamCounts are also saved
            for (String word : hamCounts.keySet()) {
                if (!spamCounts.containsKey(word)) {
                    ContentValues values = new ContentValues();
                    values.put(Constants.COL_WORD, word);
                    values.put(Constants.COL_SPAM_COUNT, 0);
                    values.put(Constants.COL_HAM_COUNT, hamCounts.get(word));
                    db.insertWithOnConflict(Constants.TABLE_WORD_COUNTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void loadWordCounts(Map<String, Integer> spamCounts, Map<String, Integer> hamCounts) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_WORD_COUNTS, null, null, null, null, null, null);
        if (cursor != null) {
            try {
                int wordIdx = cursor.getColumnIndexOrThrow(Constants.COL_WORD);
                int spamIdx = cursor.getColumnIndexOrThrow(Constants.COL_SPAM_COUNT);
                int hamIdx = cursor.getColumnIndexOrThrow(Constants.COL_HAM_COUNT);
                while (cursor.moveToNext()) {
                    String word = cursor.getString(wordIdx);
                    spamCounts.put(word, cursor.getInt(spamIdx));
                    hamCounts.put(word, cursor.getInt(hamIdx));
                }
            } finally {
                cursor.close();
            }
        }
    }

    public void saveGlobalStats(int totalSpam, int totalHam, int spamWords, int hamWords) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            saveStat(db, Constants.KEY_TOTAL_SPAM_MSGS, totalSpam);
            saveStat(db, Constants.KEY_TOTAL_HAM_MSGS, totalHam);
            saveStat(db, Constants.KEY_SPAM_TOTAL_WORDS, spamWords);
            saveStat(db, Constants.KEY_HAM_TOTAL_WORDS, hamWords);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void saveStat(SQLiteDatabase db, String key, int value) {
        ContentValues values = new ContentValues();
        values.put(Constants.COL_KEY, key);
        values.put(Constants.COL_VALUE, value);
        db.insertWithOnConflict(Constants.TABLE_GLOBAL_STATS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Map<String, Integer> loadGlobalStats() {
        Map<String, Integer> stats = new HashMap<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_GLOBAL_STATS, null, null, null, null, null, null);
        if (cursor != null) {
            try {
                int keyIdx = cursor.getColumnIndexOrThrow(Constants.COL_KEY);
                int valIdx = cursor.getColumnIndexOrThrow(Constants.COL_VALUE);
                while (cursor.moveToNext()) {
                    stats.put(cursor.getString(keyIdx), cursor.getInt(valIdx));
                }
            } finally {
                cursor.close();
            }
        }
        return stats;
    }
}
