package com.posrestaurante.backend.security;

import com.posrestaurante.backend.usuario.Rol;

import java.util.UUID;

public record AuthenticatedUser(UUID usuarioId, String nombre, Rol rol) {
}
