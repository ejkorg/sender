package com.onsemi.cim.apps.exensio.exensioDearchiver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import com.onsemi.cim.apps.exensio.exensioDearchiver.security.JwtAuthenticationFilter;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

@Configuration
@EnableWebSecurity
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "security.sso", name = "enabled", havingValue = "false", matchIfMissing = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable() // tests don't provide CSRF token
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/h2-console/**").permitAll()
                .requestMatchers("/internal/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .httpBasic();

        // Add JWT token filter to process Bearer tokens
        http.addFilterBefore(new JwtAuthenticationFilter(new com.onsemi.cim.apps.exensio.exensioDearchiver.security.JwtUtil()), BasicAuthenticationFilter.class);

        // Allow H2 console frames
        http.headers().frameOptions().disable();

        return http.build();
    }

    @Bean
    public UserDetailsService users() {
        UserDetails admin = User.withUsername("admin")
            .password("admin123")
            .roles("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new AuthenticationManager() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                String username = authentication.getPrincipal() == null ? null : authentication.getPrincipal().toString();
                String password = authentication.getCredentials() == null ? null : authentication.getCredentials().toString();
                // Simple dev/test credential check. Replace with real user details in production.
                if ("admin".equals(username) && "admin123".equals(password)) {
                    return new UsernamePasswordAuthenticationToken(username, password, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                }
                throw new BadCredentialsException("Bad credentials");
            }
        };
    }
}
