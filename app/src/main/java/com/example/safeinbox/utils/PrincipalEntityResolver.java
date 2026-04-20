package com.example.safeinbox.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility to resolve cryptic alphanumeric SMS headers (Principal Entities)
 * into friendly, readable business names. Primarily for Indian SMS formats.
 */
public class PrincipalEntityResolver {

    private static final Map<String, String> ENTITY_MAP = new HashMap<>();

    /**
     * Checks if a sender string is a pure numeric phone number.
     */
    public static boolean isNumeric(String sender) {
        if (sender == null || sender.trim().isEmpty()) return false;
        // Strip common phone symbols for check
        String cleaned = sender.replaceAll("[\\s\\-\\+\\(\\)]", "");
        return cleaned.matches("\\d+");
    }

    /**
     * Checks if a sender string is an alphanumeric sender ID (e.g. AX-IRCTC-S).
     */
    public static boolean isAlphanumericSender(String sender) {
        if (sender == null || sender.trim().isEmpty()) return false;
        // If it contains any letter, it's considered alphanumeric/service ID
        for (char c : sender.toCharArray()) {
            if (Character.isLetter(c)) return true;
        }
        return false;
    }

    static {
        // Banking & Finance
        ENTITY_MAP.put("AXISBK", "Axis Bank");
        ENTITY_MAP.put("ICICIB", "ICICI Bank");
        ENTITY_MAP.put("HDFCBK", "HDFC Bank");
        ENTITY_MAP.put("SBICRD", "SBI Card");
        ENTITY_MAP.put("SBIUPI", "SBI UPI");
        ENTITY_MAP.put("SBISMS", "SBI");
        ENTITY_MAP.put("SBIINB", "SBI Internet Banking");
        ENTITY_MAP.put("KOTAKB", "Kotak Bank");
        ENTITY_MAP.put("PNBSMS", "PNB Bank");
        ENTITY_MAP.put("BOISMS", "Bank of India");
        ENTITY_MAP.put("CANARA", "Canara Bank");
        ENTITY_MAP.put("YESBNK", "Yes Bank");
        ENTITY_MAP.put("INDUSB", "IndusInd Bank");
        ENTITY_MAP.put("BARBKG", "Bank of Baroda");
        ENTITY_MAP.put("RBLBNK", "RBL Bank");
        ENTITY_MAP.put("PAYTM", "Paytm");
        ENTITY_MAP.put("PAYTMB", "Paytm Bank");
        ENTITY_MAP.put("PHONEP", "PhonePe");
        ENTITY_MAP.put("NAVIHQ", "Navi UPI");
        ENTITY_MAP.put("COMPCR", "Competitive Cracker");
        ENTITY_MAP.put("FEDBNK", "Federal Bank");
        ENTITY_MAP.put("IDFCFB", "IDFC First Bank");
        ENTITY_MAP.put("AUSFNB", "AU Small Finance Bank");
        ENTITY_MAP.put("BAJAJF", "Bajaj Finserv");

        // Travel & Services
        ENTITY_MAP.put("IRCTCi", "IRCTC");
        ENTITY_MAP.put("IRCTCR", "IRCTC (Retiring Room)");
        ENTITY_MAP.put("IRCTCS", "IRCTC");
        ENTITY_MAP.put("REDBUS", "redBus");
        ENTITY_MAP.put("INDIGO", "IndiGo");
        ENTITY_MAP.put("AIRIND", "Air India");
        ENTITY_MAP.put("VISTAR", "Vistara");
        ENTITY_MAP.put("MAKEMY", "MakeMyTrip");

        // Telecom & Tech
        ENTITY_MAP.put("JIOHTR", "Jio");
        ENTITY_MAP.put("JIOFBR", "Jio Fiber");
        ENTITY_MAP.put("JIONET", "Jio Fiber");
        ENTITY_MAP.put("Jionet", "Jio Fiber");
        ENTITY_MAP.put("Airtel", "Airtel");
        ENTITY_MAP.put("AIRTLR", "Airtel");
        ENTITY_MAP.put("AIRTL", "Airtel");
        ENTITY_MAP.put("VIcare", "Vi");
        ENTITY_MAP.put("BSNLSM", "BSNL");
        ENTITY_MAP.put("GOOGLE", "Google");
        ENTITY_MAP.put("AMAZON", "Amazon");
        ENTITY_MAP.put("AMZNDL", "Amazon Delivery");
        ENTITY_MAP.put("FLIPKT", "Flipkart");
        ENTITY_MAP.put("ZOMATO", "Zomato");
        ENTITY_MAP.put("SWIGGY", "Swiggy");
        ENTITY_MAP.put("UBERIN", "Uber");
        ENTITY_MAP.put("OLACAB", "Ola");
        ENTITY_MAP.put("DUNZO", "Dunzo");
        ENTITY_MAP.put("MYNTRA", "Myntra");
        ENTITY_MAP.put("MEESHO", "Meesho");
        ENTITY_MAP.put("NYKAA", "Nykaa");

        // E-Gov & Education
        ENTITY_MAP.put("Aadhar", "Aadhaar");
        ENTITY_MAP.put("VOTERI", "Election Commission");
        ENTITY_MAP.put("UIPDAI", "UIDAI");
        ENTITY_MAP.put("DIGILK", "DigiLocker");
        ENTITY_MAP.put("DGLOCR", "DigiLocker");
        ENTITY_MAP.put("COLLDA", "Collegedunia");
        ENTITY_MAP.put("EDUSKL", "EduSkills");
        ENTITY_MAP.put("ARWINF", "Arwinf");
        ENTITY_MAP.put("NAUKRI", "Naukri");
        ENTITY_MAP.put("LNKDIN", "LinkedIn");
        ENTITY_MAP.put("ECISMS", "Election Commission");
        ENTITY_MAP.put("NPCI", "NPCI (UPI)");
    }

    /**
     * Resolves a sender ID to a friendly name.
     * e.g., 'AX-IRCTCi-S' -> 'IRCTC'
     */
    public static String resolve(String senderId) {
        if (senderId == null || senderId.trim().isEmpty() 
                || senderId.equalsIgnoreCase("Unknown Sender") 
                || senderId.equalsIgnoreCase("Unknown")) return null;
        
        // 1. Strip the network prefix (e.g., 'AX-', 'JM-', 'AD-')
        String core = senderId;
        if (core.contains("-")) {
            String[] parts = core.split("-");
            if (parts.length >= 2) {
                // Check if first part is a 2-char network code (e.g. AX, BZ, JX)
                if (parts[0].length() <= 2) {
                    core = parts[1];
                } else {
                    core = parts[0]; // Take first major part
                }
            }
        }

        // 2. Map directly if possible (case insensitive)
        for (String key : ENTITY_MAP.keySet()) {
            if (key.equalsIgnoreCase(core)) {
                return ENTITY_MAP.get(key);
            }
        }

        // 3. Fallback: Title Case the core if it's alphanumeric
        return toTitleCase(core);
    }

    /**
     * HEURISTIC FALLBACK: If the sender address is truly lost, 
     * extract the likely entity name from the message body keywords.
     */
    public static String resolveFromBody(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        String lowerBody = body.toLowerCase();

        // Banking & Finance
        if (lowerBody.contains("sbi") || lowerBody.contains("state bank")) return "SBI Bank";
        if (lowerBody.contains("axis")) return "Axis Bank";
        if (lowerBody.contains("icici")) return "ICICI Bank";
        if (lowerBody.contains("hdfc")) return "HDFC Bank";
        if (lowerBody.contains("kotak")) return "Kotak Bank";
        if (lowerBody.contains("pnb") || lowerBody.contains("punjab national")) return "PNB Bank";
        if (lowerBody.contains("canara")) return "Canara Bank";
        if (lowerBody.contains("paytm")) return "Paytm";
        if (lowerBody.contains("phonepe")) return "PhonePe";
        if (lowerBody.contains("gpay") || lowerBody.contains("google pay")) return "Google Pay";
        
        // Travel
        if (lowerBody.contains("irctc")) return "IRCTC";
        if (lowerBody.contains("redbus")) return "redBus";
        if (lowerBody.contains("makemytrip")) return "MakeMyTrip";
        
        // Telecom
        if (lowerBody.contains("airtel") || lowerBody.contains("callcost") || lowerBody.contains("pack valid")) return "Airtel";
        if (lowerBody.contains("jio")) return "Jio";
        if (lowerBody.contains("bsnl")) return "BSNL";
        if (lowerBody.contains(" vi ") || lowerBody.contains("vodafone") || lowerBody.contains("idea")) return "Vi";
        
        // E-Commerce & Services 
        if (lowerBody.contains("amazon")) return "Amazon";
        if (lowerBody.contains("flipkart")) return "Flipkart";
        if (lowerBody.contains("zomato")) return "Zomato";
        if (lowerBody.contains("swiggy")) return "Swiggy";
        if (lowerBody.contains("uber")) return "Uber";
        if (lowerBody.contains("ola")) return "Ola";
        
        // Government & Education
        if (lowerBody.contains("digilocker")) return "DigiLocker";
        if (lowerBody.contains("aadhaar") || lowerBody.contains("uidai")) return "UIDAI (Aadhaar)";
        if (lowerBody.contains("eduskills")) return "EduSkills";
        if (lowerBody.contains("collegedunia")) return "Collegedunia";
        if (lowerBody.contains("naukri")) return "Naukri";
        if (lowerBody.contains("linkedin")) return "LinkedIn";

        // Generic OTP detection (last resort)
        if (lowerBody.contains("otp") && lowerBody.contains("verify")) return "Verification Service";
        
        return null; // Truly unknown
    }

    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        
        // Remove trailing -S, -R, -N etc if remaining
        if (input.length() > 2 && input.endsWith("-S")) input = input.substring(0, input.length() - 2);
        
        StringBuilder res = new StringBuilder();
        boolean nextTitleCase = true;

        for (char c : input.toCharArray()) {
            if (Character.isSpaceChar(c) || c == '-' || c == '_') {
                nextTitleCase = true;
                res.append(' ');
            } else if (nextTitleCase) {
                res.append(Character.toTitleCase(c));
                nextTitleCase = false;
            } else {
                res.append(Character.toLowerCase(c));
            }
        }

        return res.toString().trim();
    }
}
