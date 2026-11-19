package com.posrestaurante.backend.usuario.dto;

import com.posrestaurante.backend.usuario.Estacion;
import com.posrestaurante.backend.usuario.Rol;

import java.util.UUID;

public record UsuarioResponse(UUID id, String nombre, Rol rol, Estacion estacion, String username, boolean activo) {
}
