package app.gym.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/** Server-side sessions (Spring Session JDBC); the browser only holds an opaque, HttpOnly cookie. */
@Configuration
public class SessionConfig {

    @Bean
    public CookieSerializer cookieSerializer(AppProperties props) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("GYMSESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(props.cookie().secure());
        serializer.setSameSite("Lax");
        return serializer;
    }
}
