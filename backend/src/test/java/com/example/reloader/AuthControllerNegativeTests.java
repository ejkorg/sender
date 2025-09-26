package com.example.reloader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// no mocks here; rely on non-existent username to trigger auth failure
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerNegativeTests {

    @Autowired
    private MockMvc mvc;

    // no AuthenticationManager mock; keep test lightweight and avoid interfering
    // with the SecurityConfig used in the other integration tests

    @Test
    void refreshWithoutCookie_returns401() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
            .andExpect(status().isUnauthorized());
    }
}
