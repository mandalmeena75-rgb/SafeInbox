package com.example.safeinbox.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.safeinbox.utils.Constants;

public class DBHelper extends SQLiteOpenHelper {

    private static final String TAG = "DBHelper";
    private static DBHelper instance;

    public static synchronized DBHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DBHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DBHelper(Context context) {
        super(context, Constants.DB_NAME, null, Constants.DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        try {
            // Turbo Mode: Enable Write-Ahead Logging for concurrent read/write
            db.enableWriteAheadLogging();
            // Turbo Mode: Speed up writes by using NORMAL synchronization
            db.execSQL("PRAGMA synchronous = NORMAL");
        } catch (Exception e) {
            Log.e(TAG, "onConfigure Turbo Mode failed", e);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Messages table with UNIQUE constraint to prevent duplicates
        String createMessages = "CREATE TABLE " + Constants.TABLE_MESSAGES + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_SENDER + " TEXT, "
                + Constants.COL_SENDER_NAME + " TEXT, "
                + Constants.COL_BODY + " TEXT, "
                + Constants.COL_DATE + " INTEGER, "
                + Constants.COL_IS_SPAM + " INTEGER DEFAULT 0, "
                + Constants.COL_IS_BLOCKED + " INTEGER DEFAULT 0, "
                + Constants.COL_DEDUP_ID + " TEXT, "
                + "UNIQUE(" + Constants.COL_DEDUP_ID + "))";
        db.execSQL(createMessages);

        String createSpamNumbers = "CREATE TABLE " + Constants.TABLE_SPAM_NUMBERS + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_NUMBER + " TEXT UNIQUE)";
        db.execSQL(createSpamNumbers);

        String createFeedback = "CREATE TABLE " + Constants.TABLE_FEEDBACK + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_MESSAGE_ID + " INTEGER, "
                + Constants.COL_LABEL + " TEXT)";
        db.execSQL(createFeedback);

        String createWordCounts = "CREATE TABLE " + Constants.TABLE_WORD_COUNTS + " ("
                + Constants.COL_WORD + " TEXT PRIMARY KEY, "
                + Constants.COL_SPAM_COUNT + " INTEGER DEFAULT 0, "
                + Constants.COL_HAM_COUNT + " INTEGER DEFAULT 0)";
        db.execSQL(createWordCounts);

        String createGlobalStats = "CREATE TABLE " + Constants.TABLE_GLOBAL_STATS + " ("
                + Constants.COL_KEY + " TEXT PRIMARY KEY, "
                + Constants.COL_VALUE + " INTEGER DEFAULT 0)";
        db.execSQL(createGlobalStats);

        String createSenderScores = "CREATE TABLE " + Constants.TABLE_SENDER_SCORES + " ("
                + Constants.COL_SENDER_KEY + " TEXT PRIMARY KEY, "
                + Constants.COL_SPAM_HITS + " INTEGER DEFAULT 0, "
                + Constants.COL_HAM_HITS + " INTEGER DEFAULT 0, "
                + Constants.COL_USER_SPAM_REPORTS + " INTEGER DEFAULT 0, "
                + Constants.COL_USER_HAM_REPORTS + " INTEGER DEFAULT 0, "
                + Constants.COL_LAST_UPDATED + " INTEGER)";
        db.execSQL(createSenderScores);

        // ── TURBO INDEXES for fast queries ──────────────────────────────────
        createIndexes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading DB from v" + oldVersion + " to v" + newVersion);

        if (oldVersion < 6) {
            // v6: Add performance indexes (non-destructive – no data loss)
            createIndexes(db);
        }
        
        if (oldVersion < 7) {
            // v7: Add dedup_id column and unique index
            try {
                db.execSQL("ALTER TABLE " + Constants.TABLE_MESSAGES + " ADD COLUMN " + Constants.COL_DEDUP_ID + " TEXT");
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_dedup ON " 
                        + Constants.TABLE_MESSAGES + "(" + Constants.COL_DEDUP_ID + ")");
                Log.d(TAG, "v7 migration: dedup_id added");
            } catch (Exception e) {
                Log.e(TAG, "v7 migration failed", e);
            }
        }

        if (oldVersion < 8) {
            // v8: Add sender_scores table
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS " + Constants.TABLE_SENDER_SCORES + " ("
                        + Constants.COL_SENDER_KEY + " TEXT PRIMARY KEY, "
                        + Constants.COL_SPAM_HITS + " INTEGER DEFAULT 0, "
                        + Constants.COL_HAM_HITS + " INTEGER DEFAULT 0, "
                        + Constants.COL_USER_SPAM_REPORTS + " INTEGER DEFAULT 0, "
                        + Constants.COL_USER_HAM_REPORTS + " INTEGER DEFAULT 0, "
                        + Constants.COL_LAST_UPDATED + " INTEGER)");
                Log.d(TAG, "v8 migration: sender_scores table created");
            } catch (Exception e) {
                Log.e(TAG, "v8 migration failed", e);
            }
        }

        if (oldVersion < 9) {
            // v9: Add is_blocked column to messages
            try {
                db.execSQL("ALTER TABLE " + Constants.TABLE_MESSAGES + " ADD COLUMN " + Constants.COL_IS_BLOCKED + " INTEGER DEFAULT 0");
                Log.d(TAG, "v9 migration: is_blocked added");
            } catch (Exception e) {
                Log.e(TAG, "v9 migration failed", e);
            }
        }
    }

    /** Create indexes that make queries 10x faster. Safe to call multiple times. */
    private void createIndexes(SQLiteDatabase db) {
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_date ON "
                    + Constants.TABLE_MESSAGES + "(" + Constants.COL_DATE + " DESC)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_spam ON "
                    + Constants.TABLE_MESSAGES + "(" + Constants.COL_IS_SPAM + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_sender ON "
                    + Constants.TABLE_MESSAGES + "(" + Constants.COL_SENDER + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_spam_date ON "
                    + Constants.TABLE_MESSAGES + "(" + Constants.COL_IS_SPAM + ", " + Constants.COL_DATE + " DESC)");
            Log.d(TAG, "Performance indexes created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating indexes", e);
        }
    }
}
