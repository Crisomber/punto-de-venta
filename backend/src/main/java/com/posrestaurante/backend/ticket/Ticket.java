package com.posrestaurante.backend.ticket;

import com.posrestaurante.backend.orden.Orden;
import com.posrestaurante.backend.usuario.Estacion;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket")
@Getter
@Setter
public class Ticket {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id")
    private Orden orden;

    @Enumerated(EnumType.STRING)
    private Estacion estacion;

    private Instant fechaGenerado;

    @Enumerated(EnumType.STRING)
    private TicketEstado estado;

}
