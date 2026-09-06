package com.mindcare.backend.bootstrap;

import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Every Maintenance account (after the first) is provisioned by an existing one
 * through {@code POST /api/staff} — there is no public sign-up path to this role.
 * That leaves a bootstrapping problem: something has to create the very first one.
 * This runner does it once, from server-side configuration only, so the seed
 * credentials never pass through a client request.
 */
@Component
public class MaintenanceAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceAccountSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedEmail;
    private final String seedPassword;
    private final String seedName;

    public MaintenanceAccountSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${mindcare.seed.maintenance-email:}") String seedEmail,
            @Value("${mindcare.seed.maintenance-password:}") String seedPassword,
            @Value("${mindcare.seed.maintenance-name:Maintenance Admin}") String seedName
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedEmail = seedEmail;
        this.seedPassword = seedPassword;
        this.seedName = seedName;
    }

    @Override
    public void run(String... args) {
        if (userRepository.countByRole(Role.MAINTENANCE) > 0) {
            return;
        }

        if (seedEmail.isBlank() || seedPassword.isBlank()) {
            log.warn("No MAINTENANCE account exists yet, and no seed credentials were provided. " +
                    "Set SEED_MAINTENANCE_EMAIL and SEED_MAINTENANCE_PASSWORD and restart to create the first one.");
            return;
        }

        User maintenance = new User(
                seedName,
                seedEmail.trim().toLowerCase(),
                passwordEncoder.encode(seedPassword),
                Role.MAINTENANCE,
                null
        );
        userRepository.save(maintenance);
        log.info("Seeded the first MAINTENANCE account for {}", maintenance.getEmail());
    }
}
