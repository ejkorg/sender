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
public class RegisterControllerIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    public void registerAndVerify_flow() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"ituser\",\"email\":\"ituser@example.com\",\"password\":\"password123\"}";
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/register", new HttpEntity<>(body, headers), Map.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String,Object> m = resp.getBody();
        assertThat(m).containsKey("verificationToken");

        // call verify
        String token = String.valueOf(m.get("verificationToken"));
        String verifyBody = "{\"token\":\"" + token + "\"}";
        ResponseEntity<Map> v = rest.postForEntity("/api/auth/verify", new HttpEntity<>(verifyBody, headers), Map.class);
        assertThat(v.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
