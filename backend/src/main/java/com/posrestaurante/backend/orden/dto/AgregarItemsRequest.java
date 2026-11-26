package com.posrestaurante.backend.orden.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AgregarItemsRequest(List<ItemRequest> items) {

    public record ItemRequest(UUID platilloId, Integer cantidad, Set<UUID> modificadorIds, UUID varianteId) {
    }

}
