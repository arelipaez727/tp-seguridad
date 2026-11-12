package com.demo.cuidadores;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;

    // Lista en memoria de tokens revocados individualmente (jti -> fecha de expiración en ms)
    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();

    // Se obtiene la clave desde una variable de entorno.
    public JwtService(@Value("${JWT_SECRET}") String secretBase64) {

        byte[] secretBytes = Decoders.BASE64.decode(secretBase64);

        if (secretBytes.length < 32) {
            throw new IllegalArgumentException(
                "JWT_SECRET debe tener al menos 32 bytes"
            );
        }

        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    // Genera un JWT con identificador único (jti) y una duración de 15 minutos.
    public String generate(Long userId, String email) {

        long now = System.currentTimeMillis();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(new Date(now))
                .expiration(new Date(now + 15 * 60 * 1000L))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // Revoca individualmente un JWT hasta su fecha de vencimiento.
    public void revoke(String token) {
        cleanupExpired();
        Claims claims = parseClaims(token);
        String tokenId = claims.getId() != null ? claims.getId() : token;
        long exp = claims.getExpiration() != null
                ? claims.getExpiration().getTime()
                : System.currentTimeMillis() + 15 * 60 * 1000L;
        revokedTokens.put(tokenId, exp);
    }

    // Verifica firma, expiración y que el JWT no haya sido revocado, y recupera el ID del usuario.
    public Long parse(String token) {
        cleanupExpired();
        Claims claims = parseClaims(token);
        String tokenId = claims.getId() != null ? claims.getId() : token;

        if (revokedTokens.containsKey(tokenId)) {
            throw new JwtException("El token ha sido revocado");
        }

        return Long.valueOf(claims.getSubject());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
