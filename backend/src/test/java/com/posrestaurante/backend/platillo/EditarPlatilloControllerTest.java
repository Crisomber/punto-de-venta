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
    VarianteRepository varianteRepository;

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

    private Platillo unPlatilloConVariantes(Categoria categoria) {
        Platillo platillo = new Platillo();
        platillo.setNombre("Pizza Pastor");
        platillo.setPrecio(null);
        platillo.setCategoria(categoria);
        platillo.setEstacion(Estacion.P1);
        platillo.setActivo(true);
        platilloRepository.save(platillo);

        Variante mediana = new Variante();
        mediana.setPlatillo(platillo);
        mediana.setNombre("Mediana");
        mediana.setPrecio(new BigDecimal("199.00"));
        mediana.setOrdenIndex(0);
        varianteRepository.save(mediana);

        Variante grande = new Variante();
        grande.setPlatillo(platillo);
        grande.setNombre("Grande");
        grande.setPrecio(new BigDecimal("249.00"));
        grande.setOrdenIndex(1);
        varianteRepository.save(grande);

        return platillo;
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
    void duenoSwitchesPlatilloToVariantes() throws Exception {
        Platillo platillo = unPlatillo(unaCategoria("Entradas"));

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"variantes":[{"nombre":"Mediana","precio":199.00},{"nombre":"Grande","precio":249.00}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.variantes.length()").value(2));
    }

    @Test
    void duenoSwitchesPlatilloBackToPrecioFijo() throws Exception {
        Platillo platillo = unPlatilloConVariantes(unaCategoria("Entradas"));

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"variantes\":[],\"precio\":150.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(150.00))
                .andExpect(jsonPath("$.variantes.length()").value(0));
    }

    @Test
    void clearingVariantesWithoutProvidingPrecioFails() throws Exception {
        Platillo platillo = unPlatilloConVariantes(unaCategoria("Entradas"));

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"variantes\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settingPrecioWhileVariantesExistWithoutClearingFails() throws Exception {
        Platillo platillo = unPlatilloConVariantes(unaCategoria("Entradas"));

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"precio\":50.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settingBothPrecioAndNonEmptyVariantesFails() throws Exception {
        Platillo platillo = unPlatillo(unaCategoria("Entradas"));

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"precio":10.00,"variantes":[{"nombre":"A","precio":5.00},{"nombre":"B","precio":6.00}]}"""))
                .andExpect(status().isBadRequest());
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
