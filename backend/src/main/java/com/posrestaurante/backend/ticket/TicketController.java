package com.posrestaurante.backend.ticket;

import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.orden.Orden;
import com.posrestaurante.backend.orden.OrdenItem;
import com.posrestaurante.backend.orden.OrdenItemRepository;
import com.posrestaurante.backend.orden.dto.OrdenItemResponse;
import com.posrestaurante.backend.security.AuthenticatedUser;
import com.posrestaurante.backend.ticket.dto.TicketResponse;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets")
@Transactional
public class TicketController {

    private final TicketRepository ticketRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final UsuarioRepository usuarioRepository;

    public TicketController(TicketRepository ticketRepository, OrdenItemRepository ordenItemRepository,
                             UsuarioRepository usuarioRepository) {
        this.ticketRepository = ticketRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public List<TicketResponse> listarTickets() {
        return ticketRepository.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/propios")
    public List<TicketResponse> misTickets(Authentication authentication) {
        AuthenticatedUser caller = (AuthenticatedUser) authentication.getPrincipal();
        Usuario chef = usuarioRepository.findById(caller.usuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return ticketRepository.findByEstacion(chef.getEstacion()).stream().map(this::toResponse).toList();
    }

    private TicketResponse toResponse(Ticket ticket) {
        List<OrdenItemResponse> items = ordenItemRepository.findByTicket(ticket).stream()
                .map(this::toItemResponse).toList();
        Orden orden = ticket.getOrden();
        return new TicketResponse(ticket.getId(), ticket.getEstacion(), ticket.getFechaGenerado(),
                ticket.getEstado(), orden.getId(), orden.getMesa() == null ? null : orden.getMesa().getNumero(),
                items);
    }

    private OrdenItemResponse toItemResponse(OrdenItem item) {
        Set<UUID> modIds = item.getModificadores().stream().map(Modificador::getId).collect(Collectors.toSet());
        Set<String> modNombres = item.getModificadores().stream().map(Modificador::getNombre).collect(Collectors.toSet());
        return new OrdenItemResponse(item.getId(), item.getPlatillo().getId(), item.getPlatillo().getNombre(),
                item.getCantidad(), item.getPrecioUnitario(), item.getEstado(), modIds, modNombres);
    }

}
