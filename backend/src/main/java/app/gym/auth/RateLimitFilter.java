package app.gym.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-IP limit on the unauthenticated credential endpoints. (The per-account limit lives in AuthService.) */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/activate",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/confirm");

    private final RateLimiter limiter;
    private final ObjectMapper mapper;

    public RateLimitFilter(RateLimiter limiter, ObjectMapper mapper) {
        this.limiter = limiter;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!limiter.tryIp(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            JsonSecurityHandlers.write(response, mapper, 429, "RATE_LIMITED",
                    "Too many attempts. Please wait a minute and try again.");
            return;
        }
        chain.doFilter(request, response);
    }
}
