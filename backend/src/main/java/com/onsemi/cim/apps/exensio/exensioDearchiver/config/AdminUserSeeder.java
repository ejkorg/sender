package com.onsemi.cim.apps.exensio.exensioDearchiver.config;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.AppUser;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.annotation.PostConstruct;
import java.util.Set;

@Configuration
public class AdminUserSeeder {
    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void seedAdminUser() {
        if (appUserRepository.findByUsername("admin").isEmpty()) {
            AppUser admin = new AppUser();
            admin.setUsername("admin");
            admin.setEmail("admin@localhost");
            admin.setPasswordHash(passwordEncoder.encode("admin123"));
            admin.setEnabled(true);
            admin.setRoles(Set.of("ROLE_ADMIN", "ROLE_USER"));
            appUserRepository.save(admin);
            System.out.println("Seeded initial admin user: admin/admin123");
        }
    }
}
