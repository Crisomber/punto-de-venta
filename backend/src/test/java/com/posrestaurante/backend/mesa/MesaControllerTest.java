package com.posrestaurante.backend.mesa;

import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.usuario.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MesaControllerTest extends AbstractIntegrationTest {

    @Autowired
    MesaRepository mesaRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void duenoCreatesMesaSuccessfully() throws Exception {
        mockMvc.perform(post("/api/mesas")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"numero\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(5))
                .andExpect(jsonPath("$.estado").value("LIBRE"));
    }

    @Test
    void creatingMesaWithDuplicateNumeroFails() throws Exception {
        mockMvc.perform(post("/api/mesas")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"numero\":7}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/mesas")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"numero\":7}"))
                .andExpect(status().isConflict());
    }

    @Test
    void creatingMesaWithInvalidNumeroFails() throws Exception {
        mockMvc.perform(post("/api/mesas")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"numero\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anyAuthenticatedRoleCanListMesas() throws Exception {
        Mesa mesa = new Mesa();
        mesa.setNumero(3);
        mesa.setEstado(EstadoMesa.LIBRE);
        mesaRepository.save(mesa);

        mockMvc.perform(get("/api/mesas").header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numero == 3)]").exists());
    }

    @Test
    void duenoDeletesMesa() throws Exception {
        Mesa mesa = new Mesa();
        mesa.setNumero(9);
        mesa.setEstado(EstadoMesa.LIBRE);
        mesaRepository.save(mesa);

        mockMvc.perform(delete("/api/mesas/" + mesa.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/mesas").header("Authorization", "Bearer " + tokenFor(Rol.DUENO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numero == 9)]").doesNotExist());
    }

    @Test
    void deletingUnknownMesaReturns404() throws Exception {
        mockMvc.perform(delete("/api/mesas/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonDuenoCannotCreateMesa() throws Exception {
        mockMvc.perform(post("/api/mesas")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CAJERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"numero\":9}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotListMesas() throws Exception {
        mockMvc.perform(get("/api/mesas")).andExpect(status().isUnauthorized());
    }

}
