package com.posrestaurante.backend.platillo;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.usuario.Estacion;
import com.posrestaurante.backend.usuario.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivarPlatilloControllerTest extends AbstractIntegrationTest {

    @Autowired
    PlatilloRepository platilloRepository;

    @Autowired
    CategoriaRepository categoriaRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private Platillo unPlatillo(boolean activo) {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);

        Platillo platillo = new Platillo();
        platillo.setNombre("Tacos");
        platillo.setPrecio(new BigDecimal("85.00"));
        platillo.setCategoria(categoria);
        platillo.setEstacion(Estacion.P2);
        platillo.setActivo(activo);
        return platilloRepository.save(platillo);
    }

    @Test
    void duenoDeactivatesPlatillo() throws Exception {
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(patch("/api/platillos/" + platillo.getId() + "/activo")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void duenoReactivatesPlatillo() throws Exception {
        Platillo platillo = unPlatillo(false);

        mockMvc.perform(patch("/api/platillos/" + platillo.getId() + "/activo")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void activoFieldIsRequired() throws Exception {
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(patch("/api/platillos/" + platillo.getId() + "/activo")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPlatilloIdReturns404() throws Exception {
        mockMvc.perform(patch("/api/platillos/" + UUID.randomUUID() + "/activo")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonDuenoCannotToggleActivo() throws Exception {
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(patch("/api/platillos/" + platillo.getId() + "/activo")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CHEF))
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotToggleActivo() throws Exception {
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(patch("/api/platillos/" + platillo.getId() + "/activo")
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isUnauthorized());
    }

}
