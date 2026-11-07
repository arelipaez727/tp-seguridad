package com.demo.cuidadores;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    // ⚠️ VULNERABILIDAD 3b — SECRETO DE FIRMA DÉBIL Y HARDCODEADO (CWE-798 / CWE-521)
    // Es corto, predecible y está en el repositorio: quien lo conozca o lo adivine puede firmar tokens válidos.
    // FIX: secreto aleatorio de 256 bits desde variable de entorno:
    //      Keys.hmacShaKeyFor(Decoders.BASE64.decode(System.getenv("JWT_SECRET")))
    private static final String SECRET = "cuidadores2026";
    private final SecretKey key = Keys.hmacShaKeyFor(Arrays.copyOf(SECRET.getBytes(StandardCharsets.UTF_8), 32));

    public String generate(Long userId, String email) {
        // ⚠️ VULNERABILIDAD 3c — TOKEN SIN EXPIRACIÓN NI REVOCACIÓN (CWE-613)
        // Un token robado sirve para siempre; "Salir" solo lo borra del navegador.
        // FIX: .expiration(new Date(System.currentTimeMillis() + 15 * 60 * 1000)) + refresh token + lista de revocación
        return Jwts.builder().subject(String.valueOf(userId)).claim("email", email)
                .issuedAt(new Date()).signWith(key).compact();
    }

    public Long parse(String token) {
        return Long.valueOf(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());
    }
}
