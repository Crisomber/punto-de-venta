package com.posrestaurante.backend.categoria;

import com.posrestaurante.backend.categoria.dto.CategoriaResponse;
import com.posrestaurante.backend.categoria.dto.CrearCategoriaRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;

    public CategoriaController(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse crearCategoria(@RequestBody CrearCategoriaRequest request) {
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre requerido");
        }
        Categoria categoria = new Categoria();
        categoria.setNombre(request.nombre());
        categoriaRepository.save(categoria);
        return toResponse(categoria);
    }

    @GetMapping
    public List<CategoriaResponse> listarCategorias() {
        return categoriaRepository.findAll().stream().map(this::toResponse).toList();
    }

    private CategoriaResponse toResponse(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNombre());
    }

}
