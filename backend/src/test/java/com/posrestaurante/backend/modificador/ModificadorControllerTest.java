package com.posrestaurante.backend.modificador;

import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.usuario.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModificadorControllerTest extends AbstractIntegrationTest {

    @Autowired
    ModificadorRepository modificadorRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void duenoCreatesModificadorSuccessfully() throws Exception {
        mockMvc.perform(post("/api/modificadores")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Sin cebolla\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Sin cebolla"));
    }

    @Test
    void creatingModificadorWithBlankNombreFails() throws Exception {
        mockMvc.perform(post("/api/modificadores")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anyAuthenticatedRoleCanListModificadores() throws Exception {
        Modificador modificador = new Modificador();
        modificador.setNombre("Sin cilantro");
        modificadorRepository.save(modificador);

        mockMvc.perform(get("/api/modificadores").header("Authorization", "Bearer " + tokenFor(Rol.CAJERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Sin cilantro')]").exists());
    }

    @Test
    void nonDuenoCannotCreateModificador() throws Exception {
        mockMvc.perform(post("/api/modificadores")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CHEF))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Extra queso\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotCreateModificador() throws Exception {
        mockMvc.perform(post("/api/modificadores")
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Extra queso\"}"))
                .andExpect(status().isUnauthorized());
    }

}
