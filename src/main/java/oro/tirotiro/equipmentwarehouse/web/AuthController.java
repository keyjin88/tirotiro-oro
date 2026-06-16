package oro.tirotiro.equipmentwarehouse.web;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import oro.tirotiro.equipmentwarehouse.auth.UserRegistrationService;

@Controller
public class AuthController {

    private final UserRegistrationService userRegistrationService;

    public AuthController(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registrationForm") RegistrationForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (!form.passwordsMatch()) {
            bindingResult.addError(new FieldError(
                    "registrationForm",
                    "passwordConfirmation",
                    form.getPasswordConfirmation(),
                    false,
                    new String[] {"registration.passwordsMismatch"},
                    null,
                    "Пароли не совпадают."));
        }
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            userRegistrationService.register(form.toCommand());
        } catch (UserRegistrationService.DuplicateEmailException ex) {
            bindingResult.addError(new FieldError(
                    "registrationForm",
                    "email",
                    form.getEmail(),
                    false,
                    new String[] {"registration.emailDuplicate"},
                    null,
                    ex.getMessage()));
            return "register";
        }

        redirectAttributes.addFlashAttribute("message", "Аккаунт создан. Войдите с новой учетной записью.");
        return "redirect:/login";
    }
}
