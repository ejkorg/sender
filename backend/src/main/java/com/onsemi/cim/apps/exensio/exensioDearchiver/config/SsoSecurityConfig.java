package com.onsemi.cim.apps.exensio.exensioDearchiver.config;

import com.onsemi.cim.apps.exensio.exensioDearchiver.security.LocalAuthoritiesMapper;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.RefDbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;

import java.util.HashSet;
import java.util.Set;

/**
 * Security config enabling SSO via OAuth2/OIDC. Activated when security.sso.enabled=true.
 * It authenticates via the configured provider and maps local roles from REFDB.
 */
@Configuration
@ConditionalOnProperty(prefix = "security.sso", name = "enabled", havingValue = "true")
public class SsoSecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SsoSecurityConfig.class);

    @Bean
    public LocalAuthoritiesMapper localAuthoritiesMapper(RefDbService refDbService) {
        return new LocalAuthoritiesMapper(refDbService);
    }

    @Bean
    public SecurityFilterChain ssoFilterChain(HttpSecurity http, LocalAuthoritiesMapper authoritiesMapper) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/h2-console/**").permitAll()
                .requestMatchers("/internal/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oauth2UserService(authoritiesMapper))
                    .oidcUserService(oidcUserService(authoritiesMapper))
                )
            )
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/"));

        return http.build();
    }

    private OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService(LocalAuthoritiesMapper authoritiesMapper) {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return request -> {
            OAuth2User user = delegate.loadUser(request);
            String username = extractUsername(user);
            Set<String> roles = authoritiesMapper.loadAuthorities(username).stream()
                .map(a -> a.getAuthority())
                .collect(java.util.stream.Collectors.toSet());
            // Merge authorities: keep IdP claims but enforce local roles
            Set<org.springframework.security.core.GrantedAuthority> merged = new HashSet<>(user.getAuthorities());
            roles.forEach(r -> merged.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(r)));
            return new DefaultOAuth2User(merged, user.getAttributes(), "preferred_username");
        };
    }

    private OidcUserService oidcUserService(LocalAuthoritiesMapper authoritiesMapper) {
        OidcUserService delegate = new OidcUserService();
        return new OidcUserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                OidcUser user = delegate.loadUser(userRequest);
                String username = extractUsername(user);
                Set<String> roles = authoritiesMapper.loadAuthorities(username).stream()
                    .map(a -> a.getAuthority())
                    .collect(java.util.stream.Collectors.toSet());
                Set<org.springframework.security.core.GrantedAuthority> merged = new HashSet<>(user.getAuthorities());
                roles.forEach(r -> merged.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(r)));
                return new DefaultOidcUser(merged, user.getIdToken(), user.getUserInfo());
            }
        };
    }

    private String extractUsername(OAuth2User user) {
        // Try common claims from Azure AD/ADFS: preferred_username, upn, email, name
        Object preferred = user.getAttributes().get("preferred_username");
        if (preferred != null) return preferred.toString();
        Object upn = user.getAttributes().get("upn");
        if (upn != null) return upn.toString();
        Object email = user.getAttributes().get("email");
        if (email != null) return email.toString();
        Object name = user.getAttributes().get("name");
        if (name != null) return name.toString();
        return user.getName();
    }
}
