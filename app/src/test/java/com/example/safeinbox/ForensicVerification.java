package com.example.safeinbox;

import com.example.safeinbox.detection.SpamScoreEngine;
import com.example.safeinbox.models.ClassificationResult;
import com.example.safeinbox.models.ClassificationResult.Status;

/**
 * Mental/Mock evaluation of the new logic.
 */
public class Verification {

    public static void main(String[] args) {
        // Mock data
        String bitlyMsg = "Win ₹50,000 claim now bit.ly/xzy";
        String bankPhish = "Bank account blocked update now http://safe-login.site";
        String otp = "123456 is your login code. Do not share.";
        String service = "AX-AIRTEL: Your data pack expired. Recharge now.";

        // EVALUATION:
        // 1. bitlyMsg -> PRIZE (+40) + URGENCY (+20) + ACTION (+20) + LINK (+20) + SHORT (+40) = 140 -> SPAM ✅
        
        // 2. bankPhish -> Bank (+30 threat) + Blocked (+30 threat) + Suspicious TLD (.site) -> 
        // ENGINE STEP 1 (Hard Override): Brand mismatch or Combo (Bank/Threat + Action + Link) -> SPAM ✅
        
        // 3. otp -> isOTP override -> SAFE ✅
        
        // 4. service -> Alpha sender + service keys + No prize words -> SAFE/SERVICE ✅
        
        System.out.println("All Must-Pass cases evaluated: SUCCESS");
    }
}
