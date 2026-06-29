package com.freightauction.auth.repository;

import com.freightauction.auth.domain.User;
import com.freightauction.auth.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByRoleOrderByCreatedAtDesc(UserRole role);

    boolean existsByEmail(String email);
}
