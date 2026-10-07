package com.sitegenius.whatsappbe.config;

import com.sitegenius.whatsappbe.entity.User;
import com.sitegenius.whatsappbe.enums.Role;
import com.sitegenius.whatsappbe.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SuperAdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // SiteGenius platform owner credentials
    private static final String SUPER_ADMIN_NAME =
            "SiteGenius Owner";

    private static final String SUPER_ADMIN_EMAIL =
            "superadmin@sitegenius.com";

    private static final String SUPER_ADMIN_PASSWORD =
            "superadmin123";

    public SuperAdminInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            System.out.println(
                    "SUPER_ADMIN already exists."
            );
            return;
        }

        User superAdmin = new User();

        superAdmin.setName(SUPER_ADMIN_NAME);
        superAdmin.setEmail(SUPER_ADMIN_EMAIL);

        superAdmin.setPassword(
                passwordEncoder.encode(
                        SUPER_ADMIN_PASSWORD
                )
        );

        superAdmin.setRole(Role.SUPER_ADMIN);

        // SUPER_ADMIN is platform-level.
        superAdmin.setOrganization(null);

        userRepository.save(superAdmin);

        System.out.println(
                "=========================================="
        );
        System.out.println(
                "SUPER_ADMIN created successfully."
        );
        System.out.println(
                "Email: " + SUPER_ADMIN_EMAIL
        );
        System.out.println(
                "=========================================="
        );
    }
}