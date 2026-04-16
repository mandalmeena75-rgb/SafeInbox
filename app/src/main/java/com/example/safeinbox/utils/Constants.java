package com.example.safeinbox.utils;

public class Constants {

    // Database
    public static final String DB_NAME = "safeinbox.db";
    public static final int DB_VERSION = 5;

    // Table: messages
    public static final String TABLE_MESSAGES = "messages";
    public static final String COL_ID = "id";
    public static final String COL_SENDER = "sender";
    public static final String COL_SENDER_NAME = "sender_name";
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

    // Table: word_counts (ML Persistence)
    public static final String TABLE_WORD_COUNTS = "word_counts";
    public static final String COL_WORD = "word";
    public static final String COL_SPAM_COUNT = "spam_count";
    public static final String COL_HAM_COUNT = "ham_count";

    // Table: global_stats (ML Persistence)
    public static final String TABLE_GLOBAL_STATS = "global_stats";
    public static final String COL_KEY = "stat_key";
    public static final String COL_VALUE = "stat_value";

    public static final String KEY_TOTAL_SPAM_MSGS = "total_spam_messages";
    public static final String KEY_TOTAL_HAM_MSGS = "total_ham_messages";
    public static final String KEY_SPAM_TOTAL_WORDS = "spam_total_words";
    public static final String KEY_HAM_TOTAL_WORDS = "ham_total_words";

    // Feedback labels
    public static final String LABEL_SPAM = "spam";
    public static final String LABEL_NOT_SPAM = "not_spam";

    // Permission request code
    public static final int PERMISSION_REQUEST_CODE = 100;
}
