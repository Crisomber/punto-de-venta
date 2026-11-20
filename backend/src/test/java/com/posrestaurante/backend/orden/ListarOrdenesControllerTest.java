package com.posrestaurante.backend.orden;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.platillo.Platillo;
import com.posrestaurante.backend.platillo.PlatilloRepository;
import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
import com.posrestaurante.backend.usuario.Estacion;
import com.posrestaurante.backend.usuario.Rol;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListarOrdenesControllerTest extends AbstractIntegrationTest {

    @Autowired
    OrdenRepository ordenRepository;

    @Autowired
    OrdenItemRepository ordenItemRepository;

    @Autowired
    PlatilloRepository platilloRepository;

    @Autowired
    CategoriaRepository categoriaRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private Orden unaOrdenConUnItem() {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.setFechaApertura(Instant.now());
        ordenRepository.save(orden);

        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);
        Platillo platillo = new Platillo();
        platillo.setNombre("Tacos");
        platillo.setPrecio(new BigDecimal("85.00"));
        platillo.setCategoria(categoria);
        platillo.setEstacion(Estacion.P2);
        platillo.setActivo(true);
        platilloRepository.save(platillo);

        OrdenItem item = new OrdenItem();
        item.setOrden(orden);
        item.setPlatillo(platillo);
        item.setCantidad(3);
        item.setPrecioUnitario(platillo.getPrecio());
        item.setEstado(EstadoOrdenItem.PENDIENTE);
        ordenItemRepository.save(item);

        return orden;
    }

    @Test
    void anyAuthenticatedRoleCanListOrdenesWithNestedItems() throws Exception {
        unaOrdenConUnItem();

        mockMvc.perform(get("/api/ordenes").header("Authorization", "Bearer " + tokenFor(Rol.CHEF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.items[0].platilloNombre == 'Tacos')]").exists());
    }

    @Test
    void anyAuthenticatedRoleCanGetOrdenById() throws Exception {
        Orden orden = unaOrdenConUnItem();

        mockMvc.perform(get("/api/ordenes/" + orden.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.CAJERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].cantidad").value(3));
    }

    @Test
    void gettingUnknownOrdenReturns404() throws Exception {
        mockMvc.perform(get("/api/ordenes/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedCannotListOrdenes() throws Exception {
        mockMvc.perform(get("/api/ordenes")).andExpect(status().isUnauthorized());
    }

}
