package com.project.p2p.dto;

import java.time.Instant;

public class PeerSessionResponse {

    private String username;
    private String sessionId;
    private String observedIp;
    private int listeningPort;
    private Instant lastHeartbeat;
    private String identityPublicKey;
    private String keyAgreementPublicKey;

    public PeerSessionResponse() {}

    public PeerSessionResponse(String username, String sessionId, String observedIp, int listeningPort,
                               Instant lastHeartbeat, String identityPublicKey, String keyAgreementPublicKey) {
        this.username = username;
        this.sessionId = sessionId;
        this.observedIp = observedIp;
        this.listeningPort = listeningPort;
        this.lastHeartbeat = lastHeartbeat;
        this.identityPublicKey = identityPublicKey;
        this.keyAgreementPublicKey = keyAgreementPublicKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getObservedIp() {
        return observedIp;
    }

    public void setObservedIp(String observedIp) {
        this.observedIp = observedIp;
    }

    public int getListeningPort() {
        return listeningPort;
    }

    public void setListeningPort(int listeningPort) {
        this.listeningPort = listeningPort;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
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
}
