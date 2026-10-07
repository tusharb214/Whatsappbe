package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.User;
import com.sitegenius.whatsappbe.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);
}