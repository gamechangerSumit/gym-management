package app.gym.auth;

import app.gym.audit.AuditService;
import app.gym.auth.AuthDtos.LinkResponse;
import app.gym.common.ApiException;
import app.gym.common.Db;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Owner-only staff account and permission management, plus reset links for any non-owner account. */
@RestController
@RequestMapping("/api/v1")
public class StaffController {

    public record StaffDto(long id, String loginId, String status, boolean mustChangePassword,
                           Instant lastLoginAt, Instant createdAt, StaffPermissions permissions) {}

    public record CreateStaffRequest(@NotBlank @Size(min = 3, max = 80) String loginId,
                                     @NotBlank @Size(max = 200) String temporaryPassword,
                                     @NotNull @Valid StaffPermissions permissions) {}

    public record StatusRequest(@NotBlank String status) {}

    private final JdbcClient jdbc;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final PasswordResetService resets;

    public StaffController(JdbcClient jdbc, UserAccountRepository users, PasswordEncoder encoder, AuditService audit,
                           PasswordResetService resets) {
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.resets = resets;
    }

    @GetMapping("/staff")
    @PreAuthorize("@perm.owner()")
    public List<StaffDto> list() {
        return jdbc.sql("SELECT u.id, u.login_id, u.status, u.must_change_password, u.last_login_at, u.created_at, "
                        + "COALESCE(p.can_record_payments, false) a, COALESCE(p.can_manage_memberships, false) b, "
                        + "COALESCE(p.can_edit_members, false) c, COALESCE(p.can_view_reports, false) d "
                        + "FROM user_account u LEFT JOIN staff_permission p ON p.user_id = u.id "
                        + "WHERE u.role = 'STAFF' ORDER BY lower(u.login_id)")
                .query((rs, i) -> new StaffDto(rs.getLong("id"), rs.getString("login_id"), rs.getString("status"),
                        rs.getBoolean("must_change_password"), Db.instant(rs, "last_login_at"),
                        Db.instant(rs, "created_at"),
                        new StaffPermissions(rs.getBoolean("a"), rs.getBoolean("b"), rs.getBoolean("c"), rs.getBoolean("d"))))
                .list();
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.owner()")
    @Transactional
    public StaffDto create(@Valid @RequestBody CreateStaffRequest req) {
        String login = req.loginId().trim();
        if (!InvitationService.LOGIN_PATTERN.matcher(login).matches()) {
            throw ApiException.badRequest("INVALID_LOGIN",
                    "Login ID must be 3-80 characters: letters, digits and . _ @ + - only.");
        }
        if (users.findByLoginId(login).isPresent()) {
            throw ApiException.conflict("LOGIN_TAKEN", "That login ID is already in use.");
        }
        PasswordPolicy.validate(req.temporaryPassword(), login);
        // Staff must choose their own password at first login.
        long id = users.insert(login, encoder.encode(req.temporaryPassword()), Role.STAFF, "ACTIVE", true, false);
        users.savePermissions(id, req.permissions());
        audit.record("STAFF_CREATED", "USER", id, details(login, req.permissions()));
        return get(id);
    }

    @PutMapping("/staff/{id}/permissions")
    @PreAuthorize("@perm.owner()")
    @Transactional
    public StaffDto updatePermissions(@PathVariable long id, @Valid @RequestBody StaffPermissions permissions) {
        UserRow user = requireStaff(id);
        users.savePermissions(id, permissions);
        audit.record("STAFF_PERMISSIONS_CHANGED", "USER", id, details(user.loginId(), permissions));
        return get(id);
    }

    @PostMapping("/staff/{id}/status")
    @PreAuthorize("@perm.owner()")
    @Transactional
    public StaffDto setStatus(@PathVariable long id, @Valid @RequestBody StatusRequest req) {
        UserRow user = requireStaff(id);
        String status = req.status();
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw ApiException.badRequest("INVALID_STATUS", "Status must be ACTIVE or DISABLED.");
        }
        users.setStatus(id, status);
        if ("DISABLED".equals(status)) {
            users.endSessions(user.loginId());
        }
        audit.record("STAFF_STATUS_CHANGED", "USER", id, Map.of("loginId", user.loginId(), "status", status));
        return get(id);
    }

    @PostMapping("/users/{id}/reset-link")
    @PreAuthorize("@perm.owner()")
    public LinkResponse resetLink(@PathVariable long id) {
        return resets.createLink(id);
    }

    private UserRow requireStaff(long id) {
        UserRow user = users.findById(id).orElseThrow(() -> ApiException.notFound("Staff account not found."));
        if (user.role() != Role.STAFF) {
            throw ApiException.notFound("Staff account not found.");
        }
        return user;
    }

    private StaffDto get(long id) {
        return list().stream().filter(s -> s.id() == id).findFirst()
                .orElseThrow(() -> ApiException.notFound("Staff account not found."));
    }

    private static Map<String, Object> details(String login, StaffPermissions p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("loginId", login);
        m.put("canRecordPayments", p.canRecordPayments());
        m.put("canManageMemberships", p.canManageMemberships());
        m.put("canEditMembers", p.canEditMembers());
        m.put("canViewReports", p.canViewReports());
        return m;
    }
}
