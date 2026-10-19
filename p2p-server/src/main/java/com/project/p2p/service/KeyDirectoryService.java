package com.project.p2p.service;

import com.project.p2p.dto.KeyRegistrationRequest;
import com.project.p2p.dto.KeyResponse;
import com.project.p2p.exception.InvalidKeyException;
import com.project.p2p.exception.KeyReplacementRequiredException;
import com.project.p2p.exception.ResourceNotFoundException;
import com.project.p2p.model.User;
import com.project.p2p.model.UserKey;
import com.project.p2p.repository.UserKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Optional;

@Service
public class KeyDirectoryService {

    private final UserKeyRepository userKeyRepository;

    public KeyDirectoryService(UserKeyRepository userKeyRepository) {
        this.userKeyRepository = userKeyRepository;
    }

    @Transactional
    public KeyResponse registerOrUpdateKeys(User user, KeyRegistrationRequest request) {
        validatePublicKey("Identity (Ed25519)", request.getIdentityPublicKey());
        validatePublicKey("Key Agreement (X25519)", request.getKeyAgreementPublicKey());

        Optional<UserKey> existing = userKeyRepository.findByUser(user);

        UserKey keyRecord;
        if (existing.isPresent()) {
            if (!request.isReplaceConfirmed()) {
                throw new KeyReplacementRequiredException(
                        "Public identity keys are already registered for user '" + user.getUsername() +
                        "'. Replacing public keys will break existing peer trust and requires explicit confirmation."
                );
            }
            keyRecord = existing.get();
            keyRecord.setIdentityPublicKey(request.getIdentityPublicKey());
            keyRecord.setKeyAgreementPublicKey(request.getKeyAgreementPublicKey());
        } else {
            keyRecord = new UserKey(user, request.getIdentityPublicKey(), request.getKeyAgreementPublicKey());
        }

        keyRecord = userKeyRepository.save(keyRecord);
        return new KeyResponse(
                user.getUsername(),
                keyRecord.getIdentityPublicKey(),
                keyRecord.getKeyAgreementPublicKey(),
                keyRecord.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public KeyResponse getKeysByUsername(String username) {
        String normalized = username.trim().toLowerCase();
        UserKey userKey = userKeyRepository.findByUser_Username(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("No public keys found for peer: " + normalized));

        return new KeyResponse(
                userKey.getUser().getUsername(),
                userKey.getIdentityPublicKey(),
                userKey.getKeyAgreementPublicKey(),
                userKey.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public KeyResponse getMyKeys(User user) {
        UserKey userKey = userKeyRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("No public keys registered for user: " + user.getUsername()));

        return new KeyResponse(
                user.getUsername(),
                userKey.getIdentityPublicKey(),
                userKey.getKeyAgreementPublicKey(),
                userKey.getUpdatedAt()
        );
    }

    private void validatePublicKey(String keyDescription, String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new InvalidKeyException(keyDescription + " key must not be blank");
        }
        try {
            byte[] rawBytes = Base64.getDecoder().decode(base64Key.trim());
            // Ed25519 and X25519 public keys are 32 bytes raw (or 44 bytes in DER/X.509 SubjectPublicKeyInfo)
            if (rawBytes.length != 32 && rawBytes.length != 44) {
                throw new InvalidKeyException(
                        keyDescription + " key length is invalid (" + rawBytes.length +
                        " bytes decoded). Expected 32 bytes raw or 44 bytes X.509 SubjectPublicKeyInfo."
                );
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidKeyException(keyDescription + " key is not valid Base64: " + e.getMessage());
        }
    }
}
