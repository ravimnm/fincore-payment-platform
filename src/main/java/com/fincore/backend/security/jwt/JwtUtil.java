package com.fincore.backend.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateToken(String username) {

        return generateToken(username, Set.of());
    }

    public String generateToken(
            String username,
            Set<String> roles) {

        return Jwts.builder()
                .setSubject(username)
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(
                        new Date(System.currentTimeMillis() + expiration)
                )
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {

        return getClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public Set<String> extractRoles(String token) {

        Object roles = getClaims(token).get("roles");

        if (roles instanceof Set<?>) {
            return (Set<String>) roles;
        }

        if (roles instanceof java.util.List<?>) {
            return Set.copyOf((java.util.List<String>) roles);
        }

        return Set.of();
    }

    public boolean validateToken(
            String token,
            String username) {

        Claims claims = getClaims(token);

        return claims.getSubject().equals(username)
                && !claims.getExpiration().before(new Date());
    }

    private Claims getClaims(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}