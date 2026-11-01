package com.mjt.Football_Tournament_Management_System.security;


import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Collectors;

@Service
public class JwtService {
    /** Tokens carry who issued them, and only tokens issued by this API are accepted */
    static final String ISSUER = "torneo-api";

    private final SecretKey key;
    private final long expirationMillis;
    @Getter
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expMinutes) {
        checkSecret(secret);
        byte[] bytes = secret.length() >= 32 ? secret.getBytes(StandardCharsets.UTF_8) : Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(bytes); // also refuses keys under 256 bits
        this.expirationMinutes = expMinutes;
        this.expirationMillis = expMinutes * 60 * 1000;
    }

    /**
     * Whoever knows the secret can sign a token saying "I am the admin". Refuse to start with one that is public
     * (the placeholder of .env.example is on GitHub) or guessable (a few repeated characters).
     */
    static void checkSecret(String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("APP_JWT_SECRET is not set");
        if (secret.toLowerCase().contains("change-me")) {
            throw new IllegalStateException("APP_JWT_SECRET is still the example value: generate one with openssl rand -base64 48");
        }
        if (secret.chars().distinct().count() < 12) {
            throw new IllegalStateException("APP_JWT_SECRET is too predictable: generate one with openssl rand -base64 48");
        }
    }

    public String generate(UserDetails user) {
        String roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Verifies the signature (HMAC with our key only: an unsigned token or another algorithm is rejected), issuer and expiry */
    public String username(String token) {
        return Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean valid(String token, UserDetails user) {
        try {
            var claims = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                    .parseSignedClaims(token).getPayload();
            return user.getUsername().equals(claims.getSubject())
                    && claims.getExpiration().after(new Date());
        } catch (Exception e) { return false; }
    }
}
