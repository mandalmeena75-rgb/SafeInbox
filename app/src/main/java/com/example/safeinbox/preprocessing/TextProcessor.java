package com.example.safeinbox.preprocessing;

import java.util.ArrayList;
import java.util.List;

public class TextProcessor {

    public String[] process(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) {
            return new String[0];
        }

        // Step 1: Convert to lowercase
        String lowered = rawBody.toLowerCase();

        // Step 2: Remove all non-alphanumeric characters (keep spaces for splitting)
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < lowered.length(); i++) {
            char c = lowered.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ') {
                cleaned.append(c);
            }
        }

        // Step 3: Split into word array
        String[] parts = cleaned.toString().trim().split("\\s+");

        // Filter out any empty strings that may result from splitting
        List<String> result = new ArrayList<>();
        for (String word : parts) {
            if (!word.isEmpty()) {
                result.add(word);
            }
        }

        return result.toArray(new String[0]);
    }
}
