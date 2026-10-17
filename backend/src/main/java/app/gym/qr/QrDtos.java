package app.gym.qr;

import java.time.Instant;

public final class QrDtos {
    private QrDtos() {}

    public static final String PREFIX = "GYMPASS:";

    /** payload is what the QR image encodes: a prefix plus a random token. It holds no personal data. */
    public record PassDto(long passId, String token, String payload, String status, Instant createdAt) {
        public static PassDto of(long id, String token, String status, Instant createdAt) {
            return new PassDto(id, token, PREFIX + token, status, createdAt);
        }
    }
}
