package com.onsemi.cim.apps.exensio.exensioDearchiver.config;

import org.springframework.beans.factory.annotation.Value;
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
import com.onsemi.cim.apps.exensio.exensioDearchiver.security.RestAccessDeniedHandler;
import com.onsemi.cim.apps.exensio.exensioDearchiver.security.RestAuthenticationEntryPoint;
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
    private final boolean relaxedCsp;

    public SecurityConfig(
        com.onsemi.cim.apps.exensio.exensioDearchiver.security.JwtUtil jwtUtil,
        @Value("${security.csp.relaxed:false}") boolean relaxedCsp
    ) {
        this.jwtUtil = jwtUtil;
        this.relaxedCsp = relaxedCsp;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable() // tests don't provide CSRF token
            .httpBasic()
            .and()
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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

        // Use REST handlers for auth failures and access denied so clients receive JSON
        http.exceptionHandling(eh -> eh
            .authenticationEntryPoint(new RestAuthenticationEntryPoint())
            .accessDeniedHandler(new RestAccessDeniedHandler())
        );

        // Allow H2 console frames
        http.headers().frameOptions().disable();

        if (relaxedCsp) {
            final String csp = String.join(" ",
                "default-src 'self';",
                "connect-src 'self' http://localhost:4200 http://127.0.0.1:4200 http://localhost:8080 http://127.0.0.1:8080 http://localhost:61051 ws://localhost:4200 ws://127.0.0.1:4200;",
                "img-src 'self' data:;",
                "script-src 'self' 'unsafe-inline' 'unsafe-eval' http://localhost:4200 http://127.0.0.1:4200 http://localhost:8080 http://127.0.0.1:8080;",
                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com;",
                "font-src 'self' https://fonts.gstatic.com;"
            );
            http.headers(headers -> headers.contentSecurityPolicy(cspConfig -> cspConfig.policyDirectives(csp)));
        }

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
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowCredentials(true);
        // Allow localhost dev and GitHub Codespaces preview origins (patterned)
        // use allowed origin patterns so we can accept subdomains like
        // "https://<random>-4200.app.github.dev" used by Codespaces preview URLs.
        configuration.setAllowedOriginPatterns(java.util.List.of(
            "http://localhost:4200",
            "http://127.0.0.1:4200",
            "http://localhost:8080",
            "http://127.0.0.1:8080",
            "https://*.app.github.dev"
        ));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
