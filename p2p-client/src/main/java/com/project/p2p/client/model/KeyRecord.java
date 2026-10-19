package com.project.p2p.client.model;

import java.time.Instant;

public record KeyRecord(String username, String identityPublicKey, String keyAgreementPublicKey, Instant updatedAt) {}
