package com.posrestaurante.backend.usuario.dto;

import com.posrestaurante.backend.usuario.Estacion;

public record EditarUsuarioRequest(String nombre, Estacion estacion, String pin) {
}
