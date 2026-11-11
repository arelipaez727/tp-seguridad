package com.demo.cuidadores;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ProfileRepositorySecurityTest {

    @Autowired
    private ProfileRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.save(profile("Nueva Córdoba"));
        repository.save(profile("Alta Córdoba"));
    }

    @Test
    void trataElPayloadOrComoDatoYNoComoSql() {
        var result = repository.findByZonaContainingIgnoreCase("zzz' OR '1'='1' --");

        assertThat(result).isEmpty();
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void noEjecutaConsultasApiladas() {
        var result = repository.findByZonaContainingIgnoreCase(
                "zzz'; DELETE FROM profiles; --"
        );

        assertThat(result).isEmpty();
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void conservaLaBusquedaParcialSinDistinguirMayusculas() {
        var result = repository.findByZonaContainingIgnoreCase("córdoba");

        assertThat(result).hasSize(2);
    }

    private Profile profile(String zona) {
        Profile profile = new Profile();
        profile.ownerId = 1L;
        profile.nombre = "Nombre";
        profile.apellido = "Apellido";
        profile.zona = zona;
        return profile;
    }
}
