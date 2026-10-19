package com.project.p2p.exception;

public class UnauthorizedSessionAccessException extends RuntimeException {
    public UnauthorizedSessionAccessException(String message) {
        super(message);
    }
}
