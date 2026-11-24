package com.posrestaurante.backend.orden;

import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.platillo.Platillo;
import com.posrestaurante.backend.ticket.Ticket;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "orden_item")
@Getter
@Setter
public class OrdenItem {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id")
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platillo_id")
    private Platillo platillo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    private int cantidad;
    private BigDecimal precioUnitario;

    @Enumerated(EnumType.STRING)
    private EstadoOrdenItem estado;

    @ManyToMany
    @JoinTable(
            name = "orden_item_modificador",
            joinColumns = @JoinColumn(name = "orden_item_id"),
            inverseJoinColumns = @JoinColumn(name = "modificador_id"))
    private Set<Modificador> modificadores = new HashSet<>();

}
