package com.posrestaurante.backend.usuario.dto;

import com.posrestaurante.backend.usuario.Estacion;
import com.posrestaurante.backend.usuario.Rol;

public record CrearUsuarioRequest(String nombre, Rol rol, Estacion estacion, String pin) {
}
