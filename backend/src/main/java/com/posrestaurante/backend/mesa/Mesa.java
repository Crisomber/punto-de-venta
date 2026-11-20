package com.posrestaurante.backend.mesa;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "mesa")
@Getter
@Setter
public class Mesa {

    @Id
    private UUID id = UUID.randomUUID();

    private int numero;

    @Enumerated(EnumType.STRING)
    private EstadoMesa estado;

}
