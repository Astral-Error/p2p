package com.project.p2p.dto;

import jakarta.validation.constraints.NotBlank;

public class HeartbeatRequest {

    @NotBlank(message = "Session ID must not be blank")
    private String sessionId;

    public HeartbeatRequest() {}

    public HeartbeatRequest(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
