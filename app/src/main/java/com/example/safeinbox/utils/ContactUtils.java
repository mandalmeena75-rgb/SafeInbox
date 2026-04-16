package com.example.safeinbox.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.HashMap;
import java.util.Map;

public class ContactUtils {

    // Memory cache to avoid repeated expensive system calls
    private static final Map<String, String> contactCache = new HashMap<>();

    /**
     * Looks up a contact name from a phone number using the device's address book.
     * Returns the name if found, otherwise returns null.
     */
    public static String getContactName(Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return null;
        }

        // Check cache first (near-instant)
        if (contactCache.containsKey(phoneNumber)) {
            return contactCache.get(phoneNumber);
        }

        // Normalize the number slightly (remove dashes, spaces) for better matching
        // Note: We don't remove '+' as it's part of the international record
        String lookupNumber = phoneNumber.replaceAll("[\\s\\-\\(\\)]", "");

        String contactName = null;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(lookupNumber));
        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    contactName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME));
                    // Save to cache for superfast future lookups
                    contactCache.put(phoneNumber, contactName);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cursor.close();
            }
        }

        return contactName;
    }

    /**
     * Performs a reverse lookup: Finds the first phone number associated with a given contact name.
     */
    public static String getPhoneNumberByName(Context context, String name) {
        if (name == null || name.isEmpty()) return null;

        String phoneNumber = null;
        String lowerName = name.toLowerCase();
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
        
        // Omni-Case Fuzzy Match: Case-insensitive, prefix, and partial matching
        String selection = "LOWER(" + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + ") = ? OR " + 
                          "LOWER(" + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + ") LIKE ? OR " +
                          "? LIKE '%' || LOWER(" + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + ") || '%'";
        String[] selectionArgs = new String[]{lowerName, lowerName + "%", lowerName};

        Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, 
                                                         ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cursor.close();
            }
        }
        return phoneNumber;
    }
}
