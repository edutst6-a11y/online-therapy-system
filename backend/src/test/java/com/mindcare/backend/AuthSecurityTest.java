package com.mindcare.backend;

import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hard requirement this whole app was built around: role always comes
 * from the database, never from anything the client claims. These prove it
 * end to end through the real HTTP stack, not just by reading the source.
 */
class AuthSecurityTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    /**
     * There's no self-service way to become a super admin — by design, only an
     * existing one can grant it (see StaffController). For this test we bypass
     * the API and flip the flag directly, the same way SuperAdminSeeder does it
     * for the one real account this app ships with, rather than depending on
     * that specific account (or its real password) existing in every test run.
     */
    private AuthedUser provisionSuperAdmin() {
        AuthedUser maintenanceUser = provisionStaff(maintenance(), "Test Super Admin", "MAINTENANCE");
        User user = userRepository.findById(maintenanceUser.id())
                .orElseThrow(() -> new NoSuchElementException("provisioned user vanished"));
        user.setSuperAdmin(true);
        userRepository.save(user);
        return maintenanceUser;
    }

    @Test
    void registrationAlwaysCreatesAClient_evenIfARoleFieldIsSmuggledIn() {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Sneaky Registrant");
        body.put("email", uniqueEmail("sneaky"));
        body.put("password", PASSWORD);
        body.put("role", "MAINTENANCE");

        var res = rest.postForEntity("/api/auth/register", body, Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) res.getBody().get("user");
        assertThat(user.get("role")).isEqualTo("CLIENT");
    }

    @Test
    void wrongPasswordIsRejected() {
        AuthedUser client = registerClient("Wrong Password Test");
        var res = rest.postForEntity("/api/auth/login", Map.of("email", client.email(), "password", "totally-wrong"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void accountLocksAfterFiveConsecutiveFailures() {
        AuthedUser client = registerClient("Lockout Test");

        for (int i = 0; i < 5; i++) {
            var res = rest.postForEntity("/api/auth/login", Map.of("email", client.email(), "password", "wrong"), Map.class);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // Even the CORRECT password is now refused — the account is locked, not just that one password.
        var res = rest.postForEntity("/api/auth/login", Map.of("email", client.email(), "password", PASSWORD), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        var res = rest.exchange("/api/appointments/mine", HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * The definitive proof: the SAME JWT, never reissued, gains and loses access
     * purely because a database row changed underneath it. If role were ever
     * read from the token instead of looked up fresh, this would fail.
     */
    @Test
    void sameTokenReflectsRoleChangesMadeInTheDatabase_superAdminActiveRoleSwitch() {
        String token = provisionSuperAdmin().token();

        // Starts able to reach a MAINTENANCE-only endpoint.
        assertThat(getList("/api/staff", token).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Switch this account's active role to CLIENT — same token throughout.
        var switchRes = patch("/api/auth/active-role", token, Map.of("role", "CLIENT"));
        assertThat(switchRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(switchRes.getBody().get("role")).isEqualTo("CLIENT");

        // The exact same token can no longer reach the MAINTENANCE-only endpoint.
        // (403 bodies are JSON objects, not arrays, so this one stays Map-typed.)
        assertThat(get("/api/staff", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // ...but can now reach a CLIENT-only one.
        assertThat(getList("/api/appointments/mine", token).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Switch back to confirm reverting (role:null) restores the original access too.
        HashMap<String, Object> revertBody = new HashMap<>();
        revertBody.put("role", null);
        var revert = patch("/api/auth/active-role", token, revertBody);
        assertThat(revert.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getList("/api/staff", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void nonSuperAdminCannotSwitchRoles() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser therapist = provisionStaff(maintenanceUser, "Not Super Admin", "THERAPIST");

        var res = patch("/api/auth/active-role", therapist.token(), Map.of("role", "MAINTENANCE"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
