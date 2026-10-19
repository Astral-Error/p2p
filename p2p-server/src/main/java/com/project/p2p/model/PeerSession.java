package com.project.p2p.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "peer_sessions", indexes = {
    @Index(name = "idx_peer_session_id", columnList = "sessionId", unique = true),
    @Index(name = "idx_peer_status_heartbeat", columnList = "status, lastHeartbeat")
})
public class PeerSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 64)
    private String observedIp;

    @Column(nullable = false)
    private int listeningPort;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastHeartbeat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SessionStatus status;

    public PeerSession() {}

    public PeerSession(String sessionId, User user, String observedIp, int listeningPort) {
        this.sessionId = sessionId;
        this.user = user;
        this.observedIp = observedIp;
        this.listeningPort = listeningPort;
        this.createdAt = Instant.now();
        this.lastHeartbeat = Instant.now();
        this.status = SessionStatus.ONLINE;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastHeartbeat == null) {
            lastHeartbeat = Instant.now();
        }
        if (status == null) {
            status = SessionStatus.ONLINE;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerSession that = (PeerSession) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
}
