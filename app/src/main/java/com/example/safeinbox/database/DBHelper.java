package com.example.safeinbox.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.safeinbox.utils.Constants;

public class DBHelper extends SQLiteOpenHelper {

    public DBHelper(Context context) {
        super(context, Constants.DB_NAME, null, Constants.DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createMessages = "CREATE TABLE " + Constants.TABLE_MESSAGES + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_SENDER + " TEXT, "
                + Constants.COL_BODY + " TEXT, "
                + Constants.COL_DATE + " INTEGER, "
                + Constants.COL_IS_SPAM + " INTEGER DEFAULT 0)";
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + Constants.TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.TABLE_SPAM_NUMBERS);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.TABLE_FEEDBACK);
        onCreate(db);
    }
}
