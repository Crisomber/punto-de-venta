package com.posrestaurante.backend.usuario;

import com.posrestaurante.backend.usuario.dto.CrearUsuarioRequest;
import com.posrestaurante.backend.usuario.dto.UsuarioResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioAdminController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioAdminController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crearUsuario(@RequestBody CrearUsuarioRequest request) {
        if (request.rol() == null || request.rol() == Rol.DUENO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rol invalido");
        }
        boolean esChef = request.rol() == Rol.CHEF;
        if (esChef && request.estacion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estacion requerida para CHEF");
        }
        if (!esChef && request.estacion() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estacion solo aplica a CHEF");
        }
        if (request.pin() == null || !request.pin().matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pin debe ser de 4 digitos");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre());
        usuario.setRol(request.rol());
        usuario.setEstacion(esChef ? request.estacion() : null);
        usuario.setPinHash(passwordEncoder.encode(request.pin()));
        usuario.setActivo(true);
        usuarioRepository.save(usuario);

        return toResponse(usuario);
    }

    @GetMapping
    public List<UsuarioResponse> listarUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getRol(),
                usuario.getEstacion(), usuario.getUsername(), usuario.isActivo());
    }

}
