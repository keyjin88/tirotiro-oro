package oro.tirotiro.equipmentwarehouse.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import oro.tirotiro.equipmentwarehouse.auth.UserRegistrationService;

public class RegistrationForm {

    @NotBlank
    @Email
    @Size(max = 320)
    private String email;

    @NotBlank
    @Size(max = 255)
    private String displayName;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotBlank
    private String passwordConfirmation;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordConfirmation() {
        return passwordConfirmation;
    }

    public void setPasswordConfirmation(String passwordConfirmation) {
        this.passwordConfirmation = passwordConfirmation;
    }

    public boolean passwordsMatch() {
        return password != null && password.equals(passwordConfirmation);
    }

    public UserRegistrationService.RegisterUserCommand toCommand() {
        return new UserRegistrationService.RegisterUserCommand(email, displayName, password);
    }
}
