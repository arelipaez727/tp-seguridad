package com.demo.cuidadores;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    // Máximo de intentos fallidos permitidos
    private static final int MAX_ATTEMPTS = 5;

    // Bloqueo de 2 minutos, para demostrarlo en clase
    private static final long BLOCK_TIME = 2 * 60 * 1000L;

    private final Map<String, Attempt> attempts = new HashMap<>();

    // Comprueba si la cuenta está bloqueada
    public synchronized boolean isBlocked(String email) {
        Attempt attempt = attempts.get(email);

        if (attempt == null || attempt.blockUntil == 0) {
            return false;
        }

        if (System.currentTimeMillis() < attempt.blockUntil) {
            return true;
        }

        attempts.remove(email);
        return false;
    }

    // Registra un intento fallido
    public synchronized void recordFailure(String email) {
        Attempt attempt = attempts.computeIfAbsent(
                email, k -> new Attempt()
        );

        if (attempt.blockUntil > System.currentTimeMillis()) {
            return;
        }

        attempt.failed++;

        if (attempt.failed >= MAX_ATTEMPTS) {
            attempt.blockUntil =
                    System.currentTimeMillis() + BLOCK_TIME;
            attempt.failed = 0;
        }
    }

    // Limpia los intentos cuando el login es exitoso
    public synchronized void recordSuccess(String email) {
        attempts.remove(email);
    }

    private static class Attempt {
        int failed = 0;
        long blockUntil = 0;
    }
}
