package com.mindcare.backend.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetMeetLinkRequest(
        @NotBlank(message = "Paste the meeting link")
        @Pattern(regexp = "^https://meet\\.google\\.com/.+", message = "That doesn't look like a Google Meet link")
        String meetLink
) {
}
