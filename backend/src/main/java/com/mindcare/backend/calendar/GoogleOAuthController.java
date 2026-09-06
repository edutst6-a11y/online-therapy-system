package com.mindcare.backend.calendar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * One-time manual setup flow to obtain a long-lived refresh token for the
 * clinic's Google account, so the backend can create Calendar/Meet events
 * forever after with no further human login. Visit /api/google/oauth/authorize
 * once, signed into the clinic Google account, and copy the refresh_token
 * from the callback response into the GOOGLE_REFRESH_TOKEN env var.
 */
@RestController
public class GoogleOAuthController {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final HttpClient http = HttpClient.newHttpClient();

    public GoogleOAuthController(
            @Value("${mindcare.google.client-id:}") String clientId,
            @Value("${mindcare.google.client-secret:}") String clientSecret,
            @Value("${mindcare.google.redirect-uri:}") String redirectUri
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @GetMapping("/api/google/oauth/authorize")
    public ResponseEntity<Void> authorize() {
        String url = AUTH_URL
                + "?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&access_type=offline"
                + "&prompt=consent"
                + "&scope=" + enc(SCOPE);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @GetMapping("/api/google/oauth/callback")
    public ResponseEntity<String> callback(@RequestParam String code) throws Exception {
        String body = "code=" + enc(code)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&redirect_uri=" + enc(redirectUri)
                + "&grant_type=authorization_code";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        boolean ok = response.statusCode() == 200;

        String message = ok
                ? "Copy the refresh_token value below into the GOOGLE_REFRESH_TOKEN environment variable:\n\n" + response.body()
                : "Token exchange failed (" + response.statusCode() + "): " + response.body();

        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_GATEWAY)
                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .body(message);
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
