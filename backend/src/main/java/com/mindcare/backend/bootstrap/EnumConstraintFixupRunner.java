package com.mindcare.backend.bootstrap;

import com.mindcare.backend.model.AppointmentStatus;
import com.mindcare.backend.model.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Hibernate's ddl-auto=update generates a Postgres CHECK constraint for each
 * @Enumerated(STRING) column when the table is first created, but never
 * widens that constraint when the Java enum later gains new values — every
 * such addition (a new Role, a new AppointmentStatus) would otherwise start
 * rejecting inserts with a cryptic "violates check constraint" error. This
 * runs on every boot and rewrites the known constraints to match the enum
 * as it stands right now, on whichever database the app is pointed at.
 */
@Component
public class EnumConstraintFixupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumConstraintFixupRunner.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        resync("users", "role", "users_role_check", Role.values());
        resync("appointments", "status", "appointments_status_check", AppointmentStatus.values());
    }

    private void resync(String table, String column, String constraintName, Enum<?>[] values) {
        String allowed = Arrays.stream(values)
                .map(v -> "'" + v.name() + "'")
                .collect(Collectors.joining(", "));

        entityManager.createNativeQuery(
                "ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName
        ).executeUpdate();

        entityManager.createNativeQuery(
                "ALTER TABLE " + table + " ADD CONSTRAINT " + constraintName +
                        " CHECK (" + column + " IN (" + allowed + "))"
        ).executeUpdate();

        log.info("Synced {} on {}.{} to {}", constraintName, table, column, allowed);
    }
}
