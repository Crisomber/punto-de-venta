package com.posrestaurante.backend.platillo;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.usuario.Estacion;
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
@Table(name = "platillo")
@Getter
@Setter
public class Platillo {

    @Id
    private UUID id = UUID.randomUUID();

    private String nombre;

    private String descripcion;

    private BigDecimal precio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    private Estacion estacion;

    @ManyToMany
    @JoinTable(
            name = "platillo_modificador",
            joinColumns = @JoinColumn(name = "platillo_id"),
            inverseJoinColumns = @JoinColumn(name = "modificador_id"))
    private Set<Modificador> modificadores = new HashSet<>();

    private boolean activo;

}
