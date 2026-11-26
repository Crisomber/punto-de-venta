package com.posrestaurante.backend.platillo.dto;

import java.math.BigDecimal;

public record VarianteRequest(String nombre, BigDecimal precio) {
}
