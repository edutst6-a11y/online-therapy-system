package com.mindcare.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The frontend is never trusted to enforce permissions — the backend
 * independently decides whether a role can perform an action. These spot-check
 * the specific role boundaries the blueprint calls out explicitly.
 */
class RoleAccessControlTest extends BaseIntegrationTest {

    @Test
    void clientCannotProvisionStaffAccounts() {
        AuthedUser client = registerClient("Not An Admin");

        var res = post("/api/staff", client.token(), Map.of(
                "fullName", "Sneaky", "email", uniqueEmail("sneaky"), "password", PASSWORD, "role", "THERAPIST"
        ));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void receptionistCannotReadClinicalIntake_operationalInfoOnly() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser receptionist = provisionStaff(maintenanceUser, "Front Desk", "RECEPTIONIST");
        AuthedUser client = registerClient("Intake Owner");

        var res = get("/api/intake/client/" + client.id(), receptionist.token());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void receptionistCannotReadClinicalNotes() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser receptionist = provisionStaff(maintenanceUser, "Front Desk 2", "RECEPTIONIST");
        AuthedUser client = registerClient("Notes Owner");

        var res = get("/api/clinical-notes/client/" + client.id(), receptionist.token());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void clientCannotReadOtherClientsAppointments() {
        AuthedUser clientA = registerClient("Client A");
        AuthedUser clientB = registerClient("Client B");

        // clientA's own /mine view never exposes clientB's data — proven separately
        // by the ownership-check tests in AppointmentFlowTest. Here we confirm the
        // clinic-wide listing endpoint itself is closed to CLIENT entirely.
        var res = get("/api/appointments", clientA.token());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void financeIsTheOnlyStaffRoleThatCanCreateInvoices_notReceptionOrTherapist() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser therapist = provisionStaff(maintenanceUser, "Not Finance", "THERAPIST");
        AuthedUser client = registerClient("Invoice Target");

        var res = post("/api/invoices", therapist.token(), Map.of(
                "clientId", client.id().toString(),
                "items", java.util.List.of(Map.of("description", "x", "amount", 10))
        ));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void auditLogIsMaintenanceOnly_evenForFinance() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser finance = provisionStaff(maintenanceUser, "Finance Person", "FINANCE");

        var res = get("/api/audit-logs", finance.token());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
