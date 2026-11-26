package com.posrestaurante.backend.platillo.dto;

import com.posrestaurante.backend.usuario.Estacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CrearPlatilloRequest(String nombre, String descripcion, BigDecimal precio, UUID categoriaId,
                                    Estacion estacion, Set<UUID> modificadorIds, List<VarianteRequest> variantes) {
}
