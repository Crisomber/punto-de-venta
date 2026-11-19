package com.posrestaurante.backend.auth.dto;

import java.util.UUID;

public record PinLoginRequest(UUID usuarioId, String pin) {
}
