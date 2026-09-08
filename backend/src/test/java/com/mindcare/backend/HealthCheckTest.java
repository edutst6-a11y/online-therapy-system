package com.mindcare.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is what a hosting platform should poll, not /api/hello — it only
 * reports UP if the database is actually reachable.
 */
class HealthCheckTest extends BaseIntegrationTest {

    @Test
    void healthEndpointIsPublicAndReportsDatabaseConnectivity() {
        ResponseEntity<Map> res = rest.getForEntity("/api/health", Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status")).isEqualTo("UP");
    }
}
