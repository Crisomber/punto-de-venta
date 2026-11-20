package com.posrestaurante.backend.orden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrdenItemRepository extends JpaRepository<OrdenItem, UUID> {

    List<OrdenItem> findByOrden(Orden orden);

    List<OrdenItem> findByOrdenAndTicketIsNull(Orden orden);

}
