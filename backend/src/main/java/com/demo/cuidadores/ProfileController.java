package com.demo.cuidadores;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/profiles")
public class ProfileController {
    private final ProfileRepository repo; private final DataSource dataSource;
    public ProfileController(ProfileRepository repo, DataSource dataSource) { this.repo = repo; this.dataSource = dataSource; }

    @GetMapping
    public List<Profile> list(@RequestParam(required = false) String zona) throws Exception {
        // Sin zona: devuelve la lista completa (uso normal del buscador).
        if (zona == null || zona.isBlank()) return repo.findAll();

        // ⚠️ VULNERABILIDAD 1 — SQL INJECTION CON CONSULTAS APILADAS (CWE-89)
        // El texto de la zona se concatena directamente en SQL nativo y se ejecuta con un Statement JDBC plano,
        // que admite varias sentencias separadas por ";". Esto habilita tanto lectura fuera de lo previsto
        // (ej: zzz' OR '1'='1' --) como modificación o borrado de datos (ej: zzz'; DELETE FROM profiles; --).
        // FIX: usar un método derivado de Spring Data (findByZonaContainingIgnoreCase) o una consulta con
        // parámetros nombrados, y nunca ejecutar texto de usuario como lote de sentencias.
        String sql = "SELECT id, owner_id AS \"ownerId\", nombre, apellido, zona, descripcion, intereses, estudios " +
                     "FROM profiles WHERE zona ILIKE '%" + zona + "%'";
        List<Profile> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            boolean isResultSet = st.execute(sql);
            if (isResultSet) {
                try (ResultSet rs = st.getResultSet()) {
                    while (rs.next()) {
                        Profile p = new Profile();
                        p.id = rs.getLong("id"); p.ownerId = rs.getLong("ownerId");
                        p.nombre = rs.getString("nombre"); p.apellido = rs.getString("apellido");
                        p.zona = rs.getString("zona"); p.descripcion = rs.getString("descripcion");
                        p.intereses = rs.getString("intereses"); p.estudios = rs.getString("estudios");
                        out.add(p);
                    }
                }
            }
        }
        // Sin resultados -> lista vacía: el frontend la interpreta como "no se encontraron perfiles".
        return out;
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