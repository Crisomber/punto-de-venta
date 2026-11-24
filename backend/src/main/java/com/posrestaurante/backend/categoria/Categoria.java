package com.posrestaurante.backend.categoria;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "categoria")
@Getter
@Setter
public class Categoria {

    @Id
    private UUID id = UUID.randomUUID();

    private String nombre;

}
