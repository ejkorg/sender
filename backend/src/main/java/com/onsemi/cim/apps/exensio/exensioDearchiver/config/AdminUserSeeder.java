package com.onsemi.cim.apps.exensio.exensioDearchiver.config;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.AppUser;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.AppUserRepository;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@DependsOnDatabaseInitialization
public class AdminUserSeeder implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);
    private final AtomicBoolean seeded = new AtomicBoolean(false);

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (seeded.compareAndSet(false, true)) {
            log.debug("AdminUserSeeder triggered after application ready event");
            seedAdminUser();
        }
    }

    private void seedAdminUser() {
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
                log.info("Seeded initial admin user: admin/admin123");
            }
        } catch (Exception e) {
            log.debug("AdminUserSeeder primary check failed; falling back to repository lookup", e);
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
                    log.info("Seeded initial admin user (fallback): admin/admin123");
                }
            } catch (Exception ex) {
                log.warn("AdminUserSeeder: could not check/seed admin user", ex);
            }
        }
    }
}
