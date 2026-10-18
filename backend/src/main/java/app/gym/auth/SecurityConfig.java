package app.gym.auth;

import app.gym.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder(AppProperties props) {
        return new BCryptPasswordEncoder(props.security().bcryptStrength());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserAccountRepository users, RateLimiter limiter,
                                           ObjectMapper mapper, AppProperties props) throws Exception {
        JsonSecurityHandlers handlers = new JsonSecurityHandlers(mapper);
        // Filters are created here (not as beans) so Spring Boot does not also register them as servlet filters.
        OncePerRequestFilter sessionUserFilter = new SessionUserFilter(users, mapper);
        OncePerRequestFilter rateLimitFilter = new RateLimitFilter(limiter, mapper);

        // Plain (non-XOR) token handler: the SPA reads the XSRF-TOKEN cookie and echoes it in X-XSRF-TOKEN.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler))
            .requestCache(rc -> rc.disable())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .headers(h -> h
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(handlers)
                .accessDeniedHandler(handlers))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/session").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/logout",
                        "/api/v1/auth/activate", "/api/v1/auth/password-reset/request",
                        "/api/v1/auth/password-reset/confirm").permitAll()
                // Defence in depth on top of method security.
                .requestMatchers("/api/v1/staff/**", "/api/v1/settings/**", "/api/v1/audit/**", "/api/v1/users/**").hasRole("OWNER")
                .requestMatchers("/api/v1/me", "/api/v1/me/**").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/v1/addons").hasAnyRole("OWNER", "STAFF", "MEMBER")
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password").authenticated()
                // Everything else under /api/v1 is back-office: members get 403 before any body is even parsed.
                .requestMatchers("/api/v1/**").hasAnyRole("OWNER", "STAFF")
                .anyRequest().denyAll())
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .addFilterAfter(sessionUserFilter, AnonymousAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, org.springframework.security.web.csrf.CsrfFilter.class);

        String origins = props.cors().allowedOrigins();
        if (origins != null && !origins.isBlank()) {
            http.cors(c -> c.configurationSource(corsSource(origins)));
        } else {
            http.cors(AbstractHttpConfigurer::disable);
        }
        return http.build();
    }

    private static CorsConfigurationSource corsSource(String origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> allowed = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        cfg.setAllowedOrigins(allowed);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Accept"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
