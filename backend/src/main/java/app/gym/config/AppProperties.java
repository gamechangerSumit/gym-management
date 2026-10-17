package app.gym.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Typed view of the "app.*" configuration. Defaults live here so tests can override single values. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:5173") String publicBaseUrl,
        @DefaultValue("91") String defaultCountryCode,
        @DefaultValue Cookie cookie,
        @DefaultValue Security security,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Token token,
        @DefaultValue Qr qr,
        @DefaultValue Bootstrap bootstrap,
        @DefaultValue Cors cors,
        @DefaultValue Dashboard dashboard) {

    public record Cookie(@DefaultValue("true") boolean secure) {}

    public record Security(@DefaultValue("12") int bcryptStrength,
                           @DefaultValue("5") int maxFailedAttempts,
                           @DefaultValue("15") int lockMinutes) {}

    public record RateLimit(@DefaultValue("30") int ipCapacity,
                            @DefaultValue("1") int ipWindowMinutes,
                            @DefaultValue("10") int accountCapacity,
                            @DefaultValue("15") int accountWindowMinutes) {}

    public record Token(@DefaultValue("72") int inviteTtlHours,
                        @DefaultValue("2") int resetTtlHours) {}

    public record Qr(@DefaultValue("") String encryptionKey) {}

    public record Bootstrap(@DefaultValue("owner") String ownerLogin,
                            @DefaultValue("") String ownerPassword,
                            @DefaultValue("true") boolean ownerMustChangePassword) {}

    public record Cors(@DefaultValue("") String allowedOrigins) {}

    public record Dashboard(@DefaultValue("60") int expiredLookbackDays) {}
}
