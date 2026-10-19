package com.mjt.Football_Tournament_Management_System.exception;

import lombok.Getter;

/** 429: too many attempts (e.g. wrong passwords); retryAfterSeconds goes in the Retry-After header. */
@Getter
public class TooManyRequestsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
