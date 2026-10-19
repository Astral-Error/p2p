package com.project.p2p.security;

import java.security.Principal;

public class UserPrincipal implements Principal {

    private final Long userId;
    private final String username;

    public UserPrincipal(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public String getName() {
        return username;
    }
}
