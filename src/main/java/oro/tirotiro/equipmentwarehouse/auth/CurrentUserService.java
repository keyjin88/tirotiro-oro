package oro.tirotiro.equipmentwarehouse.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.auth.persistence.UserRepository;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return Optional.of(authenticatedUser.id());
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<User> currentUser() {
        return currentUserId().flatMap(userRepository::findByIdWithRolesAndPermissions);
    }

    @Transactional(readOnly = true)
    public User requireCurrentUser() {
        return currentUser().orElseThrow(() -> new IllegalStateException("Требуется аутентифицированный пользователь"));
    }
}
