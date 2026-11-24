package com.posrestaurante.backend.reporte.dto;

import java.math.BigDecimal;

public record ReporteVentasResponse(BigDecimal totalVendido, BigDecimal totalEfectivo, BigDecimal totalTarjeta,
                                     BigDecimal ticketPromedio, long cantidadCuentas) {
}
