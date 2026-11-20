package com.posrestaurante.backend.platillo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatilloRepository extends JpaRepository<Platillo, UUID> {
}
