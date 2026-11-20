package com.posrestaurante.backend.modificador;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ModificadorRepository extends JpaRepository<Modificador, UUID> {
}
