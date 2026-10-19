package com.project.p2p.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.p2p.dto.AuthRequest;
import com.project.p2p.dto.AuthResponse;
import com.project.p2p.dto.KeyRegistrationRequest;
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

import java.util.Base64;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class KeyDirectoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserKeyRepository userKeyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String validEd25519Key;
    private String validX25519Key;

    @BeforeEach
    void setUp() {
        userKeyRepository.deleteAll();
        userRepository.deleteAll();

        // 32-byte mock raw public keys in Base64
        byte[] raw32a = new byte[32];
        raw32a[0] = 0x01;
        raw32a[31] = 0x20;
        validEd25519Key = Base64.getEncoder().encodeToString(raw32a);

        byte[] raw32b = new byte[32];
        raw32b[0] = 0x02;
        raw32b[31] = 0x40;
        validX25519Key = Base64.getEncoder().encodeToString(raw32b);
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
    void testUnauthorizedKeyAccessRejected() throws Exception {
        mockMvc.perform(get("/api/keys/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRegisterValidKeys() throws Exception {
        String token = registerAndGetToken("alice", "pass12345");

        KeyRegistrationRequest req = new KeyRegistrationRequest(validEd25519Key, validX25519Key, false);

        mockMvc.perform(post("/api/keys")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("alice")))
                .andExpect(jsonPath("$.identityPublicKey", is(validEd25519Key)))
                .andExpect(jsonPath("$.keyAgreementPublicKey", is(validX25519Key)));
    }

    @Test
    void testInvalidKeyEncodingRejected() throws Exception {
        String token = registerAndGetToken("alice", "pass12345");

        // Key with invalid length (24 bytes instead of 32 or 44; 32 chars in Base64)
        String invalidKey = Base64.getEncoder().encodeToString(new byte[24]);
        KeyRegistrationRequest req = new KeyRegistrationRequest(invalidKey, validX25519Key, false);

        mockMvc.perform(post("/api/keys")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("length is invalid")));
    }

    @Test
    void testKeyReplacementRequiresExplicitConfirmation() throws Exception {
        String token = registerAndGetToken("alice", "pass12345");

        KeyRegistrationRequest req1 = new KeyRegistrationRequest(validEd25519Key, validX25519Key, false);
        mockMvc.perform(post("/api/keys")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk());

        // Attempt replace without replaceConfirmed=true
        byte[] newBytes = new byte[32];
        newBytes[0] = 0x77;
        String newKey = Base64.getEncoder().encodeToString(newBytes);
        KeyRegistrationRequest req2 = new KeyRegistrationRequest(newKey, validX25519Key, false);

        mockMvc.perform(post("/api/keys")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("requires explicit confirmation")));

        // Attempt replace with replaceConfirmed=true
        KeyRegistrationRequest req3 = new KeyRegistrationRequest(newKey, validX25519Key, true);
        mockMvc.perform(post("/api/keys")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityPublicKey", is(newKey)));
    }

    @Test
    void testRetrievePeerKeys() throws Exception {
        String aliceToken = registerAndGetToken("alice", "pass12345");
        String bobToken = registerAndGetToken("bob", "pass12345");

        // Alice registers keys
        KeyRegistrationRequest req = new KeyRegistrationRequest(validEd25519Key, validX25519Key, false);
        mockMvc.perform(post("/api/keys")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Bob queries Alice's keys
        mockMvc.perform(get("/api/keys/alice")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("alice")))
                .andExpect(jsonPath("$.identityPublicKey", is(validEd25519Key)));

        // Bob queries non-existent user
        mockMvc.perform(get("/api/keys/charlie")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
    }
}
