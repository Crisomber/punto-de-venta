package com.posrestaurante.backend.platillo;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.modificador.ModificadorRepository;
import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.usuario.Estacion;
import com.posrestaurante.backend.usuario.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EditarPlatilloControllerTest extends AbstractIntegrationTest {

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

    private Categoria unaCategoria(String nombre) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        return categoriaRepository.save(categoria);
    }

    private Platillo unPlatillo(Categoria categoria) {
        Platillo platillo = new Platillo();
        platillo.setNombre("Tacos");
        platillo.setPrecio(new BigDecimal("85.00"));
        platillo.setCategoria(categoria);
        platillo.setEstacion(Estacion.P2);
        platillo.setActivo(true);
        return platilloRepository.save(platillo);
    }

    @Test
    void duenoRenamesPlatillo() throws Exception {
        Platillo platillo = unPlatillo(unaCategoria("Entradas"));

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Tacos al pastor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Tacos al pastor"));
    }

    @Test
    void duenoChangesCategoriaAndPrecio() throws Exception {
        Categoria entradas = unaCategoria("Entradas");
        Categoria platosFuertes = unaCategoria("Platos fuertes");
        Platillo platillo = unPlatillo(entradas);

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"categoriaId\":\"" + platosFuertes.getId() + "\",\"precio\":120.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaNombre").value("Platos fuertes"))
                .andExpect(jsonPath("$.precio").value(120.00));
    }

    @Test
    void duenoReplacesModificadores() throws Exception {
        Modificador sinCebolla = new Modificador();
        sinCebolla.setNombre("Sin cebolla");
        modificadorRepository.save(sinCebolla);
        Modificador sinCilantro = new Modificador();
        sinCilantro.setNombre("Sin cilantro");
        modificadorRepository.save(sinCilantro);

        Platillo platillo = unPlatillo(unaCategoria("Entradas"));
        platillo.setModificadores(new HashSet<>(java.util.List.of(sinCebolla)));
        platilloRepository.save(platillo);

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"modificadorIds\":[\"" + sinCilantro.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modificadorIds.length()").value(1))
                .andExpect(jsonPath("$.modificadorIds[0]").value(sinCilantro.getId().toString()));
    }

    @Test
    void duenoClearsModificadoresWithEmptyList() throws Exception {
        Modificador sinCebolla = new Modificador();
        sinCebolla.setNombre("Sin cebolla");
        modificadorRepository.save(sinCebolla);

        Platillo platillo = unPlatillo(unaCategoria("Entradas"));
        platillo.setModificadores(new HashSet<>(java.util.List.of(sinCebolla)));
        platilloRepository.save(platillo);

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"modificadorIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modificadorIds.length()").value(0));
    }

    @Test
    void editingWithUnknownCategoriaFails() throws Exception {
        Platillo platillo = unPlatillo(unaCategoria("Entradas"));

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"categoriaId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editingWithNonPositivePrecioFails() throws Exception {
        Platillo platillo = unPlatillo(unaCategoria("Entradas"));

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"precio\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPlatilloIdReturns404() throws Exception {
        mockMvc.perform(patch("/api/platillos/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonDuenoCannotEditPlatillo() throws Exception {
        Platillo platillo = unPlatillo(unaCategoria("Entradas"));

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotEditPlatillo() throws Exception {
        Platillo platillo = unPlatillo(unaCategoria("Entradas"));

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

}
