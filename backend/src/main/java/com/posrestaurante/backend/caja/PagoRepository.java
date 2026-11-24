package com.posrestaurante.backend.caja;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PagoRepository extends JpaRepository<Pago, UUID> {

    List<Pago> findByFechaGreaterThanEqualAndFechaLessThan(Instant desde, Instant hasta);

}
