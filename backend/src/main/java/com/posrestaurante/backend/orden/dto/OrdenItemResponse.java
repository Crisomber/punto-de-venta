package com.posrestaurante.backend.orden.dto;

import com.posrestaurante.backend.orden.EstadoOrdenItem;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record OrdenItemResponse(UUID id, UUID platilloId, String platilloNombre, int cantidad,
                                 BigDecimal precioUnitario, String varianteNombre, EstadoOrdenItem estado,
                                 Set<UUID> modificadorIds, Set<String> modificadorNombres) {
}
