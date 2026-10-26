package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;

/** A DNI as it is stored: only its digits ("30.123.456" -> "30123456"), 7 or 8 of them */
public final class Dni {
    private Dni() {}

    /** Null for blank; 400 when it is not 7 or 8 digits */
    public static String normalize(String raw) {
        if (raw == null) return null;
        var dni = raw.replaceAll("[\\s.\\-]", "");
        if (dni.isEmpty()) return null;
        if (!dni.matches("\\d{7,8}")) throw new BadRequestException("El DNI tiene que tener 7 u 8 números");
        return dni;
    }
}
