package com.posrestaurante.backend.usuario;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "usuario")
@Getter
@Setter
public class Usuario {

    @Id
    private UUID id = UUID.randomUUID();

    private String nombre;

    @Enumerated(EnumType.STRING)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    private Estacion estacion;

    private String username;

    private String passwordHash;

    private String pinHash;

    private boolean activo;

}
