package com.posrestaurante.backend.orden.dto;

import com.posrestaurante.backend.orden.EstadoOrden;
import com.posrestaurante.backend.orden.TipoOrden;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrdenResponse(UUID id, Integer mesaNumero, UUID meseroId, String meseroNombre, TipoOrden tipo,
                             EstadoOrden estado, Instant fechaApertura, Instant fechaCierre,
                             List<OrdenItemResponse> items) {
}
