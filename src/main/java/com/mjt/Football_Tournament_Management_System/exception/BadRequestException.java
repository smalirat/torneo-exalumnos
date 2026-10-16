package com.mjt.Football_Tournament_Management_System.exception;

/** Business-level validation error that bean validation cannot express. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
