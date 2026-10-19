package com.project.p2p.controller;

import com.project.p2p.dto.KeyRegistrationRequest;
import com.project.p2p.dto.KeyResponse;
import com.project.p2p.model.User;
import com.project.p2p.security.UserPrincipal;
import com.project.p2p.service.KeyDirectoryService;
import com.project.p2p.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/keys")
public class KeyDirectoryController {

    private final KeyDirectoryService keyDirectoryService;
    private final UserService userService;

    public KeyDirectoryController(KeyDirectoryService keyDirectoryService, UserService userService) {
        this.keyDirectoryService = keyDirectoryService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<KeyResponse> registerKeys(
            @Valid @RequestBody KeyRegistrationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.getUserByUsername(principal.getName());
        KeyResponse response = keyDirectoryService.registerOrUpdateKeys(user, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{username}")
    public ResponseEntity<KeyResponse> getKeysByUsername(@PathVariable String username) {
        KeyResponse response = keyDirectoryService.getKeysByUsername(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<KeyResponse> getMyKeys(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.getUserByUsername(principal.getName());
        KeyResponse response = keyDirectoryService.getMyKeys(user);
        return ResponseEntity.ok(response);
    }
}
