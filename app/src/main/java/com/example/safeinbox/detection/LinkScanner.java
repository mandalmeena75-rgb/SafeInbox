package com.example.safeinbox.detection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Advanced LinkScanner aligned with SafeInbox Production Specs.
 */
public class LinkScanner {

    private static final Set<String> SHORTENER_DOMAINS = new HashSet<>(Arrays.asList(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "bit.do", "ow.ly", "is.gd", "buff.ly", "rb.gy"
    ));

    private static final Set<String> TRUSTED_DOMAINS = new HashSet<>(Arrays.asList(
            "youtube.com", "google.com", "amazon.in", "amazon.com", "paytm.com", 
            "apple.com", "microsoft.com", "facebook.com", "instagram.com",
            "uidai.gov.in", "irctc.co.in"
    ));

    // G) Suspicious Domain Zones (RISK)
    private static final Set<String> SUSPICIOUS_TLDS = new HashSet<>(Arrays.asList(
            "xyz", "top", "click", "live", "site", "pw", "monster", "online", "club", "buzz", "cam", "loan", "win", "info"
    ));
    
    private static final Set<String> SUSPICIOUS_PATHS = new HashSet<>(Arrays.asList(
            "free-money", "win-now", "claim-prize", "secure-login", "verify-account", "update-now"
    ));

    public static class LinkRiskResult {
        public boolean hasLink;
        public int score;
        public String reason;
        public boolean isShortened;
        public boolean isBrandMismatch;
        public boolean isTrustedDomain;
        public boolean isSuspiciousTLD;
        public boolean isHomograph;
        
        public LinkRiskResult() {
            this.hasLink = false;
            this.score = 0;
            this.reason = "Secure (No links detected)";
            this.isShortened = false;
            this.isBrandMismatch = false;
            this.isTrustedDomain = false;
            this.isSuspiciousTLD = false;
            this.isHomograph = false;
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

            // 1. Check brand mismatch first (Phishing Pattern -> LEVEL 1 HARD OVERRIDE)
            if (isBrandMismatch(body, domain)) {
                result.isBrandMismatch = true;
                result.score += 60; // Increased penalty for brand spoofing
                result.reason = "⚔️ BRAND SPOOFING detected";
            }

            // 2. Homograph Attack Detection (Non-ASCII characters in domain)
            if (checkHomograph(domain)) {
                result.isHomograph = true;
                result.score += 50; 
                result.reason = (result.reason == null) ? "🚩 HOMOGRAPH ATTACK: Visual similarity detected" : result.reason + " | Homograph";
            }

            // 3. Check if Shortened
            if (primaryLink.isShortened || SHORTENER_DOMAINS.contains(domain)) {
                result.isShortened = true;
                result.score += 25; 
                result.reason = (result.reason == null) ? "⚠️ Masked URL shortener" : result.reason + " | Shortened";
            }

            // 4. Check if Trusted Domain
            if (TRUSTED_DOMAINS.contains(domain)) {
                result.isTrustedDomain = true;
                result.score -= 50; // Boosted trust
            }

            // 5. Suspicious Domain TLD Analysis
            String tld = getTLD(domain);
            if (SUSPICIOUS_TLDS.contains(tld)) {
                result.isSuspiciousTLD = true;
                result.score += 30; 
            }

            for (String sp : SUSPICIOUS_PATHS) {
                if (url.contains(sp)) {
                    result.score += 40; 
                    break;
                }
            }

            if (result.isSuspiciousTLD && result.reason == null) {
                result.reason = "🚨 Risky TLD zone (." + tld + ")";
            }
        }

        return result;
    }

    private boolean checkHomograph(String domain) {
        if (domain == null) return false;
        // Simple check: if domain contains non-ASCII characters, it's highly suspicious for phishing
        for (char c : domain.toCharArray()) {
            if (c > 127) return true;
        }
        return false;
    }

    private String getTLD(String domain) {
        int lastDot = domain.lastIndexOf('.');
        if (lastDot != -1 && lastDot < domain.length() - 1) {
            return domain.substring(lastDot + 1);
        }
        return "";
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
            {"irctc", "irctc.co.in"}
        };

        for (String[] mapping : brandMappings) {
            if (lowerBody.contains(mapping[0]) && !domain.contains(mapping[1])) {
                return true; 
            }
        }
        return false;
    }
}
