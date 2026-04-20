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
    private String dedupId;
    
    // New fields for advanced classification
    private String classificationStatus; // SAFE, SUSPICIOUS, SPAM
    private boolean hasRiskyLink;
    private boolean isBlocked;

    public SmsMessage() {
        this.classificationStatus = "SAFE";
    }

    public SmsMessage(String sender, String body, long date) {
        this.sender = sender;
        this.body = body;
        this.date = date;
        this.classificationStatus = "SAFE";
    }

    public SmsMessage(long id, String sender, String senderName, String body, long date, String classificationStatus) {
        this(id, sender, senderName, body, date, classificationStatus, null);
    }

    public SmsMessage(long id, String sender, String senderName, String body, long date, String classificationStatus, String dedupId) {
        this.id = id;
        this.sender = sender;
        this.senderName = senderName;
        this.body = body;
        this.date = date;
        this.classificationStatus = classificationStatus != null ? classificationStatus : "SAFE";
        this.dedupId = dedupId;
    }

    public String getClassificationStatus() {
        return classificationStatus;
    }

    public void setClassificationStatus(String classificationStatus) {
        this.classificationStatus = classificationStatus;
    }

    public boolean isHasRiskyLink() {
        return hasRiskyLink;
    }

    public void setHasRiskyLink(boolean hasRiskyLink) {
        this.hasRiskyLink = hasRiskyLink;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
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

