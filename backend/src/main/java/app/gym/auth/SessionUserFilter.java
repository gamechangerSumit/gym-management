package app.gym.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-validates the session's account on every request: a disabled or deleted account loses access immediately, and an
 * account flagged must_change_password can only change its password, read its session, or log out.
 */
public class SessionUserFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/session");

    private final UserAccountRepository users;
    private final ObjectMapper mapper;

    public SessionUserFilter(UserAccountRepository users, ObjectMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUser principal) {
            Optional<UserRow> row = users.findById(principal.id());
            if (row.isEmpty() || !"ACTIVE".equals(row.get().status())) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                JsonSecurityHandlers.write(response, mapper, 401, "UNAUTHENTICATED", "Authentication required.");
                return;
            }
            if (row.get().mustChangePassword()
                    && !ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED.contains(request.getRequestURI())) {
                JsonSecurityHandlers.write(response, mapper, 403, "PASSWORD_CHANGE_REQUIRED",
                        "You must change your password before continuing.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
