package com.onsemi.cim.apps.exensio.exensioDearchiver.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ForgotPasswordIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    public void requestResetAndUseToken_flow() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // create a user
        String register = "{\"username\":\"fpuser\",\"email\":\"fpuser@example.com\",\"password\":\"password123\"}";
        ResponseEntity<Map> r = rest.postForEntity("/api/auth/register", new HttpEntity<>(register, headers), Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();

        // verify to enable the user so login works (register returns verification token)
        Map body = r.getBody();
        assertThat(body).containsKey("verificationToken");
        String vt = String.valueOf(body.get("verificationToken"));
        String verifyBody = "{\"token\":\"" + vt + "\"}";
        ResponseEntity<Map> v = rest.postForEntity("/api/auth/verify", new HttpEntity<>(verifyBody, headers), Map.class);
        assertThat(v.getStatusCode().is2xxSuccessful()).isTrue();

        // request password reset
        String requestReset = "{\"username\":\"fpuser\"}";
        ResponseEntity<Map> rr = rest.postForEntity("/api/auth/request-reset", new HttpEntity<>(requestReset, headers), Map.class);
        assertThat(rr.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rr.getBody()).containsKey("resetToken");
        String resetToken = String.valueOf(rr.getBody().get("resetToken"));

        // perform reset with new password
        String resetBody = "{\"token\":\"" + resetToken + "\",\"password\":\"newPass123\"}";
        ResponseEntity<Map> rp = rest.postForEntity("/api/auth/reset-password", new HttpEntity<>(resetBody, headers), Map.class);
        assertThat(rp.getStatusCode().is2xxSuccessful()).isTrue();

        // confirm we can login with new password
        String loginBody = "{\"username\":\"fpuser\",\"password\":\"newPass123\"}";
        ResponseEntity<Map> loginResp = rest.postForEntity("/api/auth/login", new HttpEntity<>(loginBody, headers), Map.class);
        assertThat(loginResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(loginResp.getBody()).containsKey("accessToken");
    }
}
