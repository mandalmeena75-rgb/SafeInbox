package com.example.safeinbox.services;

import android.app.Notification;
import android.app.Person;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.detection.SpamDetector;
import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.utils.ContactUtils;

public class SmsNotificationListener extends NotificationListenerService {

    private static final String TAG = "SmsNotifListener";
    private static final String GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging";

    private SpamDetector spamDetector;
    private SpamDao spamDao;

    @Override
    public void onCreate() {
        super.onCreate();
        spamDetector = new SpamDetector(this);
        spamDao = new SpamDao(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Only intercept notifications from Google Messages (RCS)
        if (!GOOGLE_MESSAGES_PACKAGE.equals(sbn.getPackageName())) {
            return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        if (extras == null) return;

        // Extract sender and body
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);

        if (title == null || text == null) return;

        String sender = title.toString();
        String body = text.toString();
        long timestamp = sbn.getPostTime();

        // Advanced Discovery: Try to find a real phone number in the notification metadata
        String discoveredNumber = extractPhoneNumberFromNotification(notification);

        Log.d(TAG, "Intercepted message from " + sender + " (Number detected: " + discoveredNumber + "): " + body);

        // Classify and save
        new Thread(() -> {
            boolean isSpam = spamDetector.isSpam(discoveredNumber != null ? discoveredNumber : sender, body);
            
            String finalSender = discoveredNumber != null ? discoveredNumber : sender;
            String finalName = sender; 

            // If we only have a name (sender) and no number (discoveredNumber), try reverse lookup
            if (discoveredNumber == null) {
                String reverseLookup = ContactUtils.getPhoneNumberByName(this, sender);
                if (reverseLookup != null) {
                    finalSender = reverseLookup;
                }
            }

            SmsMessage message = new SmsMessage(finalSender, body, timestamp);
            message.setSenderName(finalName);
            message.setSpam(isSpam);

            // Our DB unique constraint handles deduplication with standard SMS receiver
            spamDao.insertMessage(message);
        }).start();
    }

    private String extractPhoneNumberFromNotification(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return null;

        // 1. Direct MessagingStyle extraction (Modern Android)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages != null && messages.length > 0) {
                for (Parcelable p : messages) {
                    Bundle msg = (Bundle) p;
                    Person person = (Person) msg.getParcelable("sender_person");
                    if (person != null && person.getUri() != null) {
                        String uri = person.getUri();
                        if (uri.startsWith("tel:")) return uri.substring(4);
                    }
                }
            }
            
            // Check the main user object
            Person user = extras.getParcelable("android.messagingStyleUser");
            if (user != null && user.getUri() != null) {
                String uri = user.getUri();
                if (uri.startsWith("tel:")) return uri.substring(4);
            }
        }

        // 2. Ultimate Recursive Scraper: Brute force search every key for number patterns
        return findAnyPhoneNumberInBundle(extras);
    }

    private String findAnyPhoneNumberInBundle(Bundle bundle) {
        if (bundle == null) return null;
        
        for (String key : bundle.keySet()) {
            Object val = bundle.get(key);
            if (val instanceof CharSequence) {
                String str = val.toString();
                // Regex for a potential phone number (simple: [+][digits][spaces/dashes])
                // We look for something that is mostly digits and at least 8 chars long
                String cleaned = str.replaceAll("[\\s\\-\\(\\)]", "");
                if (cleaned.matches("^\\+?[0-9]{8,15}$")) {
                    return cleaned;
                }
            } else if (val instanceof Bundle) {
                String inner = findAnyPhoneNumberInBundle((Bundle) val);
                if (inner != null) return inner;
            } else if (val instanceof Parcelable[]) {
                for (Parcelable p : (Parcelable[]) val) {
                    if (p instanceof Bundle) {
                        String inner = findAnyPhoneNumberInBundle((Bundle) p);
                        if (inner != null) return inner;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not needed for this implementation
    }
}
