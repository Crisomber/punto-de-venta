package com.posrestaurante.backend.orden;

import com.posrestaurante.backend.mesa.EstadoMesa;
import com.posrestaurante.backend.mesa.Mesa;
import com.posrestaurante.backend.mesa.MesaRepository;
import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.orden.dto.AbrirOrdenRequest;
import com.posrestaurante.backend.orden.dto.AgregarItemsRequest;
import com.posrestaurante.backend.orden.dto.AgregarItemsRequest.ItemRequest;
import com.posrestaurante.backend.orden.dto.OrdenItemResponse;
import com.posrestaurante.backend.orden.dto.OrdenResponse;
import com.posrestaurante.backend.platillo.Platillo;
import com.posrestaurante.backend.platillo.PlatilloRepository;
import com.posrestaurante.backend.security.AuthenticatedUser;
import com.posrestaurante.backend.ticket.Ticket;
import com.posrestaurante.backend.ticket.TicketEstado;
import com.posrestaurante.backend.ticket.TicketRepository;
import com.posrestaurante.backend.usuario.Estacion;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ordenes")
@Transactional
public class OrdenController {

    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlatilloRepository platilloRepository;
    private final TicketRepository ticketRepository;

    public OrdenController(OrdenRepository ordenRepository, OrdenItemRepository ordenItemRepository,
                            MesaRepository mesaRepository, UsuarioRepository usuarioRepository,
                            PlatilloRepository platilloRepository, TicketRepository ticketRepository) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.mesaRepository = mesaRepository;
        this.usuarioRepository = usuarioRepository;
        this.platilloRepository = platilloRepository;
        this.ticketRepository = ticketRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenResponse abrirOrden(@RequestBody AbrirOrdenRequest request, Authentication authentication) {
        if (request.tipo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo requerido");
        }
        boolean esLocal = request.tipo() == TipoOrden.CONSUMO_LOCAL;

        Mesa mesa = null;
        if (esLocal) {
            if (request.mesaId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mesa requerida para consumo local");
            }
            mesa = mesaRepository.findById(request.mesaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "mesa invalida"));
            if (mesa.getEstado() == EstadoMesa.OCUPADA) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "la mesa ya esta ocupada");
            }
        } else if (request.mesaId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "para llevar no debe especificar mesa");
        }

        AuthenticatedUser caller = (AuthenticatedUser) authentication.getPrincipal();
        Usuario mesero = usuarioRepository.findById(caller.usuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        Orden orden = new Orden();
        orden.setMesa(mesa);
        orden.setMesero(mesero);
        orden.setTipo(request.tipo());
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.setFechaApertura(Instant.now());
        ordenRepository.save(orden);

        if (mesa != null) {
            mesa.setEstado(EstadoMesa.OCUPADA);
            mesaRepository.save(mesa);
        }

        return toResponse(orden, List.of());
    }

    @PostMapping("/{id}/items")
    public OrdenResponse agregarItems(@PathVariable UUID id, @RequestBody AgregarItemsRequest request) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (orden.getEstado() != EstadoOrden.ABIERTA && orden.getEstado() != EstadoOrden.ENVIADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "la orden no acepta mas platillos en su estado actual");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items requeridos");
        }

        List<OrdenItem> nuevos = new ArrayList<>();
        for (ItemRequest itemRequest : request.items()) {
            nuevos.add(crearOrdenItem(orden, itemRequest));
        }

        if (orden.getEstado() == EstadoOrden.ENVIADA) {
            generarTickets(orden, nuevos);
        }

        return toResponse(orden, obtenerItems(orden));
    }

    private OrdenItem crearOrdenItem(Orden orden, ItemRequest itemRequest) {
        if (itemRequest.cantidad() == null || itemRequest.cantidad() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cantidad invalida");
        }
        Platillo platillo = platilloRepository.findById(itemRequest.platilloId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "platillo invalido"));
        if (!platillo.isActivo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platillo no disponible");
        }

        Set<UUID> modificadorIds = itemRequest.modificadorIds() == null ? Set.of() : itemRequest.modificadorIds();
        Set<UUID> permitidos = platillo.getModificadores().stream()
                .map(Modificador::getId).collect(Collectors.toSet());
        if (!permitidos.containsAll(modificadorIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modificador no disponible para este platillo");
        }
        Set<Modificador> modificadores = platillo.getModificadores().stream()
                .filter(m -> modificadorIds.contains(m.getId()))
                .collect(Collectors.toSet());

        OrdenItem item = new OrdenItem();
        item.setOrden(orden);
        item.setPlatillo(platillo);
        item.setCantidad(itemRequest.cantidad());
        item.setPrecioUnitario(platillo.getPrecio());
        item.setEstado(EstadoOrdenItem.PENDIENTE);
        item.setModificadores(modificadores);
        return ordenItemRepository.save(item);
    }

    @PostMapping("/{id}/enviar")
    public OrdenResponse enviarOrden(@PathVariable UUID id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (orden.getEstado() != EstadoOrden.ABIERTA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "la orden ya fue enviada o no esta abierta");
        }
        List<OrdenItem> pendientes = ordenItemRepository.findByOrdenAndTicketIsNull(orden);
        if (pendientes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no hay platillos para enviar");
        }

        generarTickets(orden, pendientes);
        orden.setEstado(EstadoOrden.ENVIADA);
        ordenRepository.save(orden);

        return toResponse(orden, obtenerItems(orden));
    }

    private void generarTickets(Orden orden, List<OrdenItem> items) {
        Map<Estacion, Ticket> ticketsPorEstacion = new HashMap<>();
        for (OrdenItem item : items) {
            Estacion estacion = item.getPlatillo().getEstacion();
            Ticket ticket = ticketsPorEstacion.computeIfAbsent(estacion, e -> {
                Ticket nuevo = new Ticket();
                nuevo.setOrden(orden);
                nuevo.setEstacion(e);
                nuevo.setFechaGenerado(Instant.now());
                nuevo.setEstado(TicketEstado.PENDIENTE);
                return ticketRepository.save(nuevo);
            });
            item.setTicket(ticket);
            ordenItemRepository.save(item);
        }
    }

    @PostMapping("/{id}/cerrar")
    public OrdenResponse cerrarOrden(@PathVariable UUID id) {
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (orden.getEstado() != EstadoOrden.ENVIADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "solo se puede cerrar una orden enviada");
        }
        orden.setEstado(EstadoOrden.CERRADA);
        orden.setFechaCierre(Instant.now());
        ordenRepository.save(orden);
        return toResponse(orden, obtenerItems(orden));
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
