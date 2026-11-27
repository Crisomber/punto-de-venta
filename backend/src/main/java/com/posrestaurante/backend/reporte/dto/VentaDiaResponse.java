package com.posrestaurante.backend.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VentaDiaResponse(LocalDate fecha, BigDecimal total) {
}
