package com.mindcare.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * There's no backend Google Calendar/Meet integration — the therapist starts
 * an ordinary Google Meet call under their own account and pastes the
 * resulting link onto the approved appointment so the client can join the
 * same call. These prove that hand-off actually works end to end.
 */
class VideoSessionLinkTest extends BaseIntegrationTest {

    private String bookAndApprove(AuthedUser maintenanceUser, AuthedUser therapist, AuthedUser client) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant end = start.plus(50, ChronoUnit.MINUTES);
        var slot = post("/api/availability", therapist.token(), Map.of("startTime", start.toString(), "endTime", end.toString()));
        String slotId = (String) slot.getBody().get("id");

        var booked = post("/api/appointments", client.token(), Map.of("availabilitySlotId", slotId, "notes", "video test"));
        String appointmentId = (String) booked.getBody().get("id");

        patch("/api/appointments/" + appointmentId + "/approve", maintenanceUser.token(), Map.of());
        return appointmentId;
    }

    @Test
    void therapistPastesTheMeetLinkAndItAppearsForBothSidesOnceApproved() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser therapist = provisionStaff(maintenanceUser, "Video Therapist", "THERAPIST");
        AuthedUser client = registerClient("Video Client");
        String appointmentId = bookAndApprove(maintenanceUser, therapist, client);

        // Before the link is set, neither side has anything to join.
        var beforeMine = getList("/api/appointments/mine", client.token());
        assertThat(beforeMine.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> beforeList = beforeMine.getBody();
        assertThat(beforeList).anySatisfy(a -> {
            if (a.get("id").equals(appointmentId)) {
                assertThat(a.get("meetLink")).isNull();
            }
        });

        var setLink = patch("/api/appointments/" + appointmentId + "/meet-link", therapist.token(),
                Map.of("meetLink", "https://meet.google.com/abc-defg-hij"));
        assertThat(setLink.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setLink.getBody().get("meetLink")).isEqualTo("https://meet.google.com/abc-defg-hij");

        // The client sees the exact same link through their own view of the appointment.
        var afterMine = getList("/api/appointments/mine", client.token());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> afterList = afterMine.getBody();
        assertThat(afterList).anySatisfy(a -> {
            if (a.get("id").equals(appointmentId)) {
                assertThat(a.get("meetLink")).isEqualTo("https://meet.google.com/abc-defg-hij");
            }
        });
    }

    @Test
    void nonGoogleMeetLinksAreRejected() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser therapist = provisionStaff(maintenanceUser, "Strict Link Therapist", "THERAPIST");
        AuthedUser client = registerClient("Strict Link Client");
        String appointmentId = bookAndApprove(maintenanceUser, therapist, client);

        var res = patch("/api/appointments/" + appointmentId + "/meet-link", therapist.token(),
                Map.of("meetLink", "https://zoom.us/j/123456789"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aTherapistCannotSetTheMeetLinkOnAnotherTherapistsAppointment() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser owner = provisionStaff(maintenanceUser, "Owning Therapist", "THERAPIST");
        AuthedUser intruder = provisionStaff(maintenanceUser, "Intruding Therapist", "THERAPIST");
        AuthedUser client = registerClient("Cross Therapist Video Client");
        String appointmentId = bookAndApprove(maintenanceUser, owner, client);

        var res = patch("/api/appointments/" + appointmentId + "/meet-link", intruder.token(),
                Map.of("meetLink", "https://meet.google.com/xyz-uvwx-yz"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void reschedulingClearsTheStaleMeetLinkSoItCannotBeReusedForTheNewTime() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser therapist = provisionStaff(maintenanceUser, "Reschedule Video Therapist", "THERAPIST");
        AuthedUser client = registerClient("Reschedule Video Client");
        String appointmentId = bookAndApprove(maintenanceUser, therapist, client);

        patch("/api/appointments/" + appointmentId + "/meet-link", therapist.token(),
                Map.of("meetLink", "https://meet.google.com/old-link-here"));

        Instant newTime = Instant.now().plus(11, ChronoUnit.DAYS);
        var rescheduled = patch("/api/appointments/" + appointmentId + "/reschedule", maintenanceUser.token(),
                Map.of("newScheduledAt", newTime.toString()));

        assertThat(rescheduled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rescheduled.getBody().get("meetLink")).isNull();
    }
}
