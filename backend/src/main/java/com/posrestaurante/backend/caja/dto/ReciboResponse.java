package com.posrestaurante.backend.caja.dto;

import com.posrestaurante.backend.caja.MetodoPago;
import com.posrestaurante.backend.orden.dto.OrdenItemResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReciboResponse(UUID ordenId, Integer mesaNumero, UUID cajeroId, String cajeroNombre,
                              MetodoPago metodo, BigDecimal monto, Instant fecha, List<OrdenItemResponse> items) {
}
