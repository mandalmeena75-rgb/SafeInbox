package com.example.safeinbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.safeinbox.database.SpamDao
import com.example.safeinbox.detection.SpamDetector
import com.example.safeinbox.models.SmsMessage
import com.example.safeinbox.utils.ContactUtils

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("SafeInbox", "Broadcast received with action: $action")
        
        if (action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION || 
            action == "android.provider.Telephony.SMS_RECEIVED") {
            
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages != null) {
                val detector = SpamDetector(context)
                val dao = SpamDao(context)

                for (message in messages) {
                    val sender = message.displayOriginatingAddress ?: "Unknown"
                    val body = message.displayMessageBody ?: ""
                    val timestamp = message.timestampMillis
                    
                    Log.d("SafeInbox", "SMS intercepted! From: $sender")
                    
                    // Classify the message
                    val isSpam = detector.isSpam(sender, body)
                    
                    // Lookup name
                    val contactName = ContactUtils.getContactName(context, sender)

                    // Save to database
                    val smsRecord = SmsMessage(sender, body, timestamp)
                    smsRecord.senderName = contactName
                    smsRecord.isSpam = isSpam
                    dao.insertMessage(smsRecord)
                    
                    Log.d("SafeInbox", "SMS stored. Spam detected: $isSpam")
                }
            } else {
                Log.e("SafeInbox", "SMS messages were null in intent")
            }
        }
    }
}