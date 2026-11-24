package com.posrestaurante.backend.ticket;

import com.posrestaurante.backend.usuario.Estacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByEstacion(Estacion estacion);

}
