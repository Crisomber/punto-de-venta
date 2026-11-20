package com.posrestaurante.backend.orden;

import com.posrestaurante.backend.mesa.EstadoMesa;
import com.posrestaurante.backend.mesa.Mesa;
import com.posrestaurante.backend.mesa.MesaRepository;
import com.posrestaurante.backend.orden.dto.AbrirOrdenRequest;
import com.posrestaurante.backend.orden.dto.OrdenItemResponse;
import com.posrestaurante.backend.orden.dto.OrdenResponse;
import com.posrestaurante.backend.security.AuthenticatedUser;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/ordenes")
@Transactional
public class OrdenController {

    private final OrdenRepository ordenRepository;
    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;

    public OrdenController(OrdenRepository ordenRepository, MesaRepository mesaRepository,
                            UsuarioRepository usuarioRepository) {
        this.ordenRepository = ordenRepository;
        this.mesaRepository = mesaRepository;
        this.usuarioRepository = usuarioRepository;
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

    private OrdenResponse toResponse(Orden orden, List<OrdenItemResponse> items) {
        return new OrdenResponse(orden.getId(),
                orden.getMesa() == null ? null : orden.getMesa().getNumero(),
                orden.getMesero().getId(), orden.getMesero().getNombre(),
                orden.getTipo(), orden.getEstado(), orden.getFechaApertura(), orden.getFechaCierre(),
                items);
    }

}
