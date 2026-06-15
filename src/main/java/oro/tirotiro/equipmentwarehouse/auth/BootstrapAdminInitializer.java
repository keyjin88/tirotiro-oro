package oro.tirotiro.equipmentwarehouse.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import oro.tirotiro.equipmentwarehouse.auth.persistence.Role;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleCode;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleRepository;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public BootstrapAdminInitializer(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRoleCode(RoleCode.ADMIN)) {
            return;
        }

        AppProperties.BootstrapAdmin bootstrapAdmin = appProperties.bootstrapAdmin();
        String email = textOrNull(bootstrapAdmin == null ? null : bootstrapAdmin.email());
        String password = textOrNull(bootstrapAdmin == null ? null : bootstrapAdmin.password());
        if (email == null || password == null) {
            throw new IllegalStateException(
                    "No ADMIN user exists. Set APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD "
                            + "to create the first admin.");
        }

        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is missing. Check database migrations."));
        String displayName = textOrNull(bootstrapAdmin.name());

        User admin = new User(email, passwordEncoder.encode(password), displayName == null ? email : displayName);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
        LOGGER.info("Created bootstrap admin user {}", email);
    }

    private String textOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
