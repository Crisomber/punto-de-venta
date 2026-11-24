package com.posrestaurante.backend.caja;

import com.posrestaurante.backend.caja.dto.PagoRequest;
import com.posrestaurante.backend.caja.dto.ReciboResponse;
import com.posrestaurante.backend.mesa.Mesa;
import com.posrestaurante.backend.mesa.EstadoMesa;
import com.posrestaurante.backend.mesa.MesaRepository;
import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.orden.EstadoOrden;
import com.posrestaurante.backend.orden.Orden;
import com.posrestaurante.backend.orden.OrdenItem;
import com.posrestaurante.backend.orden.OrdenItemRepository;
import com.posrestaurante.backend.orden.OrdenRepository;
import com.posrestaurante.backend.orden.dto.OrdenItemResponse;
import com.posrestaurante.backend.orden.dto.OrdenResponse;
import com.posrestaurante.backend.security.AuthenticatedUser;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
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
    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoRepository pagoRepository;

    public CajaController(OrdenRepository ordenRepository, OrdenItemRepository ordenItemRepository,
                           MesaRepository mesaRepository, UsuarioRepository usuarioRepository,
                           PagoRepository pagoRepository) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.mesaRepository = mesaRepository;
        this.usuarioRepository = usuarioRepository;
        this.pagoRepository = pagoRepository;
    }

    @GetMapping("/pendientes")
    public List<OrdenResponse> cuentasPendientes() {
        return ordenRepository.findByEstado(EstadoOrden.CERRADA).stream()
                .map(orden -> toResponse(orden, obtenerItems(orden))).toList();
    }

    @PostMapping("/{ordenId}/pagar")
    public ReciboResponse pagar(@PathVariable UUID ordenId, @RequestBody PagoRequest request,
                                 Authentication authentication) {
        if (request.metodo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "metodo requerido");
        }
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (orden.getEstado() != EstadoOrden.CERRADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "la orden no esta cerrada o ya fue pagada");
        }

        AuthenticatedUser caller = (AuthenticatedUser) authentication.getPrincipal();
        Usuario cajero = usuarioRepository.findById(caller.usuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        List<OrdenItem> items = ordenItemRepository.findByOrden(orden);
        BigDecimal monto = items.stream()
                .map(item -> item.getPrecioUnitario().multiply(BigDecimal.valueOf(item.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Pago pago = new Pago();
        pago.setOrden(orden);
        pago.setCajero(cajero);
        pago.setMetodo(request.metodo());
        pago.setMonto(monto);
        pago.setFecha(Instant.now());
        pagoRepository.save(pago);

        orden.setEstado(EstadoOrden.PAGADA);
        ordenRepository.save(orden);

        Mesa mesa = orden.getMesa();
        if (mesa != null) {
            mesa.setEstado(EstadoMesa.LIBRE);
            mesaRepository.save(mesa);
        }

        List<OrdenItemResponse> itemResponses = items.stream().map(this::toItemResponse).toList();
        return new ReciboResponse(orden.getId(), mesa == null ? null : mesa.getNumero(),
                cajero.getId(), cajero.getNombre(), pago.getMetodo(), pago.getMonto(), pago.getFecha(),
                itemResponses);
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
