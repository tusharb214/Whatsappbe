package com.sitegenius.whatsappbe.config;

import com.sitegenius.whatsappbe.entity.User;
import com.sitegenius.whatsappbe.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminPasswordMigration implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminPasswordMigration(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        userRepository.findByEmail("admin@company.com")
                .ifPresent(user -> {

                    if (!passwordEncoder.matches(
                            "admin123",
                            user.getPassword())) {

                        user.setPassword(
                                passwordEncoder.encode("admin123")
                        );

                        userRepository.save(user);

                        System.out.println(
                                "Admin password migrated to BCrypt."
                        );
                    }
                });
    }
}