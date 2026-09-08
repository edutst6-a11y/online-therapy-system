package com.mindcare.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentFlowTest extends BaseIntegrationTest {

    private String publishSlot(String therapistToken) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant end = start.plus(50, ChronoUnit.MINUTES);
        var res = post("/api/availability", therapistToken, Map.of("startTime", start.toString(), "endTime", end.toString()));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) res.getBody().get("id");
    }

    @Test
    void bookApproveAndCompleteFlow() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser therapist = provisionStaff(maintenanceUser, "Booking Flow Therapist", "THERAPIST");
        AuthedUser client = registerClient("Booking Flow Client");

        String slotId = publishSlot(therapist.token());

        var booked = post("/api/appointments", client.token(), Map.of("availabilitySlotId", slotId, "notes", "integration test"));
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(booked.getBody().get("status")).isEqualTo("PENDING");
        String appointmentId = (String) booked.getBody().get("id");

        var approved = patch("/api/appointments/" + appointmentId + "/approve", maintenanceUser.token(), Map.of());
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().get("status")).isEqualTo("APPROVED");

        var completed = patch("/api/appointments/" + appointmentId + "/complete", therapist.token(), Map.of());
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().get("status")).isEqualTo("COMPLETED");
    }

    @Test
    void aClientCannotCancelAnotherClientsAppointment() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser therapist = provisionStaff(maintenanceUser, "Ownership Therapist", "THERAPIST");
        AuthedUser owner = registerClient("Owner");
        AuthedUser intruder = registerClient("Intruder");

        String slotId = publishSlot(therapist.token());
        var booked = post("/api/appointments", owner.token(), Map.of("availabilitySlotId", slotId, "notes", "mine"));
        String appointmentId = (String) booked.getBody().get("id");

        var res = patch("/api/appointments/" + appointmentId + "/cancel", intruder.token(), Map.of());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void decliningAnAppointmentFreesTheSlotForRebooking() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser therapist = provisionStaff(maintenanceUser, "Free Slot Therapist", "THERAPIST");
        AuthedUser client = registerClient("Free Slot Client");

        String slotId = publishSlot(therapist.token());
        var booked = post("/api/appointments", client.token(), Map.of("availabilitySlotId", slotId, "notes", "will be declined"));
        String appointmentId = (String) booked.getBody().get("id");

        patch("/api/appointments/" + appointmentId + "/decline", maintenanceUser.token(), Map.of());

        var openSlots = getList("/api/availability/therapist/" + therapist.id(), client.token());
        assertThat(openSlots.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = openSlots.getBody();
        assertThat(slots).anySatisfy(slot -> assertThat(slot.get("id")).isEqualTo(slotId));
    }

    @Test
    void cannotBookAnAlreadyBookedSlot() {
        AuthedUser maintenanceUser = maintenance();
        AuthedUser therapist = provisionStaff(maintenanceUser, "Double Book Therapist", "THERAPIST");
        AuthedUser firstClient = registerClient("First Client");
        AuthedUser secondClient = registerClient("Second Client");

        String slotId = publishSlot(therapist.token());
        var first = post("/api/appointments", firstClient.token(), Map.of("availabilitySlotId", slotId, "notes", "first"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var second = post("/api/appointments", secondClient.token(), Map.of("availabilitySlotId", slotId, "notes", "second"));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
