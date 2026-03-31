package com.mio.ai.superagent.model.vo;

import lombok.Data;
import org.springframework.ai.chat.messages.Message;

/**
 * @author: Takina
 * @date: 2026/3/30 15:16
 * @description:
 */
@Data
public class MessageVO {
    String role;
    String content;

    public MessageVO(Message message) {
        switch (message.getMessageType()){
            case USER -> {this.role = "user"; break;}
            case ASSISTANT -> {this.role = "assistant"; break;}
            case SYSTEM -> {this.role = "system"; break;}
            case TOOL -> {this.role = "tool"; break;}
        }
        this.content = message.getText();
    }
}
