package com.posrestaurante.backend.auth.dto;

import com.posrestaurante.backend.usuario.Rol;

import java.util.UUID;

public record StaffListItem(UUID id, String nombre, Rol rol) {
}
