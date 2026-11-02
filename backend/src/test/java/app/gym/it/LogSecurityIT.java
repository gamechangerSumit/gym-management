package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

class LogSecurityIT extends app.gym.support.AbstractIT {

    private ListAppender<ILoggingEvent> appender;
    private Logger appLogger;

    @BeforeEach
    void attach() {
        appLogger = (Logger) LoggerFactory.getLogger("app.gym");
        appender = new ListAppender<>();
        appender.start();
        appLogger.addAppender(appender);
        appLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detach() {
        appLogger.detachAppender(appender);
        appLogger.setLevel(Level.INFO);
    }

    @Test
    void passwordsAndTokensNeverAppearInLogs() throws Exception {
        String secret = "SuperSecretPw987";
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"owner\",\"password\":\"" + secret + "\"}"));
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"owner\",\"password\":\"OwnerPass123\"}"));
        long m = createMember("Log Person", "9700000001");
        String link = read(post("/api/v1/members/" + m + "/invite", null, owner), "$.url");
        mvc.perform(post("/api/v1/auth/password-reset/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"owner\"}"));
        String all = String.join("\n", appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        assertThat(all).doesNotContain(secret).doesNotContain("OwnerPass123");
        assertThat(link).isNotBlank();
        String tokenPart = link.substring(Math.max(link.lastIndexOf('=') , link.lastIndexOf('/')) + 1);
        assertThat(tokenPart.length()).isGreaterThan(20);
        assertThat(all).doesNotContain(tokenPart);
    }
}
