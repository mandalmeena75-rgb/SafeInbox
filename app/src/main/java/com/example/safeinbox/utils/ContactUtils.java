package com.example.safeinbox.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;

import com.example.safeinbox.models.ContactModel;

import java.util.ArrayList;
import java.util.List;

/**
 * ContactUtils provides helper methods to read system contacts.
 */
public class ContactUtils {

    public static List<ContactModel> getAllContacts(Context context) {
        List<ContactModel> contactList = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();
        Cursor cur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

        if (cur != null) {
            int nameIndex = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberIndex = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            int photoIndex = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI);

            while (cur.moveToNext()) {
                String name = cur.getString(nameIndex);
                String number = cur.getString(numberIndex);
                String photoUri = cur.getString(photoIndex);
                contactList.add(new ContactModel(name, number, photoUri));
            }
            cur.close();
        }
        return contactList;
    }

    public static String getContactName(Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) return null;
        String name = null;
        
        // --- LAYER 1: Deep PhoneLookup (System Optimized) ---
        try {
            ContentResolver cr = context.getContentResolver();
            android.net.Uri uri = android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(phoneNumber));
            Cursor cursor = cr.query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
                }
                cursor.close();
            }
        } catch (Exception e) {
            android.util.Log.e("ContactUtils", "Lookup failed for: " + phoneNumber, e);
        }

        if (name != null) return name;

        // --- LAYER 2: Normalized Suffix Matching (Forensic Depth) ---
        // Some numbers are saved as +91 123... while incoming is 0123...
        // We normalize both and compare the last 10 digits (Standard Mobile Length)
        String normalizedInput = normalizeSender(phoneNumber);
        if (normalizedInput.length() >= 10) {
            String inputSuffix = normalizedInput.substring(normalizedInput.length() - 10);
            
            try {
                ContentResolver cr = context.getContentResolver();
                Cursor cur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                        null, null, null);
                
                if (cur != null) {
                    while (cur.moveToNext()) {
                        String contactNumber = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                        String normalizedContact = normalizeSender(contactNumber);
                        if (normalizedContact.length() >= 10) {
                            String contactSuffix = normalizedContact.substring(normalizedContact.length() - 10);
                            if (inputSuffix.equals(contactSuffix)) {
                                name = cur.getString(cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                                break;
                            }
                        }
                    }
                    cur.close();
                }
            } catch (Exception e) {
                android.util.Log.e("ContactUtils", "Iterative match failed", e);
            }
        }

        // --- LAYER 3: Alphanumeric Entity Resolution ---
        if (name == null && PrincipalEntityResolver.isAlphanumericSender(phoneNumber)) {
            name = PrincipalEntityResolver.resolve(phoneNumber);
        }

        return name;
    }

    public static String getPhoneNumberByName(Context context, String name) {
        if (name == null) return null;
        String number = null;
        try {
            ContentResolver cr = context.getContentResolver();
            Cursor cursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ?",
                    new String[]{name}, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return number;
    }

    /**
     * Normalizes a sender address for consistent storage and matching.
     * Uses centralized PrincipalEntityResolver to classify sender types.
     */
    public static String normalizeSender(String sender) {
        if (sender == null || sender.trim().isEmpty()) return "";
        sender = sender.trim();

        if (PrincipalEntityResolver.isAlphanumericSender(sender)) {
            // Alphanumeric sender ID (e.g. AX-IRCTC-S)
            // Keep letters, digits, and hyphens. Uppercase for consistency.
            StringBuilder sb = new StringBuilder(sender.length());
            for (int i = 0; i < sender.length(); i++) {
                char c = sender.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '-') {
                    sb.append(Character.toUpperCase(c));
                }
            }
            return sb.toString();
        } else {
            // Numeric phone number: strip to digits and +
            StringBuilder sb = new StringBuilder(sender.length());
            for (int i = 0; i < sender.length(); i++) {
                char c = sender.charAt(i);
                if ((c >= '0' && c <= '9') || c == '+') {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }
    public static boolean isInternational(Context context, String sender) {
        String normalized = normalizeSender(sender);
        if (normalized.startsWith("+")) {
            // Simple heuristic: if it doesn't start with the local country code (e.g., +91 for India)
            // Ideally, we'd get the local MCC/ISO from TelephonyManager
            String localCode = "+91"; // Defaulting to India as per previous patterns
            return !normalized.startsWith(localCode);
        }
        return false;
    }

    public static String getLocalPart(String number) {
        String normalized = normalizeSender(number);
        if (normalized.startsWith("+91")) return normalized.substring(3);
        if (normalized.startsWith("0")) return normalized.substring(1);
        return normalized;
    }
}
