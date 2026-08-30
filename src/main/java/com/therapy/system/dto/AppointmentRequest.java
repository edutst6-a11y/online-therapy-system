package com.therapy.system.dto;

public class AppointmentRequest {
    private Long clientId;
    private Long therapistId;
    private String appointmentTime; // Accepts "YYYY-MM-DDTHH:mm"
    private String reason;

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public Long getTherapistId() { return therapistId; }
    public void setTherapistId(Long therapistId) { this.therapistId = therapistId; }

    public String getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(String appointmentTime) { this.appointmentTime = appointmentTime; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}