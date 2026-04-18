package com.example.safeinbox.models;

/**
 * Model class for a phone contact.
 */
public class ContactModel {
    private String name;
    private String phoneNumber;
    private String photoUri;

    public ContactModel(String name, String phoneNumber, String photoUri) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.photoUri = photoUri;
    }

    public String getName() { return name; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getPhotoUri() { return photoUri; }
}
