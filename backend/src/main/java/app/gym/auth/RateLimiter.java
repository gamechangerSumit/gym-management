package app.gym.auth;

import app.gym.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory token buckets: one per client IP and one per login id. Single-node deployment (see docs/SECURITY.md). */
@Component
public class RateLimiter {

    private static final int MAX_TRACKED_KEYS = 20_000;

    private final AppProperties props;
    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> accountBuckets = new ConcurrentHashMap<>();

    public RateLimiter(AppProperties props) {
        this.props = props;
    }

    public boolean tryIp(String ip) {
        AppProperties.RateLimit rl = props.rateLimit();
        return bucket(ipBuckets, ip, rl.ipCapacity(), rl.ipWindowMinutes()).tryConsume(1);
    }

    public boolean tryAccount(String loginId) {
        AppProperties.RateLimit rl = props.rateLimit();
        return bucket(accountBuckets, loginId.toLowerCase(), rl.accountCapacity(), rl.accountWindowMinutes()).tryConsume(1);
    }

    /** Clears all counters (used by tests). */
    public void reset() {
        ipBuckets.clear();
        accountBuckets.clear();
    }

    private static Bucket bucket(Map<String, Bucket> map, String key, int capacity, int windowMinutes) {
        if (map.size() > MAX_TRACKED_KEYS) {
            map.clear();
        }
        return map.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, Duration.ofMinutes(windowMinutes))
                        .build())
                .build());
    }
}
