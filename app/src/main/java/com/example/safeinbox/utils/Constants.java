package com.example.safeinbox.utils;

public class Constants {

    // Database
    public static final String DB_NAME = "safeinbox.db";
    public static final int DB_VERSION = 12;
    public static final int CURRENT_ENGINE_VERSION = 4; // Bumped to 4 for production-grade engine overhaul
    
    // Broadcasts
    public static final String ACTION_DATABASE_RESCANNED = "com.example.safeinbox.DATABASE_RESCANNED";

    // Table: messages
    public static final String TABLE_MESSAGES = "messages";
    public static final String COL_ID = "id";
    public static final String COL_SENDER = "sender";
    public static final String COL_SENDER_NAME = "sender_name";
    public static final String COL_BODY = "body";
    public static final String COL_DATE = "date";
    public static final String COL_IS_SPAM = "is_spam";
    public static final String COL_IS_BLOCKED = "is_blocked";
    public static final String COL_DEDUP_ID = "dedup_id";
    public static final String COL_CLASSIFICATION_STATUS = "classification_status";
    public static final String COL_HAS_RISKY_LINK = "has_risky_link";
    public static final String COL_SCORE = "score";

    // Table: spam_numbers
    public static final String TABLE_SPAM_NUMBERS = "spam_numbers";
    public static final String COL_NUMBER = "number";

    // Table: deleted_messages (Blacklist to prevent re-syncing deleted items)
    public static final String TABLE_DELETED_MESSAGES = "deleted_messages";

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

    // Table: sender_scores
    public static final String TABLE_SENDER_SCORES = "sender_scores";
    public static final String COL_SENDER_KEY = "sender_key";
    public static final String COL_SPAM_HITS = "spam_hits";
    public static final String COL_HAM_HITS = "ham_hits";
    public static final String COL_USER_SPAM_REPORTS = "user_spam_reports";
    public static final String COL_USER_HAM_REPORTS = "user_ham_reports";
    public static final String COL_LAST_UPDATED = "last_updated";

    // Score weights
    public static final int WEIGHT_KEYWORD = 40;
    public static final int WEIGHT_ML = 30;
    public static final int WEIGHT_HISTORY = 20;
    public static final int WEIGHT_FEEDBACK = 15;
    public static final int WEIGHT_CONTACT = 50;
    public static final int WEIGHT_SAFE_HISTORY = 20;

    public static final String KEY_TOTAL_SPAM_MSGS = "total_spam_messages";
    public static final String KEY_TOTAL_HAM_MSGS = "total_ham_messages";
    public static final String KEY_SPAM_TOTAL_WORDS = "spam_total_words";
    public static final String KEY_HAM_TOTAL_WORDS = "ham_total_words";

    // Feedback labels
    public static final String LABEL_SPAM = "spam";
    public static final String LABEL_HAM = "ham";

    // Permission request code
    public static final int PERMISSION_REQUEST_CODE = 100;

    // Preferences
    public static final String PREFS_NAME = "SafeInboxPrefs";
    public static final String KEY_ARCHIVED_IDS = "archived_message_ids";
    public static final String KEY_ARCHIVED_DEDUP_IDS = "archived_dedup_ids";
}
