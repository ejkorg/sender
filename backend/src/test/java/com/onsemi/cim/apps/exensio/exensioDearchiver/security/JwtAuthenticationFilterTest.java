package com.onsemi.cim.apps.exensio.exensioDearchiver.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void whenNoAuthorizationHeader_thenSecurityContextUnaffected() throws ServletException, IOException {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        // verify chain continued and no authentication set
        verify(chain).doFilter(req, resp);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "SecurityContext should not have authentication when no Authorization header is present");
    }

    @Test
    void whenValidBearerToken_thenAuthenticationSetAndChainContinues() throws ServletException, IOException {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.validateToken("good-token")).thenReturn(true);
        when(jwtUtil.extractUsername("good-token")).thenReturn("alice");
        when(jwtUtil.extractRoles("good-token")).thenReturn(List.of("ROLE_USER"));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        // verify chain continued
        verify(chain).doFilter(req, resp);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Authentication should be set for a valid token");
        assertEquals("alice", auth.getPrincipal(), "Principal should be the username extracted from the token");
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void whenInvalidBearerToken_thenAuthenticationExceptionThrown() throws ServletException, IOException {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.validateToken("bad-token")).thenReturn(false);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        org.springframework.security.core.AuthenticationException ex = assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> {
                    try {
                        filter.doFilterInternal(req, resp, chain);
                    } catch (IOException | ServletException e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        assertTrue(ex.getMessage().toLowerCase().contains("invalid") || ex.getMessage().toLowerCase().contains("expired"));
        // chain should not have been invoked
        verify(chain, never()).doFilter(any(), any());
    }
}
