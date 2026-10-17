package app.gym.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;

/** Loads named SQL from src/main/resources/sql/<name>.sql (the files are also runnable in psql with -v params). */
public final class Sql {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private Sql() {}

    public static String load(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            try (InputStream in = new ClassPathResource("sql/" + n + ".sql").getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Missing SQL resource: " + n, e);
            }
        });
    }
}
