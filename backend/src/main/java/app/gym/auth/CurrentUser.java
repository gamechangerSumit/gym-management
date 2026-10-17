package app.gym.auth;

import app.gym.common.ApiException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Identity always comes from the server-side session, never from request parameters. */
public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<AppUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AppUser require() {
        return find().orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required."));
    }

    public static long id() {
        return require().id();
    }

    public static Long idOrNull() {
        return find().map(AppUser::id).orElse(null);
    }
}
