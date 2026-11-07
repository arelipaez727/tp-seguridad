package com.demo.cuidadores;

import jakarta.persistence.*;

@Entity @Table(name = "profiles")
public class Profile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long ownerId;
    public String nombre, apellido, zona, intereses, estudios;
    @Column(length = 1000) public String descripcion;
}
