package com.onsemi.cim.apps.exensio.exensioDearchiver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import com.onsemi.cim.apps.exensio.exensioDearchiver.security.JwtAuthenticationFilter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.context.annotation.Profile;
import java.util.Optional;

import com.onsemi.cim.apps.exensio.exensioDearchiver.service.AppUserDetailsService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("!'${security.sso.enabled:false}'.equalsIgnoreCase('true') and !'${security.ldap.enabled:false}'.equalsIgnoreCase('true')")
public class SecurityConfig {
    private final com.onsemi.cim.apps.exensio.exensioDearchiver.security.JwtUtil jwtUtil;

    public SecurityConfig(com.onsemi.cim.apps.exensio.exensioDearchiver.security.JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable() // tests don't provide CSRF token
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/h2-console/**").permitAll()
                .requestMatchers("/api/auth/register").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/internal/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Add JWT token filter to process Bearer tokens
        http.addFilterBefore(new JwtAuthenticationFilter(jwtUtil), BasicAuthenticationFilter.class);

        // Allow H2 console frames
        http.headers().frameOptions().disable();

        return http.build();
    }

    @Bean
    @Profile("!prod")
    public UserDetailsService inMemoryUsers() {
        // Keep an in-memory admin for dev convenience; main auth uses AppUserDetailsService
        UserDetails admin = User.withUsername("admin")
            .password(passwordEncoder().encode("admin123"))
            .roles("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public DaoAuthenticationProvider daoAuthProvider(AppUserDetailsService userDetailsService) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService, Optional<InMemoryUserDetailsManager> maybeInMemory) {
        // Primary provider: DB-backed service
        DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider();
        daoProvider.setUserDetailsService(userDetailsService);
        daoProvider.setPasswordEncoder(passwordEncoder());

        // In-memory provider for dev fallback (only if bean exists)
        final DaoAuthenticationProvider memoryProvider = maybeInMemory
            .map(uds -> {
                DaoAuthenticationProvider p = new DaoAuthenticationProvider();
                p.setUserDetailsService(uds);
                p.setPasswordEncoder(passwordEncoder());
                return p;
            })
            .orElse(null);

        return authentication -> {
            try {
                return daoProvider.authenticate(authentication);
            } catch (Exception e) {
                if (memoryProvider != null) {
                    return memoryProvider.authenticate(authentication);
                }
                throw e;
            }
        };
    }
}
