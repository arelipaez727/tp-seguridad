package com.demo.cuidadores;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.util.Date;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;

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

    // Genera un JWT con una duración de 15 minutos.
    public String generate(Long userId, String email) {

        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(new Date(now))
                .expiration(new Date(now + 15 * 60 * 1000L))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // Verifica el JWT y recupera el ID del usuario.
    public Long parse(String token) {

        return Long.valueOf(
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject()
        );
    }
}
