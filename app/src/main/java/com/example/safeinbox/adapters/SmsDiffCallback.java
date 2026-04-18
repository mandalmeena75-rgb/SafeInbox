package com.example.safeinbox.adapters;

import androidx.recyclerview.widget.DiffUtil;
import com.example.safeinbox.models.SmsMessage;
import java.util.List;

public class SmsDiffCallback extends DiffUtil.Callback {

    private final List<SmsMessage> oldList;
    private final List<SmsMessage> newList;

    public SmsDiffCallback(List<SmsMessage> oldList, List<SmsMessage> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        // NUCLEAR DEDUPLICATION: We treat messages with the same sender and body 
        // as the "same item" for the UI list, even if they have different database IDs.
        SmsMessage oldMsg = oldList.get(oldItemPosition);
        SmsMessage newMsg = newList.get(newItemPosition);
        
        String oldSender = oldMsg.getSender() != null ? oldMsg.getSender().trim() : "";
        String newSender = newMsg.getSender() != null ? newMsg.getSender().trim() : "";
        String oldBody = oldMsg.getBody() != null ? oldMsg.getBody().trim().toLowerCase() : "";
        String newBody = newMsg.getBody() != null ? newMsg.getBody().trim().toLowerCase() : "";

        return oldSender.equals(newSender) && oldBody.equals(newBody);
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        SmsMessage oldMsg = oldList.get(oldItemPosition);
        SmsMessage newMsg = newList.get(newItemPosition);
        
        return oldMsg.isSpam() == newMsg.isSpam() &&
               (oldMsg.getSenderName() == null ? newMsg.getSenderName() == null : oldMsg.getSenderName().equals(newMsg.getSenderName()));
    }
}
