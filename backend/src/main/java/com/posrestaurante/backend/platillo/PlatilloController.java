package com.posrestaurante.backend.platillo;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.modificador.ModificadorRepository;
import com.posrestaurante.backend.platillo.dto.CrearPlatilloRequest;
import com.posrestaurante.backend.platillo.dto.PlatilloResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/platillos")
public class PlatilloController {

    private final PlatilloRepository platilloRepository;
    private final CategoriaRepository categoriaRepository;
    private final ModificadorRepository modificadorRepository;

    public PlatilloController(PlatilloRepository platilloRepository,
                               CategoriaRepository categoriaRepository,
                               ModificadorRepository modificadorRepository) {
        this.platilloRepository = platilloRepository;
        this.categoriaRepository = categoriaRepository;
        this.modificadorRepository = modificadorRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatilloResponse crearPlatillo(@RequestBody CrearPlatilloRequest request) {
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre requerido");
        }
        if (request.precio() == null || request.precio().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "precio debe ser positivo");
        }
        if (request.estacion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estacion requerida");
        }
        if (request.categoriaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria requerida");
        }
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria invalida"));

        Set<UUID> modificadorIds = request.modificadorIds() == null ? Set.of() : request.modificadorIds();
        Set<Modificador> modificadores = new HashSet<>(modificadorRepository.findAllById(modificadorIds));
        if (modificadores.size() != modificadorIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modificador invalido");
        }

        Platillo platillo = new Platillo();
        platillo.setNombre(request.nombre());
        platillo.setDescripcion(request.descripcion());
        platillo.setPrecio(request.precio());
        platillo.setCategoria(categoria);
        platillo.setEstacion(request.estacion());
        platillo.setModificadores(modificadores);
        platillo.setActivo(true);
        platilloRepository.save(platillo);

        return toResponse(platillo);
    }

    @GetMapping
    public List<PlatilloResponse> listarPlatillos() {
        return platilloRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private PlatilloResponse toResponse(Platillo platillo) {
        Set<UUID> modificadorIds = platillo.getModificadores().stream()
                .map(Modificador::getId)
                .collect(Collectors.toSet());
        return new PlatilloResponse(platillo.getId(), platillo.getNombre(), platillo.getDescripcion(),
                platillo.getPrecio(), platillo.getCategoria().getId(), platillo.getCategoria().getNombre(),
                platillo.getEstacion(), modificadorIds, platillo.isActivo());
    }

}
