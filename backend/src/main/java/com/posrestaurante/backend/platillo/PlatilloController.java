package com.posrestaurante.backend.platillo;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.modificador.ModificadorRepository;
import com.posrestaurante.backend.platillo.dto.ActivarPlatilloRequest;
import com.posrestaurante.backend.platillo.dto.CrearPlatilloRequest;
import com.posrestaurante.backend.platillo.dto.EditarPlatilloRequest;
import com.posrestaurante.backend.platillo.dto.PlatilloResponse;
import com.posrestaurante.backend.platillo.dto.VarianteRequest;
import com.posrestaurante.backend.platillo.dto.VarianteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/platillos")
@Transactional
public class PlatilloController {

    private final PlatilloRepository platilloRepository;
    private final CategoriaRepository categoriaRepository;
    private final ModificadorRepository modificadorRepository;
    private final VarianteRepository varianteRepository;

    public PlatilloController(PlatilloRepository platilloRepository,
                               CategoriaRepository categoriaRepository,
                               ModificadorRepository modificadorRepository,
                               VarianteRepository varianteRepository) {
        this.platilloRepository = platilloRepository;
        this.categoriaRepository = categoriaRepository;
        this.modificadorRepository = modificadorRepository;
        this.varianteRepository = varianteRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatilloResponse crearPlatillo(@RequestBody CrearPlatilloRequest request) {
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre requerido");
        }
        if (request.estacion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estacion requerida");
        }
        if (request.categoriaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria requerida");
        }
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria invalida"));

        boolean tieneVariantes = request.variantes() != null && !request.variantes().isEmpty();
        boolean tienePrecio = request.precio() != null;
        if (tieneVariantes && tienePrecio) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "no se puede fijar precio y variantes de tamano a la vez");
        }
        if (!tieneVariantes && !tienePrecio) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "precio o variantes requerido");
        }
        if (tienePrecio) {
            validarPrecio(request.precio());
        }
        if (tieneVariantes) {
            validarVariantes(request.variantes());
        }

        Set<UUID> modificadorIds = request.modificadorIds() == null ? Set.of() : request.modificadorIds();
        Set<Modificador> modificadores = new HashSet<>(modificadorRepository.findAllById(modificadorIds));
        if (modificadores.size() != modificadorIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modificador invalido");
        }

        Platillo platillo = new Platillo();
        platillo.setNombre(request.nombre());
        platillo.setDescripcion(request.descripcion());
        platillo.setPrecio(tieneVariantes ? null : request.precio());
        platillo.setCategoria(categoria);
        platillo.setEstacion(request.estacion());
        platillo.setModificadores(modificadores);
        platillo.setActivo(true);
        platilloRepository.save(platillo);

        if (tieneVariantes) {
            guardarVariantes(platillo, request.variantes());
        }

        return toResponse(platillo);
    }

    @GetMapping
    public List<PlatilloResponse> listarPlatillos() {
        return platilloRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @PatchMapping("/{id}/activo")
    public PlatilloResponse cambiarActivo(@PathVariable UUID id, @RequestBody ActivarPlatilloRequest request) {
        if (request.activo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "activo es requerido");
        }
        Platillo platillo = platilloRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        platillo.setActivo(request.activo());
        platilloRepository.save(platillo);
        return toResponse(platillo);
    }

    @PatchMapping("/{id}")
    public PlatilloResponse editarPlatillo(@PathVariable UUID id, @RequestBody EditarPlatilloRequest request) {
        Platillo platillo = platilloRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (request.nombre() != null && !request.nombre().isBlank()) {
            platillo.setNombre(request.nombre());
        }
        if (request.descripcion() != null) {
            platillo.setDescripcion(request.descripcion());
        }
        if (request.categoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(request.categoriaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria invalida"));
            platillo.setCategoria(categoria);
        }
        if (request.estacion() != null) {
            platillo.setEstacion(request.estacion());
        }
        if (request.modificadorIds() != null) {
            Set<Modificador> modificadores = new HashSet<>(modificadorRepository.findAllById(request.modificadorIds()));
            if (modificadores.size() != request.modificadorIds().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modificador invalido");
            }
            platillo.setModificadores(modificadores);
        }

        boolean variantesEnviadas = request.variantes() != null;
        boolean variantesVacias = variantesEnviadas && request.variantes().isEmpty();
        boolean variantesNuevas = variantesEnviadas && !request.variantes().isEmpty();

        if (request.precio() != null && variantesNuevas) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "no se puede fijar precio y variantes de tamano a la vez");
        }

        if (request.precio() != null) {
            boolean tieneVariantesActuales = !varianteRepository.findByPlatilloIdOrderByOrdenIndexAsc(id).isEmpty();
            if (tieneVariantesActuales && !variantesVacias) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "el platillo usa variantes; incluye variantes: [] para quitarlas antes de fijar un precio");
            }
            validarPrecio(request.precio());
            platillo.setPrecio(request.precio());
        }

        if (variantesVacias) {
            if (request.precio() == null && platillo.getPrecio() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "se requiere un precio al quitar las variantes");
            }
            varianteRepository.deleteByPlatilloId(id);
        }

        if (variantesNuevas) {
            validarVariantes(request.variantes());
            platillo.setPrecio(null);
            varianteRepository.deleteByPlatilloId(id);
            platilloRepository.save(platillo);
            guardarVariantes(platillo, request.variantes());
        }

        platilloRepository.save(platillo);
        return toResponse(platillo);
    }

    private void validarPrecio(BigDecimal precio) {
        if (precio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "precio debe ser positivo");
        }
    }

    private void validarVariantes(List<VarianteRequest> variantes) {
        if (variantes.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "se requieren al menos 2 variantes");
        }
        Set<String> nombresVistos = new HashSet<>();
        for (VarianteRequest variante : variantes) {
            if (variante.nombre() == null || variante.nombre().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre de variante requerido");
            }
            if (variante.precio() == null || variante.precio().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "precio de variante debe ser positivo");
            }
            if (!nombresVistos.add(variante.nombre().trim().toLowerCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombres de variante duplicados");
            }
        }
    }

    private void guardarVariantes(Platillo platillo, List<VarianteRequest> variantes) {
        int indice = 0;
        for (VarianteRequest variante : variantes) {
            Variante nueva = new Variante();
            nueva.setPlatillo(platillo);
            nueva.setNombre(variante.nombre());
            nueva.setPrecio(variante.precio());
            nueva.setOrdenIndex(indice++);
            varianteRepository.save(nueva);
        }
    }

    private PlatilloResponse toResponse(Platillo platillo) {
        Set<UUID> modificadorIds = platillo.getModificadores().stream()
                .map(Modificador::getId)
                .collect(Collectors.toSet());
        List<VarianteResponse> variantes = varianteRepository.findByPlatilloIdOrderByOrdenIndexAsc(platillo.getId())
                .stream()
                .map(v -> new VarianteResponse(v.getId(), v.getNombre(), v.getPrecio()))
                .collect(Collectors.toCollection(ArrayList::new));
        return new PlatilloResponse(platillo.getId(), platillo.getNombre(), platillo.getDescripcion(),
                platillo.getPrecio(), platillo.getCategoria().getId(), platillo.getCategoria().getNombre(),
                platillo.getEstacion(), modificadorIds, platillo.isActivo(), variantes);
    }

}
