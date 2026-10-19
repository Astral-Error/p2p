package com.project.p2p.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_keys")
public class UserKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    /** Base64-encoded Ed25519 public key (32 bytes raw or X.509 SubjectPublicKeyInfo) */
    @Column(nullable = false, length = 512)
    private String identityPublicKey;

    /** Base64-encoded X25519 public key for Diffie-Hellman key agreement */
    @Column(nullable = false, length = 512)
    private String keyAgreementPublicKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public UserKey() {}

    public UserKey(User user, String identityPublicKey, String keyAgreementPublicKey) {
        this.user = user;
        this.identityPublicKey = identityPublicKey;
        this.keyAgreementPublicKey = keyAgreementPublicKey;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
