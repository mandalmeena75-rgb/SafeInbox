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
        if (phoneNumber == null) return null;
        String name = null;
        
        // PRIORITY 1: Check System Contacts (only for numeric numbers usually, but works for both)
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
            e.printStackTrace();
        }

        // PRIORITY 2: If not in contacts and it's an Alphanumeric Sender ID, resolve to business name
        if (name == null && PrincipalEntityResolver.isAlphanumericSender(phoneNumber)) {
            name = PrincipalEntityResolver.resolve(phoneNumber);
        }

        // If it's a numeric phone number and NOT in contacts, returning null allows the UI 
        // to show the raw number instead of "Unknown".
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
}
