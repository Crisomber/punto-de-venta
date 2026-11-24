package com.posrestaurante.backend.auth.dto;

public record CambiarPasswordRequest(String passwordActual, String passwordNueva) {
}
