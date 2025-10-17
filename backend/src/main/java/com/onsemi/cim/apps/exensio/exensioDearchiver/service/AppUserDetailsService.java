package com.onsemi.cim.apps.exensio.exensioDearchiver.service;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.AppUser;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.AppUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository repo;

    public AppUserDetailsService(AppUserRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser u = repo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Collection<GrantedAuthority> authorities = u.getRoles().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        return org.springframework.security.core.userdetails.User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!u.isEnabled())
                .build();
    }
}
