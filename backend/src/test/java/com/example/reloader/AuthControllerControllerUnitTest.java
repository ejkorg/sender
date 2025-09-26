package com.example.reloader;

import com.example.reloader.security.JwtUtil;
import com.example.reloader.service.RefreshTokenService;
import com.example.reloader.web.AuthController;
import com.example.reloader.web.dto.AuthRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class AuthControllerControllerUnitTest {

    @Test
    void login_whenAuthenticationFails_throwsBadCredentials() {
        AuthenticationManager am = Mockito.mock(AuthenticationManager.class);
        when(am.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenThrow(new BadCredentialsException("Bad"));

        JwtUtil jwt = Mockito.mock(JwtUtil.class);
        RefreshTokenService rts = Mockito.mock(RefreshTokenService.class);

        AuthController ctrl = new AuthController(am, jwt, rts);

        AuthRequest req = new AuthRequest();
        req.setUsername("no_such_user");
        req.setPassword("whatever");

        assertThrows(BadCredentialsException.class, () -> ctrl.login(req, null));
    }
}
