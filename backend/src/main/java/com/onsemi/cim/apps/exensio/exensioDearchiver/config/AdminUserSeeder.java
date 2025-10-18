package com.onsemi.cim.apps.exensio.exensioDearchiver.config;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.AppUser;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.AppUserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void seedAdminUser() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM users WHERE username = ?",
                    Integer.class, "admin");
            if (count == null || count == 0) {
                AppUser admin = new AppUser();
                admin.setUsername("admin");
                admin.setEmail("admin@localhost");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setEnabled(true);
                admin.setRoles(Set.of("ROLE_ADMIN", "ROLE_USER"));
                appUserRepository.save(admin);
                System.out.println("Seeded initial admin user: admin/admin123");
            }
        } catch (Exception e) {
            // If the raw JDBC check fails (DB not available yet or unexpected types),
            // fall back to a safe attempt using the repository but catch exceptions to
            // avoid failing application startup.
            try {
                if (appUserRepository.findByUsername("admin").isEmpty()) {
                    AppUser admin = new AppUser();
                    admin.setUsername("admin");
                    admin.setEmail("admin@localhost");
                    admin.setPasswordHash(passwordEncoder.encode("admin123"));
                    admin.setEnabled(true);
                    admin.setRoles(Set.of("ROLE_ADMIN", "ROLE_USER"));
                    appUserRepository.save(admin);
                    System.out.println("Seeded initial admin user (fallback): admin/admin123");
                }
            } catch (Exception ex) {
                System.err.println("AdminUserSeeder: could not check/seed admin user: " + ex.getMessage());
            }
        }
    }
}
