package com.posrestaurante.backend.orden;

import com.posrestaurante.backend.mesa.Mesa;
import com.posrestaurante.backend.usuario.Usuario;
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
@Table(name = "orden")
@Getter
@Setter
public class Orden {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesero_id")
    private Usuario mesero;

    @Enumerated(EnumType.STRING)
    private TipoOrden tipo;

    @Enumerated(EnumType.STRING)
    private EstadoOrden estado;

    private Instant fechaApertura;
    private Instant fechaCierre;

}
