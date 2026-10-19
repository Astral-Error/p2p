package com.project.p2p.repository;

import com.project.p2p.model.User;
import com.project.p2p.model.UserKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserKeyRepository extends JpaRepository<UserKey, Long> {

    Optional<UserKey> findByUser(User user);

    Optional<UserKey> findByUser_Username(String username);
}
