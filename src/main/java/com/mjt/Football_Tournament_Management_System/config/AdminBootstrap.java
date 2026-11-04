package com.mjt.Football_Tournament_Management_System.config;

import com.mjt.Football_Tournament_Management_System.entire.AppUser;
import com.mjt.Football_Tournament_Management_System.repo.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the initial admin user from configuration, if it does not exist yet. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final AppUserRepository userRepo;
    private final PasswordEncoder encoder;

    @Value("${app.bootstrap.admin-username}")
    private String adminUsername;
    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;
    private final org.springframework.core.env.Environment env;

    /** Outside the local profiles (dev's H2 and tests use admin123) the admin password must be long */
    static final int MIN_ADMIN_PASSWORD = 12;

    @Value("${app.demo.enabled:false}")
    private boolean demo;
    @Value("${app.demo.username:demo}")
    private String demoUsername;
    @Value("${app.demo.password:}")
    private String demoPassword;
    @Value("${app.demo.captains:}")
    private java.util.List<String> demoCaptains;

    @Override
    public void run(ApplicationArguments args) {
        demoAdmin();
        if (userRepo.existsByUsername(adminUsername)) return;
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("No admin user '{}' and APP_ADMIN_PASSWORD is empty: no admin was created", adminUsername);
            return;
        }
        boolean local = env.matchesProfiles("dev | test");
        if (!local && adminPassword.length() < MIN_ADMIN_PASSWORD) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD must have at least " + MIN_ADMIN_PASSWORD
                    + " characters (openssl rand -base64 18)");
        }
        userRepo.save(new AppUser(null, adminUsername, encoder.encode(adminPassword), AppUser.Role.ADMIN));
        log.info("Admin user '{}' created", adminUsername);
    }

    /**
     * Public demo only: a second admin, and a few existing captains, with the demo password (published in the
     * README), so visitors can try the panel as either. The password from the configuration always wins (changing
     * it there changes it here). The real admin and the other captains keep their own private passwords.
     */
    void demoAdmin() {
        if (!demo || demoPassword.isBlank()) return;
        var user = userRepo.findByUsername(demoUsername).orElse(null);
        if (user == null) {
            userRepo.save(new AppUser(null, demoUsername, encoder.encode(demoPassword), AppUser.Role.ADMIN));
            log.info("Demo admin '{}' created (public demo)", demoUsername);
        } else {
            usePassword(user);
        }
        for (var name : demoCaptains == null ? java.util.List.<String>of() : demoCaptains) {
            if (name.isBlank()) continue;
            userRepo.findByUsername(name.trim())
                    .filter(u -> u.getRole() == AppUser.Role.CAPTAIN && !u.isPending())
                    .ifPresentOrElse(this::usePassword, () -> log.warn("Demo captain '{}' not found (or not a captain)", name.trim()));
        }
    }

    private void usePassword(AppUser user) {
        if (!encoder.matches(demoPassword, user.getPasswordHash())) {
            user.setPasswordHash(encoder.encode(demoPassword));
            userRepo.save(user);
        }
    }
}
