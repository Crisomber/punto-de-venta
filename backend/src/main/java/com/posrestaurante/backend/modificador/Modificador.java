package com.posrestaurante.backend.modificador;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "modificador")
@Getter
@Setter
public class Modificador {

    @Id
    private UUID id = UUID.randomUUID();

    private String nombre;

}
