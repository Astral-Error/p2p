package com.project.p2p.service;

import com.project.p2p.dto.PeerSessionResponse;
import com.project.p2p.exception.ResourceNotFoundException;
import com.project.p2p.exception.UnauthorizedSessionAccessException;
import com.project.p2p.model.PeerSession;
import com.project.p2p.model.SessionStatus;
import com.project.p2p.model.User;
import com.project.p2p.model.UserKey;
import com.project.p2p.repository.PeerSessionRepository;
import com.project.p2p.repository.UserKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class PeerDiscoveryService {

    public static final Duration HEARTBEAT_EXPIRATION_THRESHOLD = Duration.ofSeconds(60);

    private final PeerSessionRepository peerSessionRepository;
    private final UserKeyRepository userKeyRepository;

    public PeerDiscoveryService(PeerSessionRepository peerSessionRepository, UserKeyRepository userKeyRepository) {
        this.peerSessionRepository = peerSessionRepository;
        this.userKeyRepository = userKeyRepository;
    }

    @Transactional
    public PeerSession registerPresence(User user, String observedIp, int listeningPort) {
        if (listeningPort < 1024 || listeningPort > 65535) {
            throw new IllegalArgumentException("Invalid listening port: " + listeningPort + " (must be between 1024 and 65535)");
        }

        String sessionId = UUID.randomUUID().toString();
        PeerSession session = new PeerSession(sessionId, user, observedIp, listeningPort);
        return peerSessionRepository.save(session);
    }

    @Transactional
    public void heartbeat(User user, String sessionId) {
        PeerSession session = peerSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedSessionAccessException("Unauthorized attempt to update session belonging to another user");
        }

        session.setLastHeartbeat(Instant.now());
        session.setStatus(SessionStatus.ONLINE);
        peerSessionRepository.save(session);
    }

    @Transactional
    public void disconnect(User user, String sessionId) {
        PeerSession session = peerSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedSessionAccessException("Unauthorized attempt to disconnect session belonging to another user");
        }

        session.setStatus(SessionStatus.DISCONNECTED);
        peerSessionRepository.save(session);
    }

    @Transactional
    public List<PeerSessionResponse> getOnlinePeers(User currentUser) {
        Instant cutoff = Instant.now().minus(HEARTBEAT_EXPIRATION_THRESHOLD);
        peerSessionRepository.expireStaleSessions(cutoff);

        List<PeerSession> activeSessions = peerSessionRepository.findActiveSessionsWithUser(SessionStatus.ONLINE, cutoff);

        // Pre-fetch public keys for online users
        Map<Long, UserKey> keyMap = new HashMap<>();
        for (PeerSession session : activeSessions) {
            Long uid = session.getUser().getId();
            if (!keyMap.containsKey(uid)) {
                userKeyRepository.findByUser(session.getUser()).ifPresent(k -> keyMap.put(uid, k));
            }
        }

        List<PeerSessionResponse> responses = new ArrayList<>();
        for (PeerSession session : activeSessions) {
            User sessionUser = session.getUser();
            UserKey keys = keyMap.get(sessionUser.getId());

            String identityKey = (keys != null) ? keys.getIdentityPublicKey() : null;
            String keyAgreementKey = (keys != null) ? keys.getKeyAgreementPublicKey() : null;

            responses.add(new PeerSessionResponse(
                    sessionUser.getUsername(),
                    session.getSessionId(),
                    session.getObservedIp(),
                    session.getListeningPort(),
                    session.getLastHeartbeat(),
                    identityKey,
                    keyAgreementKey
            ));
        }

        return responses;
    }
}
