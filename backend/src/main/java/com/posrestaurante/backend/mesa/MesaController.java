package com.posrestaurante.backend.mesa;

import com.posrestaurante.backend.mesa.dto.CrearMesaRequest;
import com.posrestaurante.backend.mesa.dto.MesaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mesas")
public class MesaController {

    private final MesaRepository mesaRepository;

    public MesaController(MesaRepository mesaRepository) {
        this.mesaRepository = mesaRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MesaResponse crearMesa(@RequestBody CrearMesaRequest request) {
        if (request.numero() == null || request.numero() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numero invalido");
        }
        if (mesaRepository.findByNumero(request.numero()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ya existe una mesa con ese numero");
        }

        Mesa mesa = new Mesa();
        mesa.setNumero(request.numero());
        mesa.setEstado(EstadoMesa.LIBRE);
        mesaRepository.save(mesa);

        return toResponse(mesa);
    }

    @GetMapping
    public List<MesaResponse> listarMesas() {
        return mesaRepository.findAll().stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarMesa(@PathVariable UUID id) {
        if (!mesaRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        mesaRepository.deleteById(id);
    }

    private MesaResponse toResponse(Mesa mesa) {
        return new MesaResponse(mesa.getId(), mesa.getNumero(), mesa.getEstado());
    }

}
