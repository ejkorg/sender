package com.onsemi.cim.apps.exensio.exensioDearchiver.web;

import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.AppUserRepository;
import com.onsemi.cim.apps.exensio.exensioDearchiver.security.JwtUtil;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.AuthTokenService;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class AuthControllerTokenResponseTest {

    @Test
    void requestReset_whenReturnTokensDisabled_returnsOnlyMessage() {
        AuthenticationManager am = Mockito.mock(AuthenticationManager.class);
        JwtUtil jwt = Mockito.mock(JwtUtil.class);
        RefreshTokenService rts = Mockito.mock(RefreshTokenService.class);
        AuthTokenService ats = Mockito.mock(AuthTokenService.class);
        AppUserRepository repo = Mockito.mock(AppUserRepository.class);
        PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);
        com.onsemi.cim.apps.exensio.exensioDearchiver.service.MailService mail = Mockito.mock(com.onsemi.cim.apps.exensio.exensioDearchiver.service.MailService.class);

        // user exists
        var user = new com.onsemi.cim.apps.exensio.exensioDearchiver.entity.AppUser();
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        when(repo.findByUsername(anyString())).thenReturn(Optional.of(user));

        // token created
        var prt = new com.onsemi.cim.apps.exensio.exensioDearchiver.entity.PasswordResetToken();
        prt.setToken("tok-123");
        when(ats.createPasswordResetToken("testuser")).thenReturn(prt);

        // construct controller with returnTokensInResponse = false
        AuthController ctrl = new AuthController(am, jwt, rts, ats, repo, encoder, mail, false);

        var resp = ctrl.requestReset(Map.of("username", "testuser"));
        assertEquals(200, resp.getStatusCodeValue());
        var body = resp.getBody();
        assertNotNull(body);
        assertEquals("reset requested", body.get("message"));
        assertFalse(body.containsKey("resetToken"));
    }
}
