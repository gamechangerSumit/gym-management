package app.gym.auth;

import app.gym.auth.AuthDtos.ActivateRequest;
import app.gym.auth.AuthDtos.ChangePasswordRequest;
import app.gym.auth.AuthDtos.LoginRequest;
import app.gym.auth.AuthDtos.MessageResponse;
import app.gym.auth.AuthDtos.ResetConfirmRequest;
import app.gym.auth.AuthDtos.ResetRequest;
import app.gym.auth.AuthDtos.SessionInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final InvitationService invitations;
    private final PasswordResetService resets;

    public AuthController(AuthService auth, InvitationService invitations, PasswordResetService resets) {
        this.auth = auth;
        this.invitations = invitations;
        this.resets = resets;
    }

    @PostMapping("/login")
    public SessionInfo login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                             HttpServletResponse response) {
        return auth.login(body.loginId(), body.password(), request, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        auth.logout(request);
    }

    @GetMapping("/session")
    public SessionInfo session() {
        return auth.describeCurrent();
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@Valid @RequestBody ActivateRequest body) {
        invitations.activate(body.token(), body.newPassword());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        auth.changePassword(body.currentPassword(), body.newPassword(), request);
    }

    /** Always answers the same way so it cannot be used to discover which login IDs exist. */
    @PostMapping("/password-reset/request")
    public ResponseEntity<MessageResponse> requestReset(@Valid @RequestBody ResetRequest body) {
        resets.request(body.loginId());
        return ResponseEntity.accepted().body(new MessageResponse(
                "If that account exists, the gym owner has been notified and can share a reset link with you."));
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmReset(@Valid @RequestBody ResetConfirmRequest body) {
        resets.confirm(body.token(), body.newPassword());
    }
}
