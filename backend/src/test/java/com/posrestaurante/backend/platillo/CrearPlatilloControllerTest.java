package com.posrestaurante.backend.platillo;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.modificador.ModificadorRepository;
import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.usuario.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CrearPlatilloControllerTest extends AbstractIntegrationTest {

    @Autowired
    PlatilloRepository platilloRepository;

    @Autowired
    CategoriaRepository categoriaRepository;

    @Autowired
    ModificadorRepository modificadorRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private Categoria unaCategoria() {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        return categoriaRepository.save(categoria);
    }

    @Test
    void duenoCreatesPlatilloSuccessfully() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Tacos al pastor","precio":85.00,"categoriaId":"%s","estacion":"P2"}"""
                                .formatted(categoria.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Tacos al pastor"))
                .andExpect(jsonPath("$.categoriaNombre").value("Entradas"))
                .andExpect(jsonPath("$.estacion").value("P2"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void duenoCreatesPlatilloWithModificadores() throws Exception {
        Categoria categoria = unaCategoria();
        Modificador sinCebolla = new Modificador();
        sinCebolla.setNombre("Sin cebolla");
        modificadorRepository.save(sinCebolla);
        Modificador sinCilantro = new Modificador();
        sinCilantro.setNombre("Sin cilantro");
        modificadorRepository.save(sinCilantro);

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Tacos","precio":85.00,"categoriaId":"%s","estacion":"P2","modificadorIds":["%s","%s"]}"""
                                .formatted(categoria.getId(), sinCebolla.getId(), sinCilantro.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modificadorIds.length()").value(2));
    }

    @Test
    void creatingPlatilloWithUnknownCategoriaFails() throws Exception {
        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Tacos","precio":85.00,"categoriaId":"%s","estacion":"P2"}"""
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingPlatilloWithUnknownModificadorFails() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Tacos","precio":85.00,"categoriaId":"%s","estacion":"P2","modificadorIds":["%s"]}"""
                                .formatted(categoria.getId(), UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingPlatilloWithNonPositivePrecioFails() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Tacos","precio":0,"categoriaId":"%s","estacion":"P2"}"""
                                .formatted(categoria.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingPlatilloWithoutEstacionFails() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Tacos","precio":85.00,"categoriaId":"%s"}"""
                                .formatted(categoria.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonDuenoCannotCreatePlatillo() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CAJERO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Tacos","precio":85.00,"categoriaId":"%s","estacion":"P2"}"""
                                .formatted(categoria.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotCreatePlatillo() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Tacos","precio":85.00,"categoriaId":"%s","estacion":"P2"}"""
                                .formatted(categoria.getId())))
                .andExpect(status().isUnauthorized());
    }

}
