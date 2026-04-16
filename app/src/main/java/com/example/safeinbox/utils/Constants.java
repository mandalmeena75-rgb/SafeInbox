package com.example.safeinbox.utils;

public class Constants {

    // Database
    public static final String DB_NAME = "safeinbox.db";
    public static final int DB_VERSION = 1;

    // Table: messages
    public static final String TABLE_MESSAGES = "messages";
    public static final String COL_ID = "id";
    public static final String COL_SENDER = "sender";
    public static final String COL_BODY = "body";
    public static final String COL_DATE = "date";
    public static final String COL_IS_SPAM = "is_spam";

    // Table: spam_numbers
    public static final String TABLE_SPAM_NUMBERS = "spam_numbers";
    public static final String COL_NUMBER = "number";

    // Table: feedback
    public static final String TABLE_FEEDBACK = "feedback";
    public static final String COL_MESSAGE_ID = "message_id";
    public static final String COL_LABEL = "label";

    // Feedback labels
    public static final String LABEL_SPAM = "spam";
    public static final String LABEL_NOT_SPAM = "not_spam";

    // Permission request code
    public static final int PERMISSION_REQUEST_CODE = 100;
}
