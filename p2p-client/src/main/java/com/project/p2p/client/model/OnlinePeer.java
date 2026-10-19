package com.project.p2p.client.model;

import java.time.Instant;

public record OnlinePeer(
        String username,
        String sessionId,
        String observedIp,
        int listeningPort,
        Instant lastHeartbeat,
        String identityPublicKey,
        String keyAgreementPublicKey
) {}
