package com.project.p2p.repository;

import com.project.p2p.model.PeerSession;
import com.project.p2p.model.SessionStatus;
import com.project.p2p.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PeerSessionRepository extends JpaRepository<PeerSession, Long> {

    Optional<PeerSession> findBySessionId(String sessionId);

    @Query("SELECT s FROM PeerSession s JOIN FETCH s.user WHERE s.status = :status AND s.lastHeartbeat >= :cutoff")
    List<PeerSession> findActiveSessionsWithUser(@Param("status") SessionStatus status, @Param("cutoff") Instant cutoff);

    List<PeerSession> findByUser(User user);

    List<PeerSession> findByUserAndStatus(User user, SessionStatus status);

    @Modifying
    @Query("UPDATE PeerSession s SET s.status = 'EXPIRED' WHERE s.status = 'ONLINE' AND s.lastHeartbeat < :cutoff")
    int expireStaleSessions(@Param("cutoff") Instant cutoff);
}
