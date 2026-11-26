package com.posrestaurante.backend.platillo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VarianteRepository extends JpaRepository<Variante, UUID> {

    List<Variante> findByPlatilloIdOrderByOrdenIndexAsc(UUID platilloId);

    void deleteByPlatilloId(UUID platilloId);

}
