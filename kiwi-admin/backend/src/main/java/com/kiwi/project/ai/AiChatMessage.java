package com.kiwi.project.ai;

import lombok.Data;

@Data
public class AiChatMessage {
    /** user | assistant | system */
    private String role;
    private String content;
}
