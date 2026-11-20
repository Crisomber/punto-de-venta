package com.posrestaurante.backend.modificador;

import com.posrestaurante.backend.modificador.dto.CrearModificadorRequest;
import com.posrestaurante.backend.modificador.dto.ModificadorResponse;
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
@RequestMapping("/api/modificadores")
public class ModificadorController {

    private final ModificadorRepository modificadorRepository;

    public ModificadorController(ModificadorRepository modificadorRepository) {
        this.modificadorRepository = modificadorRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModificadorResponse crearModificador(@RequestBody CrearModificadorRequest request) {
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre requerido");
        }
        Modificador modificador = new Modificador();
        modificador.setNombre(request.nombre());
        modificadorRepository.save(modificador);
        return toResponse(modificador);
    }

    @GetMapping
    public List<ModificadorResponse> listarModificadores() {
        return modificadorRepository.findAll().stream().map(this::toResponse).toList();
    }

    private ModificadorResponse toResponse(Modificador modificador) {
        return new ModificadorResponse(modificador.getId(), modificador.getNombre());
    }

}
