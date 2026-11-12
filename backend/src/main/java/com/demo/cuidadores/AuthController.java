package com.demo.cuidadores;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    record Creds(String email, String password) {}

    private final UserRepository repo; private final PasswordEncoder enc; private final JwtService jwt;
    public AuthController(UserRepository repo, PasswordEncoder enc, JwtService jwt) { this.repo = repo; this.enc = enc; this.jwt = jwt; }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Creds c) {
        // ⚠️ VULNERABILIDAD 3a — POLÍTICA DE CONTRASEÑAS DÉBIL (CWE-521): acepta cualquier clave no vacía, incluso "1".
        // FIX: mínimo 12 caracteres, rechazar claves comunes y filtradas, y medir fortaleza (p. ej. zxcvbn).
        if (c.email() == null || c.password() == null || c.password().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Email y contraseña son obligatorios"));
        if (repo.findByEmail(c.email()).isPresent())
            return ResponseEntity.status(409).body(Map.of("error", "Ese email ya está registrado"));
        User u = new User(); u.email = c.email(); u.password = enc.encode(c.password());
        repo.save(u);
        return ResponseEntity.ok(Map.of("token", jwt.generate(u.id, u.email), "userId", u.id));
    }

    // ⚠️ VULNERABILIDAD 3a (cont.) — SIN LÍMITE DE INTENTOS (CWE-307) Y ENUMERACIÓN DE CUENTAS (CWE-204):
    // Se pueden probar claves sin freno ni bloqueo, y devuelve mensajes distintos si el usuario no existe
    // o si la contraseña es incorrecta, permitiendo enumerar cuentas registradas.
    // FIX: contador por email/IP con bloqueo temporal o retardo creciente (p. ej. Bucket4j), y devolver siempre
    // el mismo mensaje genérico ("Email o contraseña incorrectos").
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Creds c) {
        User u = repo.findByEmail(c.email()).orElse(null);
        if (u == null)
            return ResponseEntity.status(401).body(Map.of("error", "El usuario no existe"));
        if (!enc.matches(c.password(), u.password))
            return ResponseEntity.status(401).body(Map.of("error", "Contraseña incorrecta"));
        return ResponseEntity.ok(Map.of("token", jwt.generate(u.id, u.email), "userId", u.id));
    }
}
