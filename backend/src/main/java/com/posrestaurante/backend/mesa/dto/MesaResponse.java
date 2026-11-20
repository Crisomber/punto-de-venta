package com.posrestaurante.backend.mesa.dto;

import com.posrestaurante.backend.mesa.EstadoMesa;

import java.util.UUID;

public record MesaResponse(UUID id, int numero, EstadoMesa estado) {
}
