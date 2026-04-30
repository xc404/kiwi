package com.cryo.integration.workflow;

import lombok.Data;

import java.util.Date;

/**
 * Kiwi-admin {@code GET /bpm/process-instance/{id}/state} 响应体映射。
 */
@Data
public class KiwiProcessInstanceState {

    private String id;
    /** RUNNING | SUSPENDED | COMPLETED | CANCELED | ACTIVE */
    private String state;
    private Boolean ended;
    private Boolean suspended;
    private Date endTime;
    private String deleteReason;

    public boolean isTerminalEnded() {
        return Boolean.TRUE.equals(ended)
                || "COMPLETED".equalsIgnoreCase(state)
                || "CANCELED".equalsIgnoreCase(state);
    }
}
