package app.gym.common;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * Defence in depth: the code never logs credentials or tokens, and this converter masks anything that looks like one
 * (password=..., "token":"...", Authorization: Bearer ...) before it reaches the log output.
 */
public class LogScrubbingConverter extends MessageConverter {

    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(\"?(?:password|passwd|new_?password|current_?password|token|secret|authorization|cookie|api[_-]?key)\"?\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;&}]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+");

    public static String scrub(String message) {
        if (message == null) {
            return null;
        }
        // Bearer first, so "Authorization: Bearer <token>" is fully masked by the key/value rule afterwards.
        String out = BEARER.matcher(message).replaceAll("$1***");
        return KEY_VALUE.matcher(out).replaceAll("$1\"***\"");
    }

    @Override
    public String convert(ILoggingEvent event) {
        return scrub(event.getFormattedMessage());
    }
}
