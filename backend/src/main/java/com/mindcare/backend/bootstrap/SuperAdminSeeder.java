package com.mindcare.backend.bootstrap;

import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Grants super-admin status — the ability to log in and operate as any role, and
 * to grant super-admin to other accounts — to the one specific account named in
 * the system blueprint. Runs on every boot and is a no-op once that account
 * already has it, so this self-heals through a normal deploy exactly like
 * EnumConstraintFixupRunner does for schema drift: there's no way for this code
 * to directly touch the production database outside of app startup.
 */
@Component
public class SuperAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminSeeder.class);
    private static final String OWNER_EMAIL = "nenzoutadiwananshe@gmail.com";

    private final UserRepository userRepository;

    public SuperAdminSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        userRepository.findByEmailIgnoreCase(OWNER_EMAIL).ifPresent(user -> {
            if (!user.isSuperAdmin()) {
                user.setSuperAdmin(true);
                userRepository.save(user);
                log.info("Granted super-admin status to {}", user.getEmail());
            }
        });
    }
}
