package com.project.p2p.service;

import com.project.p2p.dto.AuthRequest;
import com.project.p2p.dto.AuthResponse;
import com.project.p2p.exception.UserAlreadyExistsException;
import com.project.p2p.model.User;
import com.project.p2p.model.UserStatus;
import com.project.p2p.repository.UserRepository;
import com.project.p2p.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        String normalizedUsername = request.getUsername().trim().toLowerCase();

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new UserAlreadyExistsException("Username '" + normalizedUsername + "' is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        User user = new User(normalizedUsername, passwordHash);
        user = userRepository.save(user);

        String token = tokenProvider.generateToken(user.getUsername(), user.getId());
        return new AuthResponse(token, user.getUsername(), user.getId(), tokenProvider.getExpirationMs());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        String normalizedUsername = request.getUsername().trim().toLowerCase();

        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Account is suspended");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = tokenProvider.generateToken(user.getUsername(), user.getId());
        return new AuthResponse(token, user.getUsername(), user.getId(), tokenProvider.getExpirationMs());
    }

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username.trim().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("User not found: " + username));
    }
}
