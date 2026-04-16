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

class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.WAP_PUSH_DELIVER_ACTION") {
            Log.d("MmsReceiver", "MMS received!")
            
            // Allow a small delay for the system to process and save the MMS to provider
            Thread {
                Thread.sleep(2000) 
                fetchAndProcessLatestMms(context)
            }.start()
        }
    }

    private fun fetchAndProcessLatestMms(context: Context) {
        val uri = Uri.parse("content://mms/inbox")
        val cursor = context.contentResolver.query(uri, null, null, null, "date DESC LIMIT 1")

        if (cursor != null && cursor.moveToFirst()) {
            try {
                val mmsId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow("date")) * 1000

                val body = getMmsText(context, mmsId)
                val sender = getMmsAddress(context, mmsId)

                if (!body.isNullOrEmpty()) {
                    val detector = SpamDetector(context)
                    val dao = SpamDao(context)
                    
                    val isSpam = detector.isSpam(sender ?: "Unknown", body)
                    val contactName = ContactUtils.getContactName(context, sender)

                    val mmsRecord = SmsMessage(sender ?: "Unknown", body, date)
                    mmsRecord.senderName = contactName
                    mmsRecord.isSpam = isSpam
                    dao.insertMessage(mmsRecord)
                    
                    Log.d("MmsReceiver", "MMS stored from $sender. Spam: $isSpam")
                }
            } catch (e: Exception) {
                Log.e("MmsReceiver", "Error processing MMS", e)
            } finally {
                cursor.close()
            }
        }
    }

    private fun getMmsText(context: Context, mmsId: Long): String? {
        val uri = Uri.parse("content://mms/part")
        val cursor = context.contentResolver.query(uri, null, "mid=$mmsId", null, null)
        val sb = StringBuilder()

        cursor?.use {
            while (it.moveToNext()) {
                val ct = it.getString(it.getColumnIndexOrThrow("ct"))
                if (ct == "text/plain") {
                    sb.append(it.getString(it.getColumnIndexOrThrow("text")))
                }
            }
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
