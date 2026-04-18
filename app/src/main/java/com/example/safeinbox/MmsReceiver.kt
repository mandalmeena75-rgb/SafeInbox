package com.example.safeinbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.safeinbox.database.SpamDao
import com.example.safeinbox.detection.SpamDetector
import com.example.safeinbox.models.SmsMessage
import com.example.safeinbox.utils.ContactUtils
import com.example.safeinbox.utils.TurboExecutor

class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.WAP_PUSH_DELIVER_ACTION") {
            Log.d("MmsReceiver", "MMS received!")
            
            // Allow a small delay for the system to process and save the MMS to provider
            TurboExecutor.getInstance().execute {
                try {
                    Thread.sleep(2000) 
                    fetchAndProcessLatestMms(context)
                } catch (e: Exception) {
                    Log.e("MmsReceiver", "Error in background fetch", e)
                }
            }
        }
    }

    private fun fetchAndProcessLatestMms(context: Context) {
        val uri = Uri.parse("content://mms/inbox")
        val cursor = context.contentResolver.query(uri, null, null, null, "date DESC LIMIT 1")

        cursor?.use {
            if (it.moveToFirst()) {
                try {
                    val mmsId = it.getLong(it.getColumnIndexOrThrow("_id"))
                    val date = it.getLong(it.getColumnIndexOrThrow("date")) * 1000

                    val body = getMmsText(context, mmsId)
                    val sender = getMmsAddress(context, mmsId)

                    if (!body.isNullOrEmpty()) {
                        val bodyTrim = body.trim()
                        val detector = SpamDetector.getInstance(context)
                        val dao = SpamDao(context)
                        
                        val finalSender = sender ?: "Unknown"
                        // Advanced Dedup: Check if this message was already processed in the last 60s
                        if (dao.checkIfMessageExistsRecently(finalSender, bodyTrim)) {
                            Log.d("MmsReceiver", "Duplicate MMS detected, skipping")
                            return
                        }

                        val isBlocked = dao.isBlockedNumber(finalSender)
                        val isSpam = isBlocked || detector.isSpam(finalSender, bodyTrim)
                        val contactName = ContactUtils.getContactName(context, finalSender)

                        val mmsRecord = SmsMessage(finalSender, bodyTrim, date)
                        mmsRecord.senderName = contactName
                        mmsRecord.isSpam = isSpam
                        mmsRecord.isBlocked = isBlocked
                        dao.insertMessage(mmsRecord)
                        
                        // Phase 3: Increment sender score based on classification
                        dao.incrementSenderScore(finalSender, isSpam)

                        Log.d("MmsReceiver", "MMS stored from $finalSender. Spam: $isSpam | Blocked: $isBlocked")
                    }
                } catch (e: Exception) {
                    Log.e("MmsReceiver", "Error processing MMS", e)
                }
            }
        }
    }

    private fun getMmsText(context: Context, mmsId: Long): String? {
        val uri = Uri.parse("content://mms/part")
        val cursor = context.contentResolver.query(uri, null, "mid=$mmsId", null, null)
        val sb = StringBuilder()

        cursor?.use {
            while (it.moveToNext()) {
                try {
                    val ct = it.getString(it.getColumnIndexOrThrow("ct"))
                    if (ct == "text/plain") {
                        val data = it.getString(it.getColumnIndexOrThrow("_data"))
                        val text = it.getString(it.getColumnIndexOrThrow("text"))
                        
                        if (text != null) {
                            sb.append(text)
                        } else if (data != null) {
                            // Fallback: Read from stream if 'text' column is null
                            sb.append(readMmsPartStream(context, it))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MmsReceiver", "Error reading MMS part", e)
                }
            }
        }
        return sb.toString()
    }

    private fun readMmsPartStream(context: Context, cursor: android.database.Cursor): String {
        val partId = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
        val partUri = Uri.parse("content://mms/part/$partId")
        val sb = StringBuilder()
        try {
            context.contentResolver.openInputStream(partUri)?.use { isr ->
                java.io.BufferedReader(java.io.InputStreamReader(isr, "UTF-8")).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        sb.append(line)
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MmsReceiver", "Error reading MMS stream", e)
        }
        return sb.toString()
    }

    private fun getMmsAddress(context: Context, mmsId: Long): String? {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        val cursor = context.contentResolver.query(uri, null, "type=137", null, null)
        var address: String? = null

        cursor?.use {
            if (it.moveToFirst()) {
                address = it.getString(it.getColumnIndexOrThrow("address"))
            }
        }
        return address
    }
}
