package com.posrestaurante.backend.platillo.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VarianteResponse(UUID id, String nombre, BigDecimal precio) {
}
