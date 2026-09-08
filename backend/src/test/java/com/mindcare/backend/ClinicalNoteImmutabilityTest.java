package com.mindcare.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRAFT -> SIGNED -> immutable. Corrections go through amend, which creates a
 * new draft rather than ever touching the signed original — the most
 * access-sensitive, integrity-sensitive resource in the system.
 */
class ClinicalNoteImmutabilityTest extends BaseIntegrationTest {

    @Test
    void signedNoteRejectsFurtherEdits_amendCreatesANewChainedDraftInstead() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser therapist = provisionStaff(maintenanceUser, "Notes Test Therapist", "THERAPIST");
        AuthedUser client = registerClient("Notes Test Client");

        var created = post("/api/clinical-notes", therapist.token(), Map.of(
                "clientId", client.id().toString(),
                "content", Map.of("sessionType", "Initial", "presentingConcerns", "Anxiety")
        ));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        String noteId = (String) created.getBody().get("id");
        assertThat(created.getBody().get("status")).isEqualTo("DRAFT");

        var signed = patch("/api/clinical-notes/" + noteId + "/sign", therapist.token(), Map.of());
        assertThat(signed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(signed.getBody().get("status")).isEqualTo("SIGNED");

        // Direct edit attempt after signing is rejected outright.
        var editAttempt = rest.exchange("/api/clinical-notes/" + noteId, org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(
                        Map.of("sessionType", "Tampered", "presentingConcerns", "Should not apply"),
                        authHeaders(therapist.token())),
                Map.class);
        assertThat(editAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Amend creates a brand new draft referencing the original...
        var amended = post("/api/clinical-notes/" + noteId + "/amend", therapist.token(), Map.of());
        assertThat(amended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(amended.getBody().get("status")).isEqualTo("DRAFT");
        assertThat(amended.getBody().get("amendsNoteId")).isEqualTo(noteId);

        // ...and the original, signed note is untouched — still SIGNED, still says "Anxiety".
        var originalAfterAmend = get("/api/clinical-notes/" + noteId, therapist.token()).getBody();
        assertThat(originalAfterAmend.get("status")).isEqualTo("SIGNED");
        assertThat(originalAfterAmend.get("presentingConcerns")).isEqualTo("Anxiety");
    }

    @Test
    void therapistCannotEditAnotherTherapistsNote() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser owner = provisionStaff(maintenanceUser, "Note Owner Therapist", "THERAPIST");
        AuthedUser intruder = provisionStaff(maintenanceUser, "Intruder Therapist", "THERAPIST");
        AuthedUser client = registerClient("Cross Therapist Client");

        var created = post("/api/clinical-notes", owner.token(), Map.of(
                "clientId", client.id().toString(),
                "content", Map.of("sessionType", "Initial")
        ));
        String noteId = (String) created.getBody().get("id");

        var res = rest.exchange("/api/clinical-notes/" + noteId, org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("sessionType", "Hijacked"), authHeaders(intruder.token())),
                Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void clinicalSupervisorCannotCreateNotes_readOnlyByDesign() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser supervisor = provisionStaff(maintenanceUser, "Supervisor", "CLINICAL_SUPERVISOR");
        AuthedUser client = registerClient("Supervisor Test Client");

        var res = post("/api/clinical-notes", supervisor.token(), Map.of(
                "clientId", client.id().toString(),
                "content", Map.of("sessionType", "Should not be allowed")
        ));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
