package com.posrestaurante.backend.asistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RegistroAsistenciaRepository extends JpaRepository<RegistroAsistencia, UUID> {

    long countByUsuarioId(UUID usuarioId);

}
