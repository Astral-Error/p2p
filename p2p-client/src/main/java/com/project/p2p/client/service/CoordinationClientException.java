package com.project.p2p.client.service;

public class CoordinationClientException extends RuntimeException {

    private final int statusCode;

    public CoordinationClientException(int statusCode, String message) {
        super(String.format("Coordination service error [%d]: %s", statusCode, message));
        this.statusCode = statusCode;
    }

    public CoordinationClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
