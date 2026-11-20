package com.posrestaurante.backend.orden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrdenRepository extends JpaRepository<Orden, UUID> {
}
