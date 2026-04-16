package com.example.safeinbox.models;

public class SmsModel {

    private long id;
    private String sender;
    private String body;
    private long date;
    private boolean isSpam;

    public SmsModel() {
    }

    public SmsModel(String sender, String body, long date) {
        this.sender = sender;
        this.body = body;
        this.date = date;
        this.isSpam = false;
    }

    public SmsModel(long id, String sender, String body, long date, boolean isSpam) {
        this.id = id;
        this.sender = sender;
        this.body = body;
        this.date = date;
        this.isSpam = isSpam;
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
}
