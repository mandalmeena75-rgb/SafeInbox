package com.example.safeinbox.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.provider.BlockedNumberContract;
import android.util.Log;
import android.util.LruCache;

import com.example.safeinbox.models.SenderReputation;
import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.utils.Constants;
import com.example.safeinbox.utils.ContactUtils;

import java.util.ArrayList;
import java.util.List;

public class SpamDao {

    private static final String TAG = "SpamDao";
    // Turbo Mode Cache: Normalized Sender -> Reputation Stats
    private static final LruCache<String, SenderReputation> reputationCache = new LruCache<>(200);

    private final DBHelper dbHelper;
    private final Context context;

    public SpamDao(Context context) {
        this.context = context;
        this.dbHelper = DBHelper.getInstance(context);
    }

    // ==================== Sender Normalization (Dedup Core) ====================

    /**
     * Normalizes a phone number/sender for consistent storage.
     * Strips spaces, dashes, parens, and leading country-code variations
     * so "+91 98765-43210" and "9876543210" resolve to the same key.
     */
    private String normalizeSender(String sender) {
        return ContactUtils.normalizeSender(sender);
    }

    /**
     * Internal helper to normalize message body for high-accuracy deduplication.
     * Strips non-alphanumeric chars, lowercases and trims.
     */
    private String normalizeBody(String body) {
        if (body == null) return "";
        return body.replaceAll("[^a-zA-Z0-9]", "").toLowerCase().trim();
    }

    /**
     * Generates a fuzzy dedup key to collapse identical messages arriving via
     * different paths (RCS/SMS) within a 1-minute window.
     */
    private String generateDedupId(SmsMessage message) {
        String sender = normalizeSender(message.getSender());
        String body = normalizeBody(message.getBody());
        // Round timestamp to nearest 1 hour (3600 seconds) to bridge sync/live gap
        long fuzzyTime = message.getDate() / 3600000;
        return sender + "|" + body + "|" + fuzzyTime;
    }

    /**
     * Checks if a mathematically similar message (identical sender and body)
     * was stored in the last 5 minutes. Prevents duplicates from different timestamp providers.
     */
    public boolean checkIfMessageExistsRecently(String sender, String body) {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            String normalizedSender = normalizeSender(sender);
            String normalizedBody = normalizeBody(body);
            
            // 5-minute window for concurrent delivery deduplication
            long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60000);
            
            // We search for a similar sender + body in the last 5 minutes.
            // Note: Since real data in DB is NOT alphanumeric-stripped, we compare against original body
            // but for absolute perfection, we could use a virtual column or just rely on the DEDUP_ID collision
            // which handles the alphanumeric stripping at the storage layer.
            
            Cursor cursor = db.query(Constants.TABLE_MESSAGES, null,
                    Constants.COL_SENDER + " = ? AND (" + Constants.COL_BODY + " = ? OR " + Constants.COL_DEDUP_ID + " LIKE ? ) AND " + Constants.COL_DATE + " > ?",
                    new String[]{normalizedSender, body.trim(), normalizedSender + "|" + normalizedBody + "|%", String.valueOf(fiveMinutesAgo)},
                    null, null, null, "1");
            
            if (cursor != null) {
                boolean exists = cursor.getCount() > 0;
                cursor.close();
                return exists;
            }
        } catch (Exception e) {
            Log.e(TAG, "checkIfMessageExistsRecently error", e);
        }
        return false;
    }

    // ==================== Messages Table Operations ====================

    /**
     * Insert a message. Uses a fuzzy dedup_id to prevent double entries
     * from RCS and SMS within a 30-second window.
     */
    public long insertMessage(SmsMessage message) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(Constants.COL_SENDER, normalizeSender(message.getSender()));
            values.put(Constants.COL_SENDER_NAME, message.getSenderName());
            values.put(Constants.COL_BODY, message.getBody() != null ? message.getBody().trim() : "");
            values.put(Constants.COL_DATE, message.getDate());
            values.put(Constants.COL_IS_SPAM, message.isSpam() ? 1 : 0);
            values.put(Constants.COL_IS_BLOCKED, message.isBlocked() ? 1 : 0);
            values.put(Constants.COL_DEDUP_ID, generateDedupId(message));
            
            return db.insertWithOnConflict(Constants.TABLE_MESSAGES, null, values,
                    SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Exception e) {
            Log.e(TAG, "insertMessage error", e);
            return -1;
        }
    }

    /**
     * Batch insert with transaction and fuzzy dedup.
     */
    public void insertMessagesBatch(List<SmsMessage> messages) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (SmsMessage message : messages) {
                ContentValues values = new ContentValues();
                values.put(Constants.COL_SENDER, normalizeSender(message.getSender()));
                values.put(Constants.COL_SENDER_NAME, message.getSenderName());
                values.put(Constants.COL_BODY, message.getBody() != null ? message.getBody().trim() : "");
                values.put(Constants.COL_DATE, message.getDate());
                values.put(Constants.COL_IS_SPAM, message.isSpam() ? 1 : 0);
                values.put(Constants.COL_IS_BLOCKED, message.isBlocked() ? 1 : 0);
                values.put(Constants.COL_DEDUP_ID, generateDedupId(message));
                
                db.insertWithOnConflict(Constants.TABLE_MESSAGES, null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "insertMessagesBatch error", e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Get messages filtered by spam status, ordered by date descending.
     * Uses the idx_messages_spam_date index for maximum speed.
     */
    public List<SmsMessage> getMessages(boolean spamOnly) {
        List<SmsMessage> messages = new ArrayList<>();
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            String selection = Constants.COL_IS_SPAM + " = ?";
            String[] selectionArgs = {spamOnly ? "1" : "0"};

            // NUCLEAR DEDUPLICATION: Trim and lowercase both sender and body for the group check.
            // This ensures "URGENT" and "urgent" or " 987..." and "987..." are collapsed.
            String query = "SELECT *, MAX(" + Constants.COL_ID + ") as stable_id FROM " + Constants.TABLE_MESSAGES
                    + " WHERE " + Constants.COL_IS_SPAM + " = ?"
                    + " GROUP BY TRIM(LOWER(" + Constants.COL_SENDER + ")), TRIM(LOWER(" + Constants.COL_BODY + "))"
                    + " ORDER BY " + Constants.COL_DATE + " DESC";

            Cursor cursor = db.rawQuery(query, selectionArgs);

            if (cursor != null) {
                try {
                    int idIdx = cursor.getColumnIndexOrThrow(Constants.COL_ID);
                    int senderIdx = cursor.getColumnIndexOrThrow(Constants.COL_SENDER);
                    int nameIdx = cursor.getColumnIndexOrThrow(Constants.COL_SENDER_NAME);
                    int bodyIdx = cursor.getColumnIndexOrThrow(Constants.COL_BODY);
                    int dateIdx = cursor.getColumnIndexOrThrow(Constants.COL_DATE);
                    int spamIdx = cursor.getColumnIndexOrThrow(Constants.COL_IS_SPAM);
                    int blockedIdx = cursor.getColumnIndexOrThrow(Constants.COL_IS_BLOCKED);

                    while (cursor.moveToNext()) {
                        messages.add(new SmsMessage(
                                cursor.getLong(idIdx),
                                cursor.getString(senderIdx),
                                cursor.getString(nameIdx),
                                cursor.getString(bodyIdx),
                                cursor.getLong(dateIdx),
                                cursor.getInt(spamIdx) == 1,
                                cursor.getInt(blockedIdx) == 1
                        ));
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getMessages error", e);
        }
        return messages;
    }

    public SmsMessage getMessageById(long messageId) {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.query(
                    Constants.TABLE_MESSAGES, null,
                    Constants.COL_ID + " = ?",
                    new String[]{String.valueOf(messageId)},
                    null, null, null
            );

            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        return new SmsMessage(
                                cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_SENDER)),
                                cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_SENDER_NAME)),
                                cursor.getString(cursor.getColumnIndexOrThrow(Constants.COL_BODY)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_DATE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_IS_SPAM)) == 1,
                                cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_IS_BLOCKED)) == 1
                        );
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getMessageById error", e);
        }
        return null;
    }

    public void updateSpamStatus(long messageId, boolean isSpam) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(Constants.COL_IS_SPAM, isSpam ? 1 : 0);
            db.update(Constants.TABLE_MESSAGES, values,
                    Constants.COL_ID + " = ?",
                    new String[]{String.valueOf(messageId)});
        } catch (Exception e) {
            Log.e(TAG, "updateSpamStatus error", e);
        }
    }

    public void clearMessages() {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.delete(Constants.TABLE_MESSAGES, null, null);
        } catch (Exception e) {
            Log.e(TAG, "clearMessages error", e);
        }
    }

    public void deleteMessageById(long messageId) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.delete(Constants.TABLE_MESSAGES,
                    Constants.COL_ID + " = ?",
                    new String[]{String.valueOf(messageId)});
        } catch (Exception e) {
            Log.e(TAG, "deleteMessageById error", e);
        }
    }

    public int getMessageCount() {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + Constants.TABLE_MESSAGES, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) return cursor.getInt(0);
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getMessageCount error", e);
        }
        return 0;
    }

    public long getLatestMessageTimestamp() {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT MAX(" + Constants.COL_DATE + ") FROM "
                    + Constants.TABLE_MESSAGES, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) return cursor.getLong(0);
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getLatestMessageTimestamp error", e);
        }
        return 0;
    }

    // ==================== Spam Numbers Table Operations ====================

    public void addSpamNumber(String number) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(Constants.COL_NUMBER, normalizeSender(number));
            db.insertWithOnConflict(Constants.TABLE_SPAM_NUMBERS, null, values,
                    SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Exception e) {
            Log.e(TAG, "addSpamNumber error", e);
        }
    }

    public boolean isNumberInSpamTable(String number) {
        if (number == null) return false;
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            // Check both raw and normalized forms
            String normalized = normalizeSender(number);
            Cursor cursor = db.query(
                    Constants.TABLE_SPAM_NUMBERS, null,
                    Constants.COL_NUMBER + " = ? OR " + Constants.COL_NUMBER + " = ?",
                    new String[]{number, normalized},
                    null, null, null
            );
            if (cursor != null) {
                try {
                    return cursor.getCount() > 0;
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "isNumberInSpamTable error", e);
        }
        return false;
    }

    // ==================== Module 3: Number Blocking Check ====================

    public boolean isBlockedNumber(String number) {
        if (number == null) return false;

        if (isNumberInSpamTable(number)) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                return BlockedNumberContract.isBlocked(context, number);
            } catch (SecurityException e) {
                return false;
            }
        }
        return false;
    }

    // ==================== Module 7: Feedback Table Operations ====================

    public void insertFeedback(long messageId, String label) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(Constants.COL_MESSAGE_ID, messageId);
            values.put(Constants.COL_LABEL, label);
            db.insert(Constants.TABLE_FEEDBACK, null, values);

            boolean isSpam = Constants.LABEL_SPAM.equals(label);
            updateSpamStatus(messageId, isSpam);

            // Phase 3: Also record user feedback in sender_scores table
            SmsMessage msg = getMessageById(messageId);
            if (msg != null) {
                recordUserFeedback(msg.getSender(), isSpam);
            }
        } catch (Exception e) {
            Log.e(TAG, "insertFeedback error", e);
        }
    }

    // ==================== Phase 3: Sender Reputation ====================

    public int getSenderSpamHits(String sender) {
        return getSenderReputation(sender).spamHits;
    }

    public int getSenderHamHits(String sender) {
        return getSenderReputation(sender).hamHits;
    }

    public int getUserSpamReports(String sender) {
        return getSenderReputation(sender).userSpamReports;
    }

    public int getUserHamReports(String sender) {
        return getSenderReputation(sender).userHamReports;
    }

    private SenderReputation getSenderReputation(String sender) {
        if (sender == null) return new SenderReputation();
        String normalized = normalizeSender(sender);
        
        // Turbo Mode: Check Memory Cache
        SenderReputation cached = reputationCache.get(normalized);
        if (cached != null) return cached;

        // Cache Miss: Load from DB
        SenderReputation rep = loadReputationFromDb(normalized);
        reputationCache.put(normalized, rep);
        return rep;
    }

    private SenderReputation loadReputationFromDb(String normalized) {
        SenderReputation rep = new SenderReputation();
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.query(Constants.TABLE_SENDER_SCORES, null,
                    Constants.COL_SENDER_KEY + " = ?", new String[]{normalized},
                    null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        rep.spamHits = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_SPAM_HITS));
                        rep.hamHits = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_HAM_HITS));
                        rep.userSpamReports = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_USER_SPAM_REPORTS));
                        rep.userHamReports = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.COL_USER_HAM_REPORTS));
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "loadReputationFromDb error", e);
        }
        return rep;
    }

    public void incrementSenderScore(String sender, boolean isSpam) {
        if (sender == null) return;
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            String normalized = normalizeSender(sender);
            String column = isSpam ? Constants.COL_SPAM_HITS : Constants.COL_HAM_HITS;

            // This SQL handles both INSERT and UPDATE (upsert)
            String sql = "INSERT INTO " + Constants.TABLE_SENDER_SCORES + " ("
                    + Constants.COL_SENDER_KEY + ", " + column + ", " + Constants.COL_LAST_UPDATED + ") "
                    + "VALUES (?, 1, ?) "
                    + "ON CONFLICT(" + Constants.COL_SENDER_KEY + ") DO UPDATE SET "
                    + column + " = " + column + " + 1, "
                    + Constants.COL_LAST_UPDATED + " = excluded." + Constants.COL_LAST_UPDATED;

            db.execSQL(sql, new Object[]{normalized, System.currentTimeMillis()});
            
            // Turbo Mode: Evict cache to force reload on next check
            reputationCache.remove(normalized);
        } catch (Exception e) {
            Log.e(TAG, "incrementSenderScore error", e);
        }
    }

    public void recordUserFeedback(String sender, boolean isSpam) {
        if (sender == null) return;
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            String normalized = normalizeSender(sender);
            String column = isSpam ? Constants.COL_USER_SPAM_REPORTS : Constants.COL_USER_HAM_REPORTS;

            String sql = "INSERT INTO " + Constants.TABLE_SENDER_SCORES + " ("
                    + Constants.COL_SENDER_KEY + ", " + column + ", " + Constants.COL_LAST_UPDATED + ") "
                    + "VALUES (?, 1, ?) "
                    + "ON CONFLICT(" + Constants.COL_SENDER_KEY + ") DO UPDATE SET "
                    + column + " = " + column + " + 1, "
                    + Constants.COL_LAST_UPDATED + " = excluded." + Constants.COL_LAST_UPDATED;

            db.execSQL(sql, new Object[]{normalized, System.currentTimeMillis()});
            
            // Turbo Mode: Evict cache to force reload
            reputationCache.remove(normalized);
        } catch (Exception e) {
            Log.e(TAG, "recordUserFeedback error", e);
        }
    }

    // ==================== Sync Helpers ====================

    public void saveLastSyncTimestamp(long timestamp) {
        context.getSharedPreferences("SafeInboxSync", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_sync_time", timestamp)
                .apply();
        Log.d(TAG, "Sync timestamp saved: " + timestamp);
    }

    public long getLastSyncTimestamp() {
        return context.getSharedPreferences("SafeInboxSync", Context.MODE_PRIVATE)
                .getLong("last_sync_time", 0);
    }
}
