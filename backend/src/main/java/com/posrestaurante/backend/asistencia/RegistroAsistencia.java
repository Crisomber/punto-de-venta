package com.posrestaurante.backend.asistencia;

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
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registro_asistencia")
@Getter
@Setter
@NoArgsConstructor
public class RegistroAsistencia {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private Instant fechaHora;

    @Enumerated(EnumType.STRING)
    private TipoAsistencia tipo;

    public RegistroAsistencia(Usuario usuario, Instant fechaHora, TipoAsistencia tipo) {
        this.usuario = usuario;
        this.fechaHora = fechaHora;
        this.tipo = tipo;
    }

}
