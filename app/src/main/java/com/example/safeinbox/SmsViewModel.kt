package com.example.safeinbox

import android.content.Context
import android.provider.Telephony
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsViewModel : ViewModel() {
    private val _messages = mutableStateListOf<SmsMessage>()
    val messages: List<SmsMessage> = _messages

    var searchQuery = mutableStateOf("")

    val filteredMessages: List<SmsMessage>
        get() = if (searchQuery.value.isEmpty()) {
            _messages
        } else {
            _messages.filter { 
                it.body.contains(searchQuery.value, ignoreCase = true) || 
                it.sender.contains(searchQuery.value, ignoreCase = true) 
            }
        }

    fun addMessage(message: SmsMessage) {
        if (!_messages.any { it.sender == message.sender && it.body == message.body && it.timestamp == message.timestamp }) {
            _messages.add(0, message)
        }
    }

    fun loadExistingMessages(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cursor = context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.Inbox.ADDRESS, Telephony.Sms.Inbox.BODY, Telephony.Sms.Inbox.DATE),
                    null,
                    null,
                    Telephony.Sms.Inbox.DEFAULT_SORT_ORDER
                )

                Log.d("SafeInbox", "Cursor count: ${cursor?.count ?: 0}")

                val existingMessages = mutableListOf<SmsMessage>()
                cursor?.use {
                    val addressIndex = it.getColumnIndex(Telephony.Sms.Inbox.ADDRESS)
                    val bodyIndex = it.getColumnIndex(Telephony.Sms.Inbox.BODY)
                    val dateIndex = it.getColumnIndex(Telephony.Sms.Inbox.DATE)

                    while (it.moveToNext()) {
                        val sender = it.getString(addressIndex) ?: "Unknown"
                        val body = it.getString(bodyIndex) ?: ""
                        val date = it.getLong(dateIndex)
                        existingMessages.add(SmsMessage(sender, body, date))
                    }
                }
                
                withContext(Dispatchers.Main) {
                    _messages.clear()
                    _messages.addAll(existingMessages)
                    Log.d("SafeInbox", "Loaded ${_messages.size} messages")
                }
            } catch (e: Exception) {
                Log.e("SafeInbox", "Error loading messages", e)
            }
        }
    }
}