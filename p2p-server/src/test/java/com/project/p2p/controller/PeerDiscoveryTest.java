package com.project.p2p.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.p2p.dto.*;
import com.project.p2p.model.PeerSession;
import com.project.p2p.model.SessionStatus;
import com.project.p2p.repository.PeerSessionRepository;
import com.project.p2p.repository.UserKeyRepository;
import com.project.p2p.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PeerDiscoveryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserKeyRepository userKeyRepository;

    @Autowired
    private PeerSessionRepository peerSessionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @BeforeEach
    void setUp() {
        peerSessionRepository.deleteAll();
        userKeyRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetToken(String username, String password) throws Exception {
        AuthRequest req = new AuthRequest(username, password);
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(res.getResponse().getContentAsString(), AuthResponse.class);
        return auth.getToken();
    }

    @Test
    void testPresenceRegistrationAndObservedIp() throws Exception {
        String token = registerAndGetToken("alice", "pass12345");

        PresenceRequest presenceReq = new PresenceRequest(9001);

        MvcResult result = mockMvc.perform(post("/api/peers/presence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(presenceReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId", notNullValue()))
                .andExpect(jsonPath("$.listeningPort", is(9001)))
                .andExpect(jsonPath("$.status", is("ONLINE")))
                .andReturn();

        Map<String, Object> map = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
        assertNotNull(map.get("observedIp"));
    }

    @Test
    void testMultipleSessionsPerUser() throws Exception {
        String token = registerAndGetToken("alice", "pass12345");

        // Session 1 on port 9001
        MvcResult res1 = mockMvc.perform(post("/api/peers/presence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PresenceRequest(9001))))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> session1 = objectMapper.readValue(res1.getResponse().getContentAsString(), new TypeReference<>() {});

        // Session 2 on port 9002
        MvcResult res2 = mockMvc.perform(post("/api/peers/presence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PresenceRequest(9002))))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> session2 = objectMapper.readValue(res2.getResponse().getContentAsString(), new TypeReference<>() {});

        assertNotEquals(session1.get("sessionId"), session2.get("sessionId"));

        // Both sessions should appear in online peers
        MvcResult onlineRes = mockMvc.perform(get("/api/peers/online")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        List<PeerSessionResponse> peers = objectMapper.readValue(onlineRes.getResponse().getContentAsString(), new TypeReference<>() {});
        assertEquals(2, peers.size());

        // Disconnect session 1 only
        mockMvc.perform(post("/api/peers/disconnect")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HeartbeatRequest((String) session1.get("sessionId")))))
                .andExpect(status().isOk());

        // Now only session 2 should be online
        MvcResult onlineRes2 = mockMvc.perform(get("/api/peers/online")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        List<PeerSessionResponse> peersAfter = objectMapper.readValue(onlineRes2.getResponse().getContentAsString(), new TypeReference<>() {});
        assertEquals(1, peersAfter.size());
        assertEquals(session2.get("sessionId"), peersAfter.get(0).getSessionId());
    }

    @Test
    void testCrossUserSessionTamperingBlocked() throws Exception {
        String aliceToken = registerAndGetToken("alice", "pass12345");
        String bobToken = registerAndGetToken("bob", "pass12345");

        MvcResult res = mockMvc.perform(post("/api/peers/presence")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PresenceRequest(9001))))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> session = objectMapper.readValue(res.getResponse().getContentAsString(), new TypeReference<>() {});
        String aliceSessionId = (String) session.get("sessionId");

        // Bob attempts to heartbeat Alice's session
        mockMvc.perform(post("/api/peers/heartbeat")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HeartbeatRequest(aliceSessionId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // Bob attempts to disconnect Alice's session
        mockMvc.perform(post("/api/peers/disconnect")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HeartbeatRequest(aliceSessionId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    void testStaleSessionExpiration() throws Exception {
        String token = registerAndGetToken("alice", "pass12345");

        MvcResult res = mockMvc.perform(post("/api/peers/presence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PresenceRequest(9001))))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> sessionMap = objectMapper.readValue(res.getResponse().getContentAsString(), new TypeReference<>() {});
        String sessionId = (String) sessionMap.get("sessionId");

        // Manually adjust the session heartbeat to 70 seconds ago
        PeerSession session = peerSessionRepository.findBySessionId(sessionId).orElseThrow();
        session.setLastHeartbeat(Instant.now().minusSeconds(70));
        peerSessionRepository.save(session);

        // Query online peers: the stale session must be expired and excluded
        MvcResult onlineRes = mockMvc.perform(get("/api/peers/online")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        List<PeerSessionResponse> peers = objectMapper.readValue(onlineRes.getResponse().getContentAsString(), new TypeReference<>() {});
        assertTrue(peers.isEmpty(), "Stale session should be excluded from online list");

        // Verify status was transitioned to EXPIRED in database
        PeerSession updated = peerSessionRepository.findBySessionId(sessionId).orElseThrow();
        assertEquals(SessionStatus.EXPIRED, updated.getStatus());
    }
}
