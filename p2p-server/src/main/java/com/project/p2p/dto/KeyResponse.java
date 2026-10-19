package com.project.p2p.dto;

import java.time.Instant;

public class KeyResponse {

    private String username;
    private String identityPublicKey;
    private String keyAgreementPublicKey;
    private Instant updatedAt;

    public KeyResponse() {}

    public KeyResponse(String username, String identityPublicKey, String keyAgreementPublicKey, Instant updatedAt) {
        this.username = username;
        this.identityPublicKey = identityPublicKey;
        this.keyAgreementPublicKey = keyAgreementPublicKey;
        this.updatedAt = updatedAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIdentityPublicKey() {
        return identityPublicKey;
    }

    public void setIdentityPublicKey(String identityPublicKey) {
        this.identityPublicKey = identityPublicKey;
    }

    public String getKeyAgreementPublicKey() {
        return keyAgreementPublicKey;
    }

    public void setKeyAgreementPublicKey(String keyAgreementPublicKey) {
        this.keyAgreementPublicKey = keyAgreementPublicKey;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
