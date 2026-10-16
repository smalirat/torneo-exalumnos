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

    @Override
    public void run(ApplicationArguments args) {
        if (userRepo.existsByUsername(adminUsername)) return;
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("No admin user '{}' and APP_ADMIN_PASSWORD is empty: no admin was created", adminUsername);
            return;
        }
        userRepo.save(new AppUser(null, adminUsername, encoder.encode(adminPassword), AppUser.Role.ADMIN));
        log.info("Admin user '{}' created", adminUsername);
    }
}
