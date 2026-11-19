package com.posrestaurante.backend.auth.dto;

import com.posrestaurante.backend.usuario.Rol;

import java.util.UUID;

public record MeResponse(UUID usuarioId, String nombre, Rol rol) {
}
