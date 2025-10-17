package com.onsemi.cim.apps.exensio.exensioDearchiver.web;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.AppUser;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    @Autowired
    private AppUserRepository appUserRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserSummary> listUsers() {
        return appUserRepository.findAll().stream()
                .map(UserSummary::from)
                .collect(Collectors.toList());
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserRoles(@PathVariable Long id, @RequestBody RoleUpdateRequest req) {
        AppUser user = appUserRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        user.setRoles(req.roles);
        appUserRepository.save(user);
        return ResponseEntity.ok().build();
    }

    public static class UserSummary {
        public Long id;
        public String username;
        public String email;
        public Set<String> roles;
        public boolean enabled;
        public static UserSummary from(AppUser u) {
            UserSummary s = new UserSummary();
            s.id = u.getId();
            s.username = u.getUsername();
            s.email = u.getEmail();
            s.roles = u.getRoles();
            s.enabled = u.isEnabled();
            return s;
        }
    }

    public static class RoleUpdateRequest {
        public Set<String> roles;
    }
}
