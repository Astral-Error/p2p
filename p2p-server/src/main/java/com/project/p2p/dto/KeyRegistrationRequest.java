package com.project.p2p.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class KeyRegistrationRequest {

    @NotBlank(message = "Identity public key (Ed25519) must not be blank")
    @Size(min = 32, max = 512, message = "Identity public key must be valid Base64 encoding")
    private String identityPublicKey;

    @NotBlank(message = "Key agreement public key (X25519) must not be blank")
    @Size(min = 32, max = 512, message = "Key agreement public key must be valid Base64 encoding")
    private String keyAgreementPublicKey;

    /** Explicit confirmation required to overwrite an existing identity key */
    private boolean replaceConfirmed = false;

    public KeyRegistrationRequest() {}

    public KeyRegistrationRequest(String identityPublicKey, String keyAgreementPublicKey, boolean replaceConfirmed) {
        this.identityPublicKey = identityPublicKey;
        this.keyAgreementPublicKey = keyAgreementPublicKey;
        this.replaceConfirmed = replaceConfirmed;
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

    public boolean isReplaceConfirmed() {
        return replaceConfirmed;
    }

    public void setReplaceConfirmed(boolean replaceConfirmed) {
        this.replaceConfirmed = replaceConfirmed;
    }
}
