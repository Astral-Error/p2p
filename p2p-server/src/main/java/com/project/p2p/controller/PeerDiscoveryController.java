package com.project.p2p.controller;

import com.project.p2p.dto.HeartbeatRequest;
import com.project.p2p.dto.PeerSessionResponse;
import com.project.p2p.dto.PresenceRequest;
import com.project.p2p.model.PeerSession;
import com.project.p2p.model.User;
import com.project.p2p.security.UserPrincipal;
import com.project.p2p.service.PeerDiscoveryService;
import com.project.p2p.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/peers")
public class PeerDiscoveryController {

    private final PeerDiscoveryService peerDiscoveryService;
    private final UserService userService;

    public PeerDiscoveryController(PeerDiscoveryService peerDiscoveryService, UserService userService) {
        this.peerDiscoveryService = peerDiscoveryService;
        this.userService = userService;
    }

    @PostMapping("/presence")
    public ResponseEntity<Map<String, Object>> registerPresence(
            @Valid @RequestBody PresenceRequest request,
            HttpServletRequest servletRequest,
            @AuthenticationPrincipal UserPrincipal principal) {

        User user = userService.getUserByUsername(principal.getName());
        String observedIp = extractRemoteIp(servletRequest);

        PeerSession session = peerDiscoveryService.registerPresence(user, observedIp, request.getListeningPort());

        return ResponseEntity.ok(Map.of(
                "sessionId", session.getSessionId(),
                "observedIp", session.getObservedIp(),
                "listeningPort", session.getListeningPort(),
                "status", session.getStatus().name()
        ));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, String>> heartbeat(
            @Valid @RequestBody HeartbeatRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        User user = userService.getUserByUsername(principal.getName());
        peerDiscoveryService.heartbeat(user, request.getSessionId());

        return ResponseEntity.ok(Map.of("status", "OK", "message", "Heartbeat received"));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, String>> disconnect(
            @Valid @RequestBody HeartbeatRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        User user = userService.getUserByUsername(principal.getName());
        peerDiscoveryService.disconnect(user, request.getSessionId());

        return ResponseEntity.ok(Map.of("status", "OK", "message", "Session disconnected"));
    }

    @GetMapping("/online")
    public ResponseEntity<List<PeerSessionResponse>> getOnlinePeers(
            @AuthenticationPrincipal UserPrincipal principal) {

        User user = userService.getUserByUsername(principal.getName());
        List<PeerSessionResponse> peers = peerDiscoveryService.getOnlinePeers(user);

        return ResponseEntity.ok(peers);
    }

    private String extractRemoteIp(HttpServletRequest request) {
        String xForwarded = request.getHeader("X-Forwarded-For");
        if (xForwarded != null && !xForwarded.isBlank()) {
            String clientIp = xForwarded.split(",")[0].trim();
            if (!clientIp.equalsIgnoreCase("unknown")) {
                return normalizeIp(clientIp);
            }
        }
        return normalizeIp(request.getRemoteAddr());
    }

    private String normalizeIp(String ip) {
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
