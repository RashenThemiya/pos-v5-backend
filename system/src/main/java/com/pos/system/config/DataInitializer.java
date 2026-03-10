package com.pos.system.config;

import com.pos.system.model.auth.Authorization;
import com.pos.system.model.auth.AuthorizationStatus;
import com.pos.system.model.auth.AuthorizationType;
import com.pos.system.model.auth.User;
import com.pos.system.repository.AuthorizationRepository;
import com.pos.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AuthorizationRepository authorizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // create default SUPERADMIN only when no auth records exist
        if (authorizationRepository.count() == 0) {
            Authorization superAdminAuth = new Authorization();
            superAdminAuth.setUsername("superadmin");
            superAdminAuth.setEmail("superadmin@example.com");
            superAdminAuth.setPasswordHash(passwordEncoder.encode("superadmin123"));
            superAdminAuth.setType(AuthorizationType.SUPERADMIN);
            superAdminAuth.setStatus(AuthorizationStatus.ACTIVE);

            Authorization saved = authorizationRepository.save(superAdminAuth);

            User superAdminUser = new User();
            superAdminUser.setAuthId(saved.getAuthId());
            superAdminUser.setBranchId(null); // SUPERADMIN has no branch
            superAdminUser.setFullName("Default Super Administrator");
            superAdminUser.setPhone(null);
            superAdminUser.setIsActive(true);
            userRepository.save(superAdminUser);

            System.out.println("Default SUPERADMIN created: superadmin / superadmin123");
        }
    }
}