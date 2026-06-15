package oro.tirotiro.equipmentwarehouse.auth;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import oro.tirotiro.equipmentwarehouse.auth.persistence.Role;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleCode;
import oro.tirotiro.equipmentwarehouse.auth.persistence.RoleRepository;
import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;

@Service
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterUserCommand command) {
        String email = normalizeEmail(command.email());
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new DuplicateEmailException("Пользователь с такой почтой уже зарегистрирован.");
        }

        Role userRole = roleRepository.findByCode(RoleCode.USER)
                .orElseThrow(() -> new IllegalStateException("Роль USER отсутствует. Проверьте миграции базы данных."));
        User user = new User(email, passwordEncoder.encode(command.password()), command.displayName().trim());
        user.getRoles().add(userRole);
        return userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record RegisterUserCommand(String email, String displayName, String password) {
    }

    public static class DuplicateEmailException extends RuntimeException {

        public DuplicateEmailException(String message) {
            super(message);
        }
    }
}
