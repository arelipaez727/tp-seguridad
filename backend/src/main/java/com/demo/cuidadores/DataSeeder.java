package com.demo.cuidadores;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final PasswordEncoder enc;

    public DataSeeder(UserRepository users, ProfileRepository profiles, PasswordEncoder enc) {
        this.users = users;
        this.profiles = profiles;
        this.enc = enc;
    }

    @Override
    public void run(String... args) {
        if (profiles.count() > 0) return;
        User ana = user("ana@demo.com");
        User luis = user("luis@demo.com");
        profile(ana, "María", "Gómez", "Nueva Córdoba", "Enfermería profesional", "Tejido, jardinería, folklore", "Diez años de experiencia en cuidado domiciliario de adultos mayores.");
        profile(ana, "Carlos", "Pérez", "Alta Córdoba", "Acompañante terapéutico", "Ajedrez, cocina, fútbol", "Acompañamiento diurno y nocturno. Paciencia y buen trato.");
        profile(luis, "Laura", "Fernández", "Cerro de las Rosas", "Técnica en gerontología", "Lectura, caminatas, música", "Especializada en estimulación cognitiva y movilidad.");
        profile(luis, "Jorge", "Ramírez", "Villa Allende", "Auxiliar de enfermería", "Pesca, radio, mate", "Cuidado de personas con movilidad reducida.");
        profile(luis, "Sofía", "Torres", "Güemes", "Cuidadora domiciliaria certificada", "Pintura, yoga, cine", "Compañía, higiene personal y administración de medicación.");
    }

    private User user(String email) {
        User u = new User();
        u.email = email;
        u.password = enc.encode("demo1234");
        return users.save(u);
    }

    private void profile(User owner, String n, String a, String z, String e, String i, String d) {
        Profile p = new Profile();
        p.ownerId = owner.id;
        p.nombre = n;
        p.apellido = a;
        p.zona = z;
        p.estudios = e;
        p.intereses = i;
        p.descripcion = d;
        profiles.save(p);
    }
}
