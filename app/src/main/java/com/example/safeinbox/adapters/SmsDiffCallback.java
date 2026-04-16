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
        // Since we use a unique constraint on (sender, body, date), these three define identity
        SmsMessage oldMsg = oldList.get(oldItemPosition);
        SmsMessage newMsg = newList.get(newItemPosition);
        
        // If IDs are assigned, use IDs. Otherwise use content identity.
        if (oldMsg.getId() != 0 && newMsg.getId() != 0) {
            return oldMsg.getId() == newMsg.getId();
        }
        
        return oldMsg.getDate() == newMsg.getDate() &&
               oldMsg.getSender().equals(newMsg.getSender()) &&
                oldMsg.getBody().equals(newMsg.getBody());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        SmsMessage oldMsg = oldList.get(oldItemPosition);
        SmsMessage newMsg = newList.get(newItemPosition);
        
        return oldMsg.isSpam() == newMsg.isSpam() &&
               (oldMsg.getSenderName() == null ? newMsg.getSenderName() == null : oldMsg.getSenderName().equals(newMsg.getSenderName()));
    }
}
