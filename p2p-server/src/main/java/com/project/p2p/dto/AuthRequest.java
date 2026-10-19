package com.project.p2p.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthRequest {

    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 32, message = "Username must be between 3 and 32 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username can only contain alphanumeric characters, underscores, dots, and hyphens")
    private String username;

    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, max = 64, message = "Password must be between 6 and 64 characters")
    private String password;

    public AuthRequest() {}

    public AuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
