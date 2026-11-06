package com.proyectointegrador.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.DeserializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;

@Service
public class JwtService {

    private static final ObjectMapper JWT_JSON = new ObjectMapper();
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
                .claim("cred", selloCredencial(userDetails))
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
                    && credencialVigente(claims, userDetails);
        } catch (Exception ex) {
            return false;
        }
    }

    /** H35: no depende de la precisión en segundos de iat ni del DATETIME de MySQL. */
    private boolean credencialVigente(Claims claims, UserDetails userDetails) {
        if (!claims.containsKey("cred")) {
            // Compatibilidad solo para cuentas sin recuperación registrada.
            return !(userDetails instanceof UserPrincipal principal) || principal.getPasswordActualizadoEn() == null;
        }
        Object sello = claims.get("cred");
        return claims.getIssuedAt() != null
                && sello instanceof String valor && valor.matches("[0-9a-f]{64}")
                && MessageDigest.isEqual(valor.getBytes(StandardCharsets.US_ASCII),
                        selloCredencial(userDetails).getBytes(StandardCharsets.US_ASCII));
    }

    /** Algoritmo compartido con Django; el JWT nunca expone el hash BCrypt almacenado. */
    private String selloCredencial(UserDetails userDetails) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update("foundia-session-v1\0".getBytes(StandardCharsets.UTF_8));
            mac.update(userDetails.getUsername().getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return HexFormat.of().formatHex(mac.doFinal(userDetails.getPassword().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo generar el sello de sesión", ex);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .deserializeJsonWith(this::leerJsonJwt)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Map<String, ?> leerJsonJwt(byte[] bytes) {
        try {
            Map<String, ?> valores = JWT_JSON.readValue(bytes, new TypeReference<Map<String, ?>>() {});
            // JJWT 0.11 elimina claims null: rechazarlos antes evita un falso modo legado.
            if (valores.containsKey("cred") && valores.get("cred") == null) {
                throw new DeserializationException("Sello de sesión inválido");
            }
            return valores;
        } catch (IOException ex) {
            throw new DeserializationException("JWT inválido", ex);
        }
    }

    private SecretKey getSignInKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
