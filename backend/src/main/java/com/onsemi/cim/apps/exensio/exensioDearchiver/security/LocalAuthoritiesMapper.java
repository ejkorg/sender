package com.onsemi.cim.apps.exensio.exensioDearchiver.security;

import com.onsemi.cim.apps.exensio.exensioDearchiver.service.RefDbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Maps an authenticated username to local application authorities from REFDB.
 * This is used after SSO/OIDC authentication to enforce app-specific roles.
 */
public class LocalAuthoritiesMapper {
    private static final Logger log = LoggerFactory.getLogger(LocalAuthoritiesMapper.class);
    private final RefDbService refDbService;

    public LocalAuthoritiesMapper(RefDbService refDbService) {
        this.refDbService = refDbService;
    }

    public Collection<? extends GrantedAuthority> loadAuthorities(String username) {
        try {
            // For now, use a simple heuristic: REFDB may have a notion of admin users or roles.
            // Wire to actual REFDB calls when available.
            Set<GrantedAuthority> roles = new HashSet<>();
            roles.add(new SimpleGrantedAuthority("ROLE_USER"));
            // If REFDB indicates admin, add ROLE_ADMIN. Placeholder: usernames ending with ".admin" for demo.
            if (username != null && username.toLowerCase().contains("admin")) {
                roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            return roles;
        } catch (Exception e) {
            log.warn("Failed loading local authorities for {}: {}", username, e.getMessage());
            return Set.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
