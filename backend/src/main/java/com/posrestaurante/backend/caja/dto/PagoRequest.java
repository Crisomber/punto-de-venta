package com.posrestaurante.backend.caja.dto;

import com.posrestaurante.backend.caja.MetodoPago;

public record PagoRequest(MetodoPago metodo) {
}
