package com.example.safeinbox.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.safeinbox.utils.Constants;

public class DBHelper extends SQLiteOpenHelper {

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
    public void onCreate(SQLiteDatabase db) {
        String createMessages = "CREATE TABLE " + Constants.TABLE_MESSAGES + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_SENDER + " TEXT, "
                + Constants.COL_SENDER_NAME + " TEXT, "
                + Constants.COL_BODY + " TEXT, "
                + Constants.COL_DATE + " INTEGER, "
                + Constants.COL_IS_SPAM + " INTEGER DEFAULT 0, "
                + "UNIQUE(" + Constants.COL_SENDER + ", " + Constants.COL_BODY + ", " + Constants.COL_DATE + "))";
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + Constants.TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.TABLE_SPAM_NUMBERS);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.TABLE_FEEDBACK);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.TABLE_WORD_COUNTS);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.TABLE_GLOBAL_STATS);
        onCreate(db);
    }
}
