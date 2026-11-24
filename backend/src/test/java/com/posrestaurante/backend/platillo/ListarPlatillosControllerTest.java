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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListarPlatillosControllerTest extends AbstractIntegrationTest {

    @Autowired
    PlatilloRepository platilloRepository;

    @Autowired
    CategoriaRepository categoriaRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void anyAuthenticatedRoleCanListPlatillosIncludingInactive() throws Exception {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);

        Platillo activo = new Platillo();
        activo.setNombre("Tacos");
        activo.setPrecio(new BigDecimal("85.00"));
        activo.setCategoria(categoria);
        activo.setEstacion(Estacion.P2);
        activo.setActivo(true);
        platilloRepository.save(activo);

        Platillo inactivo = new Platillo();
        inactivo.setNombre("Descontinuado");
        inactivo.setPrecio(new BigDecimal("50.00"));
        inactivo.setCategoria(categoria);
        inactivo.setEstacion(Estacion.P1);
        inactivo.setActivo(false);
        platilloRepository.save(inactivo);

        mockMvc.perform(get("/api/platillos").header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Tacos')]").exists())
                .andExpect(jsonPath("$[?(@.nombre == 'Descontinuado' && @.activo == false)]").exists());
    }

    @Test
    void unauthenticatedCannotListPlatillos() throws Exception {
        mockMvc.perform(get("/api/platillos")).andExpect(status().isUnauthorized());
    }

}
