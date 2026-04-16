package com.example.safeinbox

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}