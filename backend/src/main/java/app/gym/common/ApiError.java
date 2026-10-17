package app.gym.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/** Uniform error body. Never contains stack traces or internal details. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String code, String message, Map<String, String> fieldErrors) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message, null);
    }

    public static ApiError of(int status, String code, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, code, message, fieldErrors);
    }
}
