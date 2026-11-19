package com.posrestaurante.backend.auth;

import com.posrestaurante.backend.auth.dto.AuthResponse;
import com.posrestaurante.backend.auth.dto.LoginRequest;
import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login/dueno")
    public AuthResponse loginDueno(@RequestBody LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsernameAndActivoTrue(request.username())
                .orElseThrow(this::credencialesInvalidas);

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw credencialesInvalidas();
        }

        String token = jwtService.generateToken(usuario.getId(), usuario.getNombre(), usuario.getRol());
        return new AuthResponse(token, usuario.getNombre(), usuario.getRol());
    }

    private ResponseStatusException credencialesInvalidas() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "credenciales invalidas");
    }

}
