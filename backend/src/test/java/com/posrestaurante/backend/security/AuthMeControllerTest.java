package com.posrestaurante.backend.security;

import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
import com.posrestaurante.backend.usuario.Rol;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthMeControllerTest extends AbstractIntegrationTest {

    @Autowired
    JwtService jwtService;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    Environment environment;

    @Test
    void meReturns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCallerInfoWithValidToken() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        String token = jwtService.generateToken(mesero.getId(), "Luis", Rol.MESERO);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Luis"))
                .andExpect(jsonPath("$.rol").value("MESERO"));
    }

    @Test
    void meReturns401WithMalformedToken() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturns401WithExpiredToken() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(environment.getProperty("jwt.secret").getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String expiredToken = Jwts.builder()
                .claim("usuarioId", UUID.randomUUID().toString())
                .claim("nombre", "Expirado")
                .claim("rol", "MESERO")
                .issuedAt(Date.from(now.minus(10, ChronoUnit.MINUTES)))
                .expiration(Date.from(now.minus(5, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

}
