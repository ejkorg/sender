package com.onsemi.cim.apps.exensio.exensioDearchiver.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AdminControllerSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void internalPools_requiresAuth() {
        String url = String.format("http://localhost:%d/internal/pools", port);
        ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void internalPools_allowsAdminBasicAuth() {
        String url = String.format("http://localhost:%d/internal/pools", port);
        TestRestTemplate admin = restTemplate.withBasicAuth("admin", "admin123");
        ResponseEntity<String> resp = admin.getForEntity(url, String.class);
        // With admin creds we should get 200 (body may be JSON map)
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }
}
