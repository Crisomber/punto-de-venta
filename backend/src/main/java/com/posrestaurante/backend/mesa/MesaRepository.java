package com.posrestaurante.backend.mesa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MesaRepository extends JpaRepository<Mesa, UUID> {

    Optional<Mesa> findByNumero(int numero);

}
