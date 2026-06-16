package oro.tirotiro.equipmentwarehouse.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import oro.tirotiro.equipmentwarehouse.auth.AuthenticatedUser;
import oro.tirotiro.equipmentwarehouse.config.AppProperties;

@ControllerAdvice
public class UiControllerAdvice {

    private final AppProperties appProperties;

    UiControllerAdvice(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @ModelAttribute("appVersion")
    String appVersion() {
        return appProperties.version();
    }

    @ModelAttribute("currentUserDisplayName")
    String currentUserDisplayName(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return "";
        }
        return user.displayName();
    }

    @ModelAttribute("canCreateEquipment")
    boolean canCreateEquipment(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN") || authority.equals("EQUIPMENT_CREATE"));
    }

    @ModelAttribute("isAdmin")
    boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN"));
    }
}
