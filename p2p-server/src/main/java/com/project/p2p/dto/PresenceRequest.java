package com.project.p2p.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class PresenceRequest {

    @Min(value = 1024, message = "Listening port must be >= 1024 (non-privileged port)")
    @Max(value = 65535, message = "Listening port must be <= 65535")
    private int listeningPort;

    public PresenceRequest() {}

    public PresenceRequest(int listeningPort) {
        this.listeningPort = listeningPort;
    }

    public int getListeningPort() {
        return listeningPort;
    }

    public void setListeningPort(int listeningPort) {
        this.listeningPort = listeningPort;
    }
}
