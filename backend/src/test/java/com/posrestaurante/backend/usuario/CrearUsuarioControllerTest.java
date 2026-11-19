package com.posrestaurante.backend.usuario;

import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CrearUsuarioControllerTest extends AbstractIntegrationTest {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void duenoCreatesMeseroSuccessfully() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Luis","rol":"MESERO","pin":"4821"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Luis"))
                .andExpect(jsonPath("$.rol").value("MESERO"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.pin").doesNotExist())
                .andExpect(jsonPath("$.pinHash").doesNotExist());

        assertThat(usuarioRepository.findAll())
                .anySatisfy(u -> assertThat(u.getNombre()).isEqualTo("Luis"));
    }

    @Test
    void creatingChefRequiresEstacion() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Mario","rol":"CHEF","pin":"1234"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingChefWithEstacionSucceeds() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Mario","rol":"CHEF","estacion":"P2","pin":"1234"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estacion").value("P2"));
    }

    @Test
    void nonChefWithEstacionIsRejected() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Luis","rol":"MESERO","estacion":"P1","pin":"4821"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotCreateAnotherDueno() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Otro","rol":"DUENO","pin":"4821"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pinMustBeFourDigits() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Luis","rol":"MESERO","pin":"12"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonDuenoCannotCreateUsuarios() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Luis","rol":"MESERO","pin":"4821"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotCreateUsuarios() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Luis","rol":"MESERO","pin":"4821"}"""))
                .andExpect(status().isUnauthorized());
    }

}
