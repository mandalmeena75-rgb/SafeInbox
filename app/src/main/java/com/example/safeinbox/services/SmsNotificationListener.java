package com.example.safeinbox.services;

import android.app.Notification;
import android.app.Person;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.util.LruCache;

import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.detection.SpamDetector;
import com.example.safeinbox.models.ClassificationResult;
import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.utils.ContactUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsNotificationListener extends NotificationListenerService {

    private static final String TAG = "SmsNotifListener";

    /** Broadcast so InboxActivity can refresh when a new RCS message is saved. */
    public static final String ACTION_RCS_MESSAGE_RECEIVED =
            "com.example.safeinbox.RCS_MESSAGE_RECEIVED";

    // All known default messaging app packages across major OEMs
    private static final Set<String> SUPPORTED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging",   // Google Messages (RCS)
            "com.samsung.android.messaging",        // Samsung Messages
            "com.android.mms",                      // AOSP MMS
            "com.android.messaging",                // AOSP Messaging
            "com.oneplus.mms",                      // OnePlus Messages
            "com.sonyericsson.conversations",       // Sony
            "com.htc.cs.android.mms",               // HTC
            "com.xiaomi.mms",                       // Xiaomi MIUI Messages
            "com.miui.mms",                         // MIUI alternate
            "com.huawei.message",                   // Huawei
            "com.vivo.message",                     // Vivo
            "com.oppo.communication.mms",           // Oppo
            "com.realme.communication.mms",         // Realme
            "com.motorola.messaging",               // Motorola
            "com.asus.message"                      // Asus
    ));

    private SpamDetector spamDetector;
    private SpamDao spamDao;

    // Single-threaded executor to process messages sequentially (no DB contention)
    private ExecutorService executor;

    // Dedup cache: hash(sender|body|5s-bucket) → true
    private final LruCache<Integer, Boolean> processedCache = new LruCache<>(200);

    // ───────────────────────────── Lifecycle ─────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        initDependencies();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    private void initDependencies() {
        try {
            spamDetector = SpamDetector.getInstance(this);
            spamDao = new SpamDao(this);
        } catch (Exception e) {
            Log.e(TAG, "initDependencies failed", e);
        }
    }

    // ─────────────────────── Notification interception ───────────────────────

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            handleNotification(sbn);
        } catch (Exception e) {
            Log.e(TAG, "onNotificationPosted: unexpected error", e);
        }
    }

    private void handleNotification(StatusBarNotification sbn) {
        // Re-init if the service was killed then restarted without onCreate
        if (spamDetector == null || spamDao == null) {
            initDependencies();
        }
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }
        if (spamDetector == null || spamDao == null) {
            Log.e(TAG, "Cannot process: dependencies null");
            return;
        }

        if (!SUPPORTED_PACKAGES.contains(sbn.getPackageName())) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;
        Bundle extras = notification.extras;
        if (extras == null) return;

        Log.d(TAG, "Notification from: " + sbn.getPackageName());

        // ── Modern MessagingStyle path (Android 9+) ───────────────────────────
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                Parcelable[] msgArray = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
                if (msgArray != null && msgArray.length > 0) {
                    Log.d(TAG, "Found " + msgArray.length + " bundled message(s)");
                    for (Parcelable p : msgArray) {
                        if (!(p instanceof Bundle)) continue;
                        processBundledMessage((Bundle) p, sbn.getPostTime(), notification);
                    }
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "MessagingStyle parsing failed, falling back", e);
            }
        }

        // ── Fallback: plain title/text notification ───────────────────────────
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text  = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (title != null && text != null
                && !text.toString().trim().isEmpty()
                && !isGroupSummary(text.toString())) {
            saveMessage(title.toString(), text.toString(), sbn.getPostTime(), notification);
        }
    }

    /** Filters out group summary notifications like "3 new messages" */
    private boolean isGroupSummary(String text) {
        return text.matches("^\\d+ new messages?$")
                || text.matches("^\\d+ unread messages?$");
    }

    /** Extracts sender + text from one entry of a MessagingStyle bundle array. */
    private void processBundledMessage(Bundle msgBundle, long fallbackTime,
                                       Notification notification) {
        try {
            CharSequence text = msgBundle.getCharSequence("text");
            if (text == null || text.toString().trim().isEmpty()) return;

            // Each message has its OWN timestamp – critical for DB uniqueness
            long msgTime = msgBundle.getLong("time", fallbackTime);
            if (msgTime == 0) msgTime = fallbackTime;

            String senderName = null;

            // Extract sender from Person object (preferred)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    Object raw = msgBundle.getParcelable("sender_person");
                    if (raw instanceof Person) {
                        Person person = (Person) raw;
                        if (person.getName() != null) {
                            senderName = person.getName().toString();
                        }
                        if (person.getUri() != null && person.getUri().startsWith("tel:")) {
                            String number = person.getUri().substring(4);
                            saveMessage(senderName != null ? senderName : number,
                                    text.toString(), msgTime, notification);
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "sender_person extraction failed", e);
                }
            }

            // Fallback: plain "sender" key
            if (senderName == null) {
                CharSequence senderSeq = msgBundle.getCharSequence("sender");
                if (senderSeq != null && !senderSeq.toString().trim().isEmpty()) {
                    senderName = senderSeq.toString();
                }
            }
            
            // Last resort: try to identify sender from message body
            if (senderName == null) {
                String bodyName = com.example.safeinbox.utils.PrincipalEntityResolver.resolveFromBody(text.toString());
                senderName = (bodyName != null) ? bodyName : "";
            }

            saveMessage(senderName, text.toString(), msgTime, notification);
        } catch (Exception e) {
            Log.e(TAG, "processBundledMessage error", e);
        }
    }

    // ─────────────────── Core save pipeline ──────────────────────────────────

    /** Dedup → resolve number → classify → insert to DB → broadcast. */
    private void saveMessage(String senderName, String body,
                             long timestamp, Notification notification) {
        if (senderName == null || senderName.equalsIgnoreCase("Unknown")) {
            // Try to resolve from message body instead of storing "Unknown"
            String bodyResolved = com.example.safeinbox.utils.PrincipalEntityResolver.resolveFromBody(body);
            senderName = (bodyResolved != null) ? bodyResolved : "";
        }
        if (body == null || body.trim().isEmpty()) return;

        // Deduplicate: same sender+body within 5-second window = notification update
        int hashKey = (senderName.toLowerCase() + "|" + body.trim().toLowerCase()
                + "|" + (timestamp / 5000L)).hashCode();
        if (Boolean.TRUE.equals(processedCache.get(hashKey))) {
            return; // Already processed
        }
        processedCache.put(hashKey, Boolean.TRUE);

        final String finalSenderName = senderName;
        final long finalTimestamp = timestamp;
        final String finalBody = body.trim();

        // Try to extract a phone number from the notification
        final String discoveredNumber = extractPhoneNumber(notification);

        Log.d(TAG, "Queuing RCS – sender: " + finalSenderName
                + " | number: " + discoveredNumber
                + " | body: " + finalBody.substring(0, Math.min(50, finalBody.length())));

        // Execute on single-threaded executor to avoid DB contention
        executor.execute(() -> {
            try {
                String resolvedNumber = discoveredNumber;
                if (resolvedNumber == null) {
                    resolvedNumber = ContactUtils.getPhoneNumberByName(
                            SmsNotificationListener.this, finalSenderName);
                }
                String finalSender = (resolvedNumber != null) ? resolvedNumber : finalSenderName;

                // Advanced Dedup: Check if this message was already processed in the last 60s
                if (spamDao.checkIfMessageExistsRecently(finalSender, finalBody)) {
                    Log.d(TAG, "Duplicate RCS detected, skipping");
                    return;
                }

                // Use the unified classification engine (SAFE / SUSPICIOUS / SPAM)
                ClassificationResult result = spamDetector.classifyWithDetails(finalSender, finalBody);

                SmsMessage msg = new SmsMessage(finalSender, finalBody, finalTimestamp);
                msg.setSenderName(finalSenderName);
                msg.setClassificationStatus(result.status.name());

                long rowId = spamDao.insertMessage(msg);
                if (rowId > 0) {
                    // Phase 3: Increment sender score based on classification
                    boolean verdictIsSpam = (result.status == ClassificationResult.Status.SPAM);
                    spamDao.incrementSenderScore(finalSender, verdictIsSpam);

                    // Send broadcast with explicit package to guarantee delivery
                    Intent broadcast = new Intent(ACTION_RCS_MESSAGE_RECEIVED);
                    broadcast.setPackage(getPackageName());
                    sendBroadcast(broadcast);
                    Log.d(TAG, "✓ Saved RCS id=" + rowId + ", Status: " + result.status.name());
                } else {
                    Log.d(TAG, "– Duplicate from trigger, skipped");
                }
            } catch (Exception e) {
                Log.e(TAG, "saveMessage background error", e);
            }
        });
    }

    // ──────────────────── Phone number extraction ─────────────────────────────

    private String extractPhoneNumber(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return null;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                Parcelable[] msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
                if (msgs != null) {
                    for (Parcelable p : msgs) {
                        if (!(p instanceof Bundle)) continue;
                        Object raw = ((Bundle) p).getParcelable("sender_person");
                        if (raw instanceof Person) {
                            Person person = (Person) raw;
                            if (person.getUri() != null && person.getUri().startsWith("tel:")) {
                                return person.getUri().substring(4);
                            }
                        }
                    }
                }
            } catch (Exception e) { /* ignore */ }

            try {
                Object raw = extras.getParcelable("android.messagingStyleUser");
                if (raw instanceof Person) {
                    Person user = (Person) raw;
                    if (user.getUri() != null && user.getUri().startsWith("tel:")) {
                        return user.getUri().substring(4);
                    }
                }
            } catch (Exception e) { /* ignore */ }
        }

        return scanBundleForPhoneNumber(extras);
    }

    private String scanBundleForPhoneNumber(Bundle bundle) {
        if (bundle == null) return null;
        try {
            for (String key : bundle.keySet()) {
                Object val = bundle.get(key);
                if (val instanceof CharSequence) {
                    String cleaned = val.toString().replaceAll("[\\s\\-\\(\\)]", "");
                    if (cleaned.matches("^\\+?[0-9]{8,15}$")) return cleaned;
                } else if (val instanceof Bundle) {
                    String inner = scanBundleForPhoneNumber((Bundle) val);
                    if (inner != null) return inner;
                } else if (val instanceof Parcelable[]) {
                    for (Parcelable p : (Parcelable[]) val) {
                        if (p instanceof Bundle) {
                            String inner = scanBundleForPhoneNumber((Bundle) p);
                            if (inner != null) return inner;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "scanBundleForPhoneNumber error", e);
        }
        return null;
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not needed
    }
}
