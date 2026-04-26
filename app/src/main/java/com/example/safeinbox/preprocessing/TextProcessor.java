package com.example.safeinbox.preprocessing;

import java.util.ArrayList;
import java.util.List;

public class TextProcessor {

    public String[] process(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) return new String[0];

        // --- ULTRA MAX: Unicode Normalization & Deobfuscation ---
        // 1. Normalize Unicode (Combines characters like 'a' + 'accent' into one)
        String normalized = java.text.Normalizer.normalize(rawBody, java.text.Normalizer.Form.NFKC);
        
        // 2. Invisible Character Purge & Homoglyph Flattening
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            
            // Purge Zero-Width characters and control codes (common spam evasion)
            if (Character.getType(c) == Character.FORMAT || Character.isISOControl(c)) continue;
            
            // Simple Homoglyph Mapping
            if (c == '0') c = 'o';
            if (c == '1' || c == 'l') c = 'i';
            if (c == 'v' && i + 1 < normalized.length() && normalized.charAt(i+1) == 'v') {
                cleaned.append('w'); // vv -> w
                i++;
                continue;
            }

            if (Character.isLetterOrDigit(c)) {
                cleaned.append(Character.toLowerCase(c));
            } else if (Character.isWhitespace(c)) {
                cleaned.append(' ');
            }
        }

        // 3. Spaced Word Reconstruction (Detects "L O A N" or "B-A-N-K")
        String text = cleaned.toString().replaceAll("(?<=\\b\\w)\\s(?=\\w\\b)", "");
        
        // 4. Final Split & Filter
        String[] parts = text.split("\\s+");
        List<String> result = new ArrayList<>();
        for (String word : parts) {
            if (word.length() > 1) { // Ignore single-char noise after reconstruction
                result.add(word);
            }
        }

        return result.toArray(new String[0]);
    }
}
