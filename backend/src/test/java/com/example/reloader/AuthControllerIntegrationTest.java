package com.example.reloader;

import com.example.reloader.entity.RefreshToken;
import com.example.reloader.repository.RefreshTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.example.reloader.service.RefreshTokenService refreshTokenService;

    @Test
    void loginRefreshLogoutCookieFlow() throws Exception {
        // login as dev admin (enabled in test via env/property)
        String loginJson = "{\"username\":\"admin\",\"password\":\"admin123\"}";

        var loginResult = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
            .andExpect(status().isOk())
            .andReturn();

        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        // parse refresh token value
        String refreshToken = extractCookieValue(setCookie, "refresh_token");
        assertThat(refreshToken).isNotBlank();

        String body = loginResult.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        String accessToken1 = root.get("accessToken").asText();
        assertThat(accessToken1).isNotBlank();

        // call refresh with cookie
    var refreshResult = mvc.perform(post("/api/auth/refresh")
        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
            .andExpect(status().isOk())
            .andReturn();

        String setCookie2 = refreshResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie2).isNotNull();
        String refreshToken2 = extractCookieValue(setCookie2, "refresh_token");
        assertThat(refreshToken2).isNotBlank();
        assertThat(refreshToken2).isNotEqualTo(refreshToken);

        String body2 = refreshResult.getResponse().getContentAsString();
        JsonNode root2 = objectMapper.readTree(body2);
        String accessToken2 = root2.get("accessToken").asText();
    assertThat(accessToken2).isNotBlank();

    // ensure old token is revoked (i.e., not findable via service filter)
    Optional<RefreshToken> old = refreshTokenService.findByToken(refreshToken);
    assertThat(old).isEmpty();

    // rotated token should be stored and findable via service
    Optional<RefreshToken> rotated = refreshTokenService.findByToken(refreshToken2);
    assertThat(rotated).isPresent();

    // logout using rotated token
    var logoutResult = mvc.perform(post("/api/auth/logout")
        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken2)))
            .andExpect(status().isOk())
            .andReturn();

        String setCookie3 = logoutResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie3).contains("Max-Age=0");

    // ensure rotated token revoked (service filters revoked)
    Optional<RefreshToken> after = refreshTokenService.findByToken(refreshToken2);
    assertThat(after).isEmpty();
    }

    private String extractCookieValue(String setCookieHeader, String name) {
        // crude parser: look for name=VALUE; (stop at ;)
        String prefix = name + "=";
        int idx = setCookieHeader.indexOf(prefix);
        if (idx < 0) return null;
        int start = idx + prefix.length();
        int end = setCookieHeader.indexOf(';', start);
        if (end < 0) end = setCookieHeader.length();
        return setCookieHeader.substring(start, end);
    }
}
