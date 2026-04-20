package com.example.safeinbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.safeinbox.database.SpamDao
import com.example.safeinbox.detection.SpamDetector
import com.example.safeinbox.models.ClassificationResult
import com.example.safeinbox.models.SmsMessage
import com.example.safeinbox.utils.ContactUtils
import com.example.safeinbox.utils.TurboExecutor

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action
            Log.d(TAG, "Broadcast received: $action")

            if (action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
                action == "android.provider.Telephony.SMS_RECEIVED") {

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) {
                    Log.w(TAG, "No SMS messages in intent")
                    return
                }

                // Guarantee process stays alive during background work
                val pendingResult = goAsync()
                
                // Process on a background thread to avoid blocking the broadcast
                TurboExecutor.getInstance().execute {
                    try {
                        val detector = SpamDetector.getInstance(context)
                        val dao = SpamDao(context)
                        val processedMessages = mutableListOf<SmsMessage>()

                        for (message in messages) {
                            try {
                                val sender = message.displayOriginatingAddress ?: "Unknown"
                                val body = message.displayMessageBody ?: ""
                                val timestamp = message.timestampMillis

                                if (body.isBlank()) continue

                                val bodyTrim = body.trim()
                                // Advanced Dedup: Check if this message was already processed in the last 60s
                                if (dao.checkIfMessageExistsRecently(sender, bodyTrim)) {
                                    continue
                                }

                                val result = detector.classifyWithDetails(sender, bodyTrim)
                                val contactName = ContactUtils.getContactName(context, sender)

                                val smsRecord = SmsMessage(sender, bodyTrim, timestamp)
                                smsRecord.senderName = contactName
                                smsRecord.classificationStatus = result.status.name
                                smsRecord.isHasRiskyLink = result.isRiskyLink
                                
                                processedMessages.add(smsRecord)
                                
                                // Phase 3: Increment sender score based on classification
                                val isSpam = (result.status == com.example.safeinbox.models.ClassificationResult.Status.SPAM)
                                dao.incrementSenderScore(sender, isSpam)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing individual SMS", e)
                            }
                        }
                        
                        // NUCLEAR SPEED: Batch insert all messages in one transaction
                        if (processedMessages.isNotEmpty()) {
                            dao.insertMessagesBatch(processedMessages)
                            Log.d(TAG, "Batch stored ${processedMessages.size} messages")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in SMS processing thread", e)
                    } finally {
                        // Tell OS we are completely done and it can release WakeLock
                        pendingResult?.finish()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onReceive error", e)
        }
    }
}