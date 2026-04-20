package com.example.safeinbox.detection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Advanced LinkScanner aligned with SafeInbox 4.0 scoring weights.
 * Handles Shortlink detection, Trusted Domain whitelisting, and Suspicious TLD analysis.
 */
public class LinkScanner {

    private static final Set<String> SHORTENER_DOMAINS = new HashSet<>(Arrays.asList(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "bit.do", "ow.ly", "is.gd", "buff.ly", "rb.gy"
    ));

    private static final Set<String> TRUSTED_DOMAINS = new HashSet<>(Arrays.asList(
            "youtube.com", "google.com", "amazon.in", "amazon.com", "paytm.com", 
            "apple.com", "microsoft.com", "facebook.com", "instagram.com"
    ));

    private static final Set<String> SUSPICIOUS_TLDS = new HashSet<>(Arrays.asList(
            "xyz", "top", "pw", "monster", "online", "club", "buzz", "cam", "loan", "win"
    ));

    public static class LinkRiskResult {
        public boolean hasLink;
        public int score;
        public String reason;
        public boolean isShortened;
        
        public LinkRiskResult() {
            this.hasLink = false;
            this.score = 0;
            this.reason = "Secure (No links detected)";
            this.isShortened = false;
        }
    }

    public LinkRiskResult scan(String body) {
        LinkRiskResult result = new LinkRiskResult();
        if (body == null || body.isEmpty()) return result;

        List<com.example.safeinbox.utils.LinkDetector.DetectedLink> links = com.example.safeinbox.utils.LinkDetector.findLinks(body);
        if (!links.isEmpty()) {
            result.hasLink = true;
            
            // Analyze the primary link
            com.example.safeinbox.utils.LinkDetector.DetectedLink primaryLink = links.get(0);
            String url = primaryLink.url;
            String domain = com.example.safeinbox.utils.LinkDetector.getDomain(url).toLowerCase();

            // 1. Base Score for ANY Link (+20)
            result.score += 20;

            // 2. Short Link Penalty (+40)
            if (primaryLink.isShortened || SHORTENER_DOMAINS.contains(domain)) {
                result.score += 20; // 20 baseline + 20 penalty = 40 (User Requirement)
                result.isShortened = true;
                result.reason = "⚠️ High-risk URL shortener detected";
            }

            // 3. Suspicious Domain/TLD Penalty (+30)
            String tld = getTLD(domain);
            if (SUSPICIOUS_TLDS.contains(tld) || isSuspiciousDomainPattern(domain)) {
                result.score += 10; // Adjusting to ensure it lands around requested +30 range
                result.reason = "🚨 Link leads to a suspicious or untrusted domain zone";
            }

            // 4. Trusted Domain Bonus (-20)
            if (TRUSTED_DOMAINS.contains(domain)) {
                result.score -= 20; // Baseline 20 - 20 = 0 (Effectively Safe)
                result.reason = "✅ Verified Trusted Domain (e.g., Google, Amazon)";
            }
            
            // 5. Brand Mismatch Handling (+25)
            // If message mentions a bank or brand but link is different
            if (isBrandMismatch(body, domain)) {
                result.score += 25;
                result.reason = "⚔️ BRAND SPOOFING: Mismatch between mentioned brand and link destination";
            }
        }

        return result;
    }

    private String getTLD(String domain) {
        int lastDot = domain.lastIndexOf('.');
        if (lastDot != -1 && lastDot < domain.length() - 1) {
            return domain.substring(lastDot + 1);
        }
        return "";
    }

    private boolean isSuspiciousDomainPattern(String domain) {
        int digits = 0;
        for (char c : domain.toCharArray()) {
            if (Character.isDigit(c)) digits++;
        }
        return digits > 3 || domain.contains("--") || domain.contains("-free-") || domain.contains("-gift-");
    }

    private boolean isBrandMismatch(String body, String domain) {
        String lowerBody = body.toLowerCase();
        String[][] brandMappings = {
            {"amazon", "amazon."},
            {"google", "google.com"},
            {"paytm", "paytm.com"},
            {"sbi", "onlinesbi.sbi"},
            {"hdfc", "hdfcbank.com"},
            {"flipkart", "flipkart.com"},
            {"aadhaar", "uidai.gov.in"},
            {"irctc", "irctc.com"}
        };

        for (String[] mapping : brandMappings) {
            if (lowerBody.contains(mapping[0]) && !domain.contains(mapping[1])) {
                return true; 
            }
        }
        return false;
    }
}
