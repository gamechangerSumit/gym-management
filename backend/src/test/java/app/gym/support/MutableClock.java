package app.gym.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Controllable clock so tests can move time (expiry boundaries, lockout windows, token expiry). */
public class MutableClock extends Clock {
    private volatile Instant instant;

    public MutableClock(Instant start) {
        this.instant = start;
    }

    public void set(Instant value) {
        this.instant = value;
    }

    public void advance(Duration d) {
        this.instant = this.instant.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
