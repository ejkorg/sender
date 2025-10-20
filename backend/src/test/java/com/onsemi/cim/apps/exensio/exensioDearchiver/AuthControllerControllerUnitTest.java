package com.onsemi.cim.apps.exensio.exensioDearchiver;

import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.AppUserRepository;
import com.onsemi.cim.apps.exensio.exensioDearchiver.security.JwtUtil;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.AuthTokenService;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.RefreshTokenService;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.AuthController;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.AuthRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class AuthControllerControllerUnitTest {

    @Test
    void login_whenAuthenticationFails_throwsBadCredentials() {
        AuthenticationManager am = Mockito.mock(AuthenticationManager.class);
        when(am.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenThrow(new BadCredentialsException("Bad"));

        JwtUtil jwt = Mockito.mock(JwtUtil.class);
        RefreshTokenService rts = Mockito.mock(RefreshTokenService.class);
        AuthTokenService ats = Mockito.mock(AuthTokenService.class);
        AppUserRepository repo = Mockito.mock(AppUserRepository.class);
        PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);
        com.onsemi.cim.apps.exensio.exensioDearchiver.service.MailService mail = Mockito.mock(com.onsemi.cim.apps.exensio.exensioDearchiver.service.MailService.class);

        AuthController ctrl = new AuthController(am, jwt, rts, ats, repo, encoder, mail, true);

        AuthRequest req = new AuthRequest();
        req.setUsername("no_such_user");
        req.setPassword("whatever");

        var response = ctrl.login(req, new MockHttpServletResponse());
        assertEquals(401, response.getStatusCodeValue());
    }
}
