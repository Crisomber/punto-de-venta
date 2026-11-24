package com.posrestaurante.backend.categoria;

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

class CategoriaControllerTest extends AbstractIntegrationTest {

    @Autowired
    CategoriaRepository categoriaRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void duenoCreatesCategoriaSuccessfully() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Entradas\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Entradas"));
    }

    @Test
    void creatingCategoriaWithBlankNombreFails() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anyAuthenticatedRoleCanListCategorias() throws Exception {
        Categoria categoria = new Categoria();
        categoria.setNombre("Bebidas");
        categoriaRepository.save(categoria);

        mockMvc.perform(get("/api/categorias").header("Authorization", "Bearer " + tokenFor(Rol.CHEF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Bebidas')]").exists());
    }

    @Test
    void nonDuenoCannotCreateCategoria() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Postres\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotCreateCategoria() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Postres\"}"))
                .andExpect(status().isUnauthorized());
    }

}
