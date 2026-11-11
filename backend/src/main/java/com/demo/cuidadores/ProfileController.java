package com.demo.cuidadores;

import java.util.List;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/profiles")
public class ProfileController {
    private final ProfileRepository repo;

    public ProfileController(ProfileRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Profile> list(
            @RequestParam(required = false)
            @Size(max = 100, message = "La zona no puede superar los 100 caracteres")
            String zona) {
        if (zona == null || zona.isBlank()) return repo.findAll();
        return repo.findByZonaContainingIgnoreCase(zona.trim());
    }

    @GetMapping("/{id}")
    public Profile get(@PathVariable Long id) { return repo.findById(id).orElseThrow(); }

    @PostMapping
    public Profile create(@RequestBody Profile in, Authentication auth) {
        in.id = null;
        in.ownerId = (Long) auth.getPrincipal();
        return repo.save(in);
    }

    @PutMapping("/{id}")
    public Profile update(@PathVariable Long id, @RequestBody Profile in, Authentication auth) {
        Profile p = repo.findById(id).orElseThrow();
        // ⚠️ VULNERABILIDAD 2 — IDOR (CWE-639): no se verifica que el perfil pertenezca al usuario autenticado.
        // FIX: if (!p.ownerId.equals((Long) auth.getPrincipal())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        p.nombre = in.nombre; p.apellido = in.apellido; p.zona = in.zona;
        p.descripcion = in.descripcion; p.intereses = in.intereses; p.estudios = in.estudios;
        return repo.save(p);
    }
}
