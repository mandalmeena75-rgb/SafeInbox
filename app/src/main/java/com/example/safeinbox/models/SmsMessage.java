package com.example.safeinbox.models;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SmsMessage {

    private long id;
    private String sender;
    private String senderName;
    private String body;
    private long date;
    private boolean isSpam;
    private boolean isBlocked;
    private String dedupId;

    public SmsMessage() {
    }

    public SmsMessage(String sender, String body, long date) {
        this.sender = sender;
        this.body = body;
        this.date = date;
        this.isSpam = false;
        this.isBlocked = false;
    }

    public SmsMessage(long id, String sender, String senderName, String body, long date, boolean isSpam, boolean isBlocked) {
        this(id, sender, senderName, body, date, isSpam, isBlocked, null);
    }

    public SmsMessage(long id, String sender, String senderName, String body, long date, boolean isSpam, boolean isBlocked, String dedupId) {
        this.id = id;
        this.sender = sender;
        this.senderName = senderName;
        this.body = body;
        this.date = date;
        this.isSpam = isSpam;
        this.isBlocked = isBlocked;
        this.dedupId = dedupId;
    }

    public String getDedupId() {
        return dedupId;
    }

    public void setDedupId(String dedupId) {
        this.dedupId = dedupId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public boolean isSpam() {
        return isSpam;
    }

    public void setSpam(boolean spam) {
        isSpam = spam;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }

    // Helper for UI formatting
    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
        return sdf.format(new Date(date));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SmsMessage that = (SmsMessage) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}
