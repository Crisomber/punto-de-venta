package com.posrestaurante.backend.auth.dto;

import com.posrestaurante.backend.usuario.Rol;

public record AuthResponse(String token, String nombre, Rol rol) {
}
