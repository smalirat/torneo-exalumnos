package com.mjt.Football_Tournament_Management_System.exception;

/** Request is valid but clashes with the current state (duplicates, closed windows, etc). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
