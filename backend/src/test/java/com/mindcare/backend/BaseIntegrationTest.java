package com.mindcare.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the full app (real Postgres, real Spring Security filter chain) against
 * the same local database used for manual dev testing (mvn spring-boot:run) —
 * there's no Docker available in this environment for Testcontainers, and
 * ddl-auto=update is non-destructive, so this doesn't disturb existing data.
 * Every test user gets a random-suffixed email so runs never collide with each
 * other or with anything created manually.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    @Autowired
    protected TestRestTemplate rest;

    @Value("${mindcare.seed.maintenance-email}")
    protected String maintenanceEmail;

    @Value("${mindcare.seed.maintenance-password}")
    protected String maintenancePassword;

    protected static final String PASSWORD = "Password123!";

    protected record AuthedUser(String token, UUID id, String email) {
    }

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.local";
    }

    @SuppressWarnings("unchecked")
    protected AuthedUser registerClient(String fullName) {
        String email = uniqueEmail("client");
        Map<String, Object> body = Map.of("fullName", fullName, "email", email, "password", PASSWORD);
        ResponseEntity<Map> res = rest.postForEntity("/api/auth/register", body, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return toAuthedUser(res.getBody());
    }

    @SuppressWarnings("unchecked")
    protected AuthedUser login(String email, String password) {
        Map<String, Object> body = Map.of("email", email, "password", password);
        ResponseEntity<Map> res = rest.postForEntity("/api/auth/login", body, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return toAuthedUser(res.getBody());
    }

    protected AuthedUser maintenance() {
        return login(maintenanceEmail, maintenancePassword);
    }

    @SuppressWarnings("unchecked")
    protected AuthedUser provisionStaff(AuthedUser maintenanceUser, String fullName, String role) {
        String email = uniqueEmail(role.toLowerCase());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", fullName);
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("role", role);
        ResponseEntity<Map> res = rest.exchange("/api/staff", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(maintenanceUser.token())), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return login(email, PASSWORD);
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected <T> ResponseEntity<Map> post(String path, String token, T body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)), Map.class);
    }

    protected <T> ResponseEntity<Map> patch(String path, String token, T body) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, authHeaders(token)), Map.class);
    }

    protected ResponseEntity<Map> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
    }

    /** For endpoints that return a JSON array (list responses) rather than a single object. */
    protected ResponseEntity<java.util.List> getList(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), java.util.List.class);
    }

    @SuppressWarnings("unchecked")
    private AuthedUser toAuthedUser(Map<String, Object> body) {
        Map<String, Object> user = (Map<String, Object>) body.get("user");
        return new AuthedUser((String) body.get("token"), UUID.fromString((String) user.get("id")), (String) user.get("email"));
    }
}
