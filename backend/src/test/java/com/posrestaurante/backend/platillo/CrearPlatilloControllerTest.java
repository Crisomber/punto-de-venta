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
    void duenoCreatesPlatilloWithVariantes() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Pizza Pastor","categoriaId":"%s","estacion":"P1",
                                 "variantes":[{"nombre":"Mediana","precio":199.00},{"nombre":"Grande","precio":249.00}]}"""
                                .formatted(categoria.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precio").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.variantes.length()").value(2))
                .andExpect(jsonPath("$.variantes[0].nombre").value("Mediana"))
                .andExpect(jsonPath("$.variantes[0].precio").value(199.00))
                .andExpect(jsonPath("$.variantes[1].nombre").value("Grande"));
    }

    @Test
    void creatingPlatilloWithBothPrecioAndVariantesFails() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Pizza","precio":100.00,"categoriaId":"%s","estacion":"P1",
                                 "variantes":[{"nombre":"Mediana","precio":199.00},{"nombre":"Grande","precio":249.00}]}"""
                                .formatted(categoria.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingPlatilloWithNeitherPrecioNorVariantesFails() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Pizza","categoriaId":"%s","estacion":"P1"}"""
                                .formatted(categoria.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingPlatilloWithOnlyOneVarianteFails() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Pizza","categoriaId":"%s","estacion":"P1",
                                 "variantes":[{"nombre":"Mediana","precio":199.00}]}"""
                                .formatted(categoria.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingPlatilloWithDuplicateVarianteNombresFails() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Pizza","categoriaId":"%s","estacion":"P1",
                                 "variantes":[{"nombre":"Mediana","precio":199.00},{"nombre":"mediana","precio":249.00}]}"""
                                .formatted(categoria.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingPlatilloWithNonPositiveVariantePrecioFails() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Pizza","categoriaId":"%s","estacion":"P1",
                                 "variantes":[{"nombre":"Mediana","precio":0},{"nombre":"Grande","precio":249.00}]}"""
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
