package com.mjt.Football_Tournament_Management_System.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection for the login: after MAX_FAILURES wrong passwords for a username within WINDOW, that
 * username is locked for LOCK (the 429 says when to retry). Per username and not per IP: behind the hosting
 * proxy every request comes from the same address, so locking an IP would lock everyone out.
 * In memory: enough for a single instance, and a restart just clears the counters.
 */
@Component
public class LoginAttempts {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK = Duration.ofMinutes(5);

    private record Entry(int failures, Instant firstFailure, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttempts() { this(Clock.systemUTC()); }

    LoginAttempts(Clock clock) { this.clock = clock; }

    /** Seconds until the username can try again; 0 when it is not locked */
    public long secondsLocked(String username) {
        var e = entries.get(key(username));
        if (e == null || e.lockedUntil() == null) return 0;
        long left = Duration.between(clock.instant(), e.lockedUntil()).toSeconds();
        return Math.max(left, 0);
    }

    public void failed(String username) {
        var now = clock.instant();
        entries.compute(key(username), (k, e) -> {
            if (e == null || now.isAfter(e.firstFailure().plus(WINDOW)) || (e.lockedUntil() != null && now.isAfter(e.lockedUntil()))) {
                e = new Entry(0, now, null);
            }
            int failures = e.failures() + 1;
            return new Entry(failures, e.firstFailure(), failures >= MAX_FAILURES ? now.plus(LOCK) : null);
        });
    }

    public void succeeded(String username) { entries.remove(key(username)); }

    private static String key(String username) { return username == null ? "" : username.trim().toLowerCase(Locale.ROOT); }
}
