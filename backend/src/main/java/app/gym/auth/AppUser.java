package app.gym.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal stored in the server-side session. It only carries identity (id, login, role);
 * status, must-change-password and staff permissions are always re-read from the database.
 */
public final class AppUser implements UserDetails {
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String loginId;
    private final Role role;

    public AppUser(long id, String loginId, Role role) {
        this.id = id;
        this.loginId = loginId;
        this.role = role;
    }

    public long id() { return id; }
    public String loginId() { return loginId; }
    public Role role() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return loginId; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
