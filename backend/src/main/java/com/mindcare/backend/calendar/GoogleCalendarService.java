package com.mindcare.backend.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Creates real Google Calendar events (with an auto-generated Meet link) on
 * a single clinic Google account, using a long-lived refresh token obtained
 * once via {@link GoogleOAuthController}. The frontend never mentions Google
 * Meet — it just calls this a "session" — but the underlying event and
 * video link are real.
 */
@Service
public class GoogleCalendarService {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/%s/events?conferenceDataVersion=1&sendUpdates=all";

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String calendarId;
    private final String timeZone;
    private final boolean configured;

    public GoogleCalendarService(
            @Value("${mindcare.google.client-id:}") String clientId,
            @Value("${mindcare.google.client-secret:}") String clientSecret,
            @Value("${mindcare.google.refresh-token:}") String refreshToken,
            @Value("${mindcare.google.calendar-id:primary}") String calendarId,
            @Value("${mindcare.google.timezone:UTC}") String timeZone
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.calendarId = calendarId;
        this.timeZone = timeZone;
        this.configured = !clientId.isBlank() && !clientSecret.isBlank() && !refreshToken.isBlank();
    }

    public boolean isConfigured() {
        return configured;
    }

    public record MeetEvent(String eventId, String meetLink) {
    }

    public MeetEvent createSessionEvent(String summary, String clientEmail, String therapistEmail, Instant start, Instant end) {
        if (!configured) {
            throw new IllegalStateException("Video session links aren't configured yet");
        }
        try {
            String accessToken = fetchAccessToken();
            return createEvent(accessToken, summary, clientEmail, therapistEmail, start, end);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not create the session event: " + e.getMessage(), e);
        }
    }

    private String fetchAccessToken() throws Exception {
        String body = "client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&refresh_token=" + enc(refreshToken)
                + "&grant_type=refresh_token";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Token refresh failed: " + response.statusCode() + " " + response.body());
        }
        return mapper.readTree(response.body()).get("access_token").asText();
    }

    private MeetEvent createEvent(
            String accessToken, String summary, String clientEmail, String therapistEmail, Instant start, Instant end
    ) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("summary", summary);

        ObjectNode startNode = root.putObject("start");
        startNode.put("dateTime", start.toString());
        startNode.put("timeZone", timeZone);

        ObjectNode endNode = root.putObject("end");
        endNode.put("dateTime", end.toString());
        endNode.put("timeZone", timeZone);

        var attendees = root.putArray("attendees");
        attendees.addObject().put("email", clientEmail);
        attendees.addObject().put("email", therapistEmail);

        ObjectNode createRequest = root.putObject("conferenceData").putObject("createRequest");
        createRequest.put("requestId", UUID.randomUUID().toString());
        createRequest.putObject("conferenceSolutionKey").put("type", "hangoutsMeet");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(EVENTS_URL, enc(calendarId))))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Event creation failed: " + response.statusCode() + " " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        return new MeetEvent(json.get("id").asText(), extractMeetLink(json));
    }

    private String extractMeetLink(JsonNode event) {
        for (JsonNode entry : event.path("conferenceData").path("entryPoints")) {
            if ("video".equals(entry.path("entryPointType").asText())) {
                return entry.path("uri").asText();
            }
        }
        return event.path("hangoutLink").asText(null);
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
