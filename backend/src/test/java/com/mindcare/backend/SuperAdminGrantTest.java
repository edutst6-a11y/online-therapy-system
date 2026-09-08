package com.mindcare.backend;

import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Has the right to invite other members to become super admin too" — but only
 * an existing super admin can extend that invitation, and revoking it must
 * immediately end any role they'd switched themselves into.
 */
class SuperAdminGrantTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private AuthedUser makeSuperAdmin(AuthedUser maintenanceUser) {
        User user = userRepository.findById(maintenanceUser.id())
                .orElseThrow(() -> new NoSuchElementException("provisioned user vanished"));
        user.setSuperAdmin(true);
        userRepository.save(user);
        return maintenanceUser;
    }

    @Test
    void ordinaryMaintenanceCannotGrantSuperAdmin() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser ordinaryMaintenance = provisionStaff(maintenanceUser, "Ordinary Maintenance", "MAINTENANCE");
        AuthedUser target = provisionStaff(maintenanceUser, "Grant Target", "THERAPIST");

        var res = patch("/api/staff/" + target.id() + "/super-admin", ordinaryMaintenance.token(), Map.of("superAdmin", true));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aSuperAdminCanGrantAndRevokeSuperAdminOnAnotherAccount() {
        AuthedUser superAdmin = makeSuperAdmin(provisionStaff(maintenance(), "Granting Super Admin", "MAINTENANCE"));
        AuthedUser target = provisionStaff(maintenance(), "Newly Granted", "THERAPIST");

        var grant = patch("/api/staff/" + target.id() + "/super-admin", superAdmin.token(), Map.of("superAdmin", true));
        assertThat(grant.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(grant.getBody().get("superAdmin")).isEqualTo(true);

        // The newly-granted account can now switch roles itself.
        AuthedUser targetLoggedIn = login(target.email(), PASSWORD);
        var switchRes = patch("/api/auth/active-role", targetLoggedIn.token(), Map.of("role", "CLIENT"));
        assertThat(switchRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Revoking immediately clears whatever role they'd switched into.
        var revoke = patch("/api/staff/" + target.id() + "/super-admin", superAdmin.token(), Map.of("superAdmin", false));
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(revoke.getBody().get("superAdmin")).isEqualTo(false);
        assertThat(revoke.getBody().get("role")).isEqualTo("THERAPIST");

        // ...and they can no longer switch roles at all — same token as before.
        var switchAfterRevoke = patch("/api/auth/active-role", targetLoggedIn.token(), Map.of("role", "MAINTENANCE"));
        assertThat(switchAfterRevoke.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
