package com.example.safeinbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver(private val onSmsReceived: (SmsMessage) -> Unit) : BroadcastReceiver() {
    constructor() : this({})

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("SafeInbox", "Broadcast received with action: $action")
        
        if (action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION || 
            action == "android.provider.Telephony.SMS_RECEIVED") {
            
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages != null) {
                for (message in messages) {
                    val sender = message.displayOriginatingAddress ?: "Unknown"
                    val body = message.displayMessageBody ?: ""
                    val timestamp = message.timestampMillis
                    Log.d("SafeInbox", "SMS intercepted! From: $sender Content: $body")
                    onSmsReceived(SmsMessage(sender, body, timestamp))
                }
            } else {
                Log.e("SafeInbox", "SMS messages were null in intent")
            }
        }
    }
}