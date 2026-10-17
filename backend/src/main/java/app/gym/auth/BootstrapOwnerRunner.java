package app.gym.auth;

import app.gym.audit.AuditService;
import app.gym.config.AppProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the single OWNER account on first start from env-provided credentials (never from files in the repo). */
@Component
@Order(1)
public class BootstrapOwnerRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapOwnerRunner.class);

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final AuditService audit;

    public BootstrapOwnerRunner(UserAccountRepository users, PasswordEncoder encoder, AppProperties props,
                                AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsOwner()) {
            return;
        }
        AppProperties.Bootstrap cfg = props.bootstrap();
        if (cfg.ownerPassword() == null || cfg.ownerPassword().isBlank()) {
            log.warn("No OWNER account exists and APP_BOOTSTRAP_OWNER_PASSWORD is not set; nobody can log in yet.");
            return;
        }
        if (cfg.ownerPassword().length() < PasswordPolicy.MIN_LENGTH) {
            throw new IllegalStateException("APP_BOOTSTRAP_OWNER_PASSWORD must be at least "
                    + PasswordPolicy.MIN_LENGTH + " characters.");
        }
        long id = users.insert(cfg.ownerLogin(), encoder.encode(cfg.ownerPassword()), Role.OWNER, "ACTIVE",
                cfg.ownerMustChangePassword(), false);
        audit.recordAs(id, "OWNER_BOOTSTRAPPED", "USER", id, Map.of("loginId", cfg.ownerLogin()));
        log.info("Created OWNER account '{}' (must change password at first login: {})", cfg.ownerLogin(),
                cfg.ownerMustChangePassword());
    }
}
