package com.posrestaurante.backend.caja;

import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.orden.EstadoOrden;
import com.posrestaurante.backend.orden.Orden;
import com.posrestaurante.backend.orden.OrdenItem;
import com.posrestaurante.backend.orden.OrdenItemRepository;
import com.posrestaurante.backend.orden.OrdenRepository;
import com.posrestaurante.backend.orden.dto.OrdenItemResponse;
import com.posrestaurante.backend.orden.dto.OrdenResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/caja")
@Transactional
public class CajaController {

    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;

    public CajaController(OrdenRepository ordenRepository, OrdenItemRepository ordenItemRepository) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
    }

    @GetMapping("/pendientes")
    public List<OrdenResponse> cuentasPendientes() {
        return ordenRepository.findByEstado(EstadoOrden.CERRADA).stream()
                .map(orden -> toResponse(orden, obtenerItems(orden))).toList();
    }

    private List<OrdenItemResponse> obtenerItems(Orden orden) {
        return ordenItemRepository.findByOrden(orden).stream().map(this::toItemResponse).toList();
    }

    private OrdenItemResponse toItemResponse(OrdenItem item) {
        Set<UUID> modIds = item.getModificadores().stream().map(Modificador::getId).collect(Collectors.toSet());
        Set<String> modNombres = item.getModificadores().stream().map(Modificador::getNombre).collect(Collectors.toSet());
        return new OrdenItemResponse(item.getId(), item.getPlatillo().getId(), item.getPlatillo().getNombre(),
                item.getCantidad(), item.getPrecioUnitario(), item.getEstado(), modIds, modNombres);
    }

    private OrdenResponse toResponse(Orden orden, List<OrdenItemResponse> items) {
        return new OrdenResponse(orden.getId(),
                orden.getMesa() == null ? null : orden.getMesa().getNumero(),
                orden.getMesero().getId(), orden.getMesero().getNombre(),
                orden.getTipo(), orden.getEstado(), orden.getFechaApertura(), orden.getFechaCierre(),
                items);
    }

}
