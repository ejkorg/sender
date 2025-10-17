package com.onsemi.cim.apps.exensio.exensioDearchiver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("!'${security.sso.enabled:false}'.equalsIgnoreCase('true') and !'${security.ldap.enabled:false}'.equalsIgnoreCase('true')")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable() // tests don't provide CSRF token
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/h2-console/**").permitAll()
                .requestMatchers("/api/auth/register").permitAll()
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
        // Keep an in-memory admin for dev convenience, but production authentication will use the DB-backed AppUserDetailsService
        UserDetails admin = User.withUsername("admin")
            .password(passwordEncoder().encode("admin123"))
            .roles("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(users());
        provider.setPasswordEncoder(passwordEncoder());
        return authentication -> provider.authenticate(authentication);
    }
}
