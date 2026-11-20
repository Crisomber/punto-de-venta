package com.posrestaurante.backend.reporte.dto;

import java.util.UUID;

public record PlatilloMasVendidoResponse(UUID platilloId, String platilloNombre, int cantidadVendida) {
}
