package com.mio.ai.superagent.model.vo;

import lombok.Data;
import org.springframework.ai.chat.messages.Message;

@Data
public class MessageVO {
    String role;
    String content;

    public MessageVO(Message message) {
        switch (message.getMessageType()){
            case USER -> this.role = "user";
            case ASSISTANT -> this.role = "assistant";
            case SYSTEM -> this.role = "system";
            default -> this.role = "other";
        }
        this.content = message.getText();
    }
    
    public boolean isToolMessage() {
        return "other".equals(this.role);
    }
}
