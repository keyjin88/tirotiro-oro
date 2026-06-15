package oro.tirotiro.equipmentwarehouse.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import oro.tirotiro.equipmentwarehouse.auth.AuthenticatedUser;

@ControllerAdvice
public class UiControllerAdvice {

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
}
