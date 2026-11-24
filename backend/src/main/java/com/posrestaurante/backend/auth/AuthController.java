package com.posrestaurante.backend.auth;

import com.posrestaurante.backend.asistencia.RegistroAsistencia;
import com.posrestaurante.backend.asistencia.RegistroAsistenciaRepository;
import com.posrestaurante.backend.asistencia.TipoAsistencia;
import com.posrestaurante.backend.auth.dto.AuthResponse;
import com.posrestaurante.backend.auth.dto.CambiarPasswordRequest;
import com.posrestaurante.backend.auth.dto.LoginRequest;
import com.posrestaurante.backend.auth.dto.MeResponse;
import com.posrestaurante.backend.auth.dto.PinLoginRequest;
import com.posrestaurante.backend.auth.dto.StaffListItem;
import com.posrestaurante.backend.security.AuthenticatedUser;
import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.usuario.Rol;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final RegistroAsistenciaRepository registroAsistenciaRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarioRepository,
                           RegistroAsistenciaRepository registroAsistenciaRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.registroAsistenciaRepository = registroAsistenciaRepository;
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

    @GetMapping("/staff")
    public List<StaffListItem> getStaff() {
        return usuarioRepository.findByActivoTrueAndRolNot(Rol.DUENO).stream()
                .map(u -> new StaffListItem(u.getId(), u.getNombre(), u.getRol()))
                .toList();
    }

    @PostMapping("/login/pin")
    public AuthResponse loginPin(@RequestBody PinLoginRequest request) {
        Usuario usuario = usuarioRepository.findById(request.usuarioId())
                .orElseThrow(this::credencialesInvalidas);

        if (!usuario.isActivo() || !passwordEncoder.matches(request.pin(), usuario.getPinHash())) {
            throw credencialesInvalidas();
        }

        registroAsistenciaRepository.save(
                new RegistroAsistencia(usuario, Instant.now(), TipoAsistencia.ENTRADA));

        String token = jwtService.generateToken(usuario.getId(), usuario.getNombre(), usuario.getRol());
        return new AuthResponse(token, usuario.getNombre(), usuario.getRol());
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        AuthenticatedUser usuario = (AuthenticatedUser) authentication.getPrincipal();
        return new MeResponse(usuario.usuarioId(), usuario.nombre(), usuario.rol());
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(@RequestBody CambiarPasswordRequest request, Authentication authentication) {
        AuthenticatedUser caller = (AuthenticatedUser) authentication.getPrincipal();
        Usuario usuario = usuarioRepository.findById(caller.usuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "contrasena actual incorrecta");
        }
        if (request.passwordNueva() == null || request.passwordNueva().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contrasena nueva requerida");
        }

        usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
        usuarioRepository.save(usuario);
    }

    private ResponseStatusException credencialesInvalidas() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "credenciales invalidas");
    }

}
