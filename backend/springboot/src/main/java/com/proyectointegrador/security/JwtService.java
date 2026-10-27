package com.proyectointegrador.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final String secret;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secret = secret;
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserDetails userDetails) {
        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(ahora))
                .setExpiration(new Date(ahora + expirationMs))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject().equals(userDetails.getUsername())
                    && claims.getExpiration().after(new Date())
                    && userDetails.isEnabled()
                    && !emitidoAntesDelCambioDePassword(claims, userDetails);
        } catch (Exception ex) {
            return false;
        }
    }

    /** H35: tras restablecer la contraseña, las sesiones abiertas antes deben iniciar sesión otra vez. */
    private boolean emitidoAntesDelCambioDePassword(Claims claims, UserDetails userDetails) {
        if (!(userDetails instanceof UserPrincipal principal) || principal.getPasswordActualizadoEn() == null) {
            return false;
        }
        Date emitido = claims.getIssuedAt();
        Instant cambio = principal.getPasswordActualizadoEn().truncatedTo(ChronoUnit.SECONDS);
        return emitido == null || emitido.toInstant().isBefore(cambio);
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private SecretKey getSignInKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
