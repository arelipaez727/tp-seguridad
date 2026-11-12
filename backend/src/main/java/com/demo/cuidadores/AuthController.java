package com.demo.cuidadores;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    record Creds(String email, String password) {}

    // Lista pequeña de contraseñas inseguras para el laboratorio
    private static final Set<String> COMMON_PASSWORDS = Set.of(
        "123456789012",
        "password1234",
        "qwerty123456",
        "aaaaaaaaaaaa",
        "contrasena123"
    );

    private final UserRepository repo;
    private final PasswordEncoder enc;
    private final JwtService jwt;
    private final LoginAttemptService attempts;

    public AuthController(
            UserRepository repo,
            PasswordEncoder enc,
            JwtService jwt,
            LoginAttemptService attempts) {

        this.repo = repo;
        this.enc = enc;
        this.jwt = jwt;
        this.attempts = attempts;
    }

    // REGISTRO CON VALIDACIÓN DE CONTRASEÑAS
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Creds c) {

        if (c == null || c.email() == null ||
                c.email().isBlank() ||
                c.password() == null ||
                c.password().isBlank()) {

            return ResponseEntity.badRequest().body(
                Map.of("error", "Email y contraseña son obligatorios")
            );
        }

        String email = c.email().trim().toLowerCase(Locale.ROOT);
        String password = c.password();

        // Validación de longitud y contraseñas comunes
        if (password.length() < 12 ||
                COMMON_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {

            return ResponseEntity.badRequest().body(
                Map.of("error",
                    "La contraseña debe tener al menos 12 caracteres y no ser común")
            );
        }

        if (repo.findByEmail(email).isPresent()) {
            return ResponseEntity.status(409).body(
                Map.of("error", "No se pudo crear la cuenta")
            );
        }

        User u = new User();
        u.email = email;
        u.password = enc.encode(password);

        repo.save(u);

        return ResponseEntity.ok(
            Map.of(
                "token", jwt.generate(u.id, u.email),
                "userId", u.id
            )
        );
    }

    // LOGIN CON LIMITACIÓN DE INTENTOS
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Creds c) {

        if (c == null || c.email() == null ||
                c.email().isBlank() || c.password() == null) {

            return ResponseEntity.status(401).body(
                Map.of("error", "Email o contraseña incorrectos")
            );
        }

        String email = c.email().trim().toLowerCase(Locale.ROOT);

        // Comprobar si está bloqueado
        if (attempts.isBlocked(email)) {
            return ResponseEntity.status(429).body(
                Map.of("error",
                    "Demasiados intentos. Intentá nuevamente en 2 minutos")
            );
        }

        User u = repo.findByEmail(email).orElse(null);

        // Comprobar credenciales
        if (u == null || !enc.matches(c.password(), u.password)) {

            attempts.recordFailure(email);

            return ResponseEntity.status(401).body(
                Map.of("error", "Email o contraseña incorrectos")
            );
        }

        // Login correcto: reiniciar contador
        attempts.recordSuccess(email);

        return ResponseEntity.ok(
            Map.of(
                "token", jwt.generate(u.id, u.email),
                "userId", u.id
            )
        );
    }
}
