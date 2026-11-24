package com.posrestaurante.backend.orden.dto;

import com.posrestaurante.backend.orden.TipoOrden;

import java.util.UUID;

public record AbrirOrdenRequest(TipoOrden tipo, UUID mesaId) {
}
