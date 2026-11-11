package com.demo.cuidadores;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    List<Profile> findByZonaContainingIgnoreCase(String zona);
}
