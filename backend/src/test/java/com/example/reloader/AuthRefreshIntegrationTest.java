package com.example.reloader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRestTemplateConfig.class)
public class AuthRefreshIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    private TestRestTemplate cookieEnabledTestRestTemplate;
    private TestRestTemplate restTemplate() { return cookieEnabledTestRestTemplate; }

    @Test
    void loginRefreshRetryFlow() {
        String base = "http://localhost:" + port + "/api";

        // 1) login
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"admin\",\"password\":\"admin123\"}";
        HttpEntity<String> loginReq = new HttpEntity<>(body, headers);
    ResponseEntity<String> loginResp = restTemplate().postForEntity(base + "/auth/login", loginReq, String.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResp.getBody()).contains("accessToken");

    // cookies are managed by TestRestTemplate automatically

        // 2) call protected with bad token expect 401
        HttpHeaders badH = new HttpHeaders();
        badH.setBearerAuth("badtoken-xyz");
        HttpEntity<Void> badReq = new HttpEntity<>(badH);
    ResponseEntity<String> badResp = restTemplate().exchange(base + "/environments", HttpMethod.GET, badReq, String.class);
        assertThat(badResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 3) call refresh: cookie-enabled RestTemplate will send refresh_token cookie automatically
        ResponseEntity<String> refreshResp = restTemplate().postForEntity(base + "/auth/refresh", null, String.class);
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResp.getBody()).contains("accessToken");

        // 4) call protected with new token
        // extract token from refresh response body
        String refreshBody = refreshResp.getBody();
        String token = refreshBody.replaceAll(".*\"accessToken\"\s*:\s*\"([^\"]+)\".*", "$1");
        HttpHeaders okH = new HttpHeaders();
        okH.setBearerAuth(token);
        HttpEntity<Void> okReq = new HttpEntity<>(okH);
    ResponseEntity<String> okResp = restTemplate().exchange(base + "/environments", HttpMethod.GET, okReq, String.class);
        assertThat(okResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
