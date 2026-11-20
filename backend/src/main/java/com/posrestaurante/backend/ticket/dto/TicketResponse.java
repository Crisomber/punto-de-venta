package com.posrestaurante.backend.ticket.dto;

import com.posrestaurante.backend.orden.dto.OrdenItemResponse;
import com.posrestaurante.backend.ticket.TicketEstado;
import com.posrestaurante.backend.usuario.Estacion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketResponse(UUID id, Estacion estacion, Instant fechaGenerado, TicketEstado estado, UUID ordenId,
                              Integer mesaNumero, List<OrdenItemResponse> items) {
}
