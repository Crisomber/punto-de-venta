package com.posrestaurante.backend.orden;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.platillo.Platillo;
import com.posrestaurante.backend.platillo.PlatilloRepository;
import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
import com.posrestaurante.backend.ticket.TicketRepository;
import com.posrestaurante.backend.usuario.Estacion;
import com.posrestaurante.backend.usuario.Rol;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnviarOrdenControllerTest extends AbstractIntegrationTest {

    @Autowired
    OrdenRepository ordenRepository;

    @Autowired
    OrdenItemRepository ordenItemRepository;

    @Autowired
    TicketRepository ticketRepository;

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

    private Categoria unaCategoria() {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        return categoriaRepository.save(categoria);
    }

    private Platillo unPlatillo(Estacion estacion) {
        Platillo platillo = new Platillo();
        platillo.setNombre("Item");
        platillo.setPrecio(new BigDecimal("50.00"));
        platillo.setCategoria(unaCategoria());
        platillo.setEstacion(estacion);
        platillo.setActivo(true);
        return platilloRepository.save(platillo);
    }

    private Orden unaOrdenAbiertaConItems(Platillo... platillos) {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.setFechaApertura(Instant.now());
        ordenRepository.save(orden);

        for (Platillo platillo : platillos) {
            OrdenItem item = new OrdenItem();
            item.setOrden(orden);
            item.setPlatillo(platillo);
            item.setCantidad(1);
            item.setPrecioUnitario(platillo.getPrecio());
            item.setEstado(EstadoOrdenItem.PENDIENTE);
            ordenItemRepository.save(item);
        }
        return orden;
    }

    @Test
    void sendingOrderGroupsItemsIntoOneTicketPerEstacion() throws Exception {
        Orden orden = unaOrdenAbiertaConItems(unPlatillo(Estacion.P1), unPlatillo(Estacion.P2));

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/enviar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADA"));

        assertThat(ticketRepository.findAll()).hasSize(2);
        assertThat(ordenItemRepository.findByOrden(orden))
                .allSatisfy(item -> assertThat(item.getTicket()).isNotNull());
    }

    @Test
    void sendingOrderWithOnlyOneEstacionCreatesOneTicket() throws Exception {
        Orden orden = unaOrdenAbiertaConItems(unPlatillo(Estacion.P2), unPlatillo(Estacion.P2));

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/enviar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isOk());

        assertThat(ticketRepository.findAll()).hasSize(1);
    }

    @Test
    void sendingAlreadySentOrderFails() throws Exception {
        Orden orden = unaOrdenAbiertaConItems(unPlatillo(Estacion.P1));
        orden.setEstado(EstadoOrden.ENVIADA);
        ordenRepository.save(orden);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/enviar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isConflict());
    }

    @Test
    void sendingOrderWithNoPendingItemsFails() throws Exception {
        Orden orden = unaOrdenAbiertaConItems();

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/enviar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrdenIdReturns404() throws Exception {
        mockMvc.perform(post("/api/ordenes/" + UUID.randomUUID() + "/enviar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cajeroCanSendOrderForVentaDirecta() throws Exception {
        Orden orden = unaOrdenAbiertaConItems(unPlatillo(Estacion.P1));

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/enviar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CAJERO)))
                .andExpect(status().isOk());
    }

    @Test
    void nonMeseroCannotSendOrder() throws Exception {
        Orden orden = unaOrdenAbiertaConItems(unPlatillo(Estacion.P1));

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/enviar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotSendOrder() throws Exception {
        Orden orden = unaOrdenAbiertaConItems(unPlatillo(Estacion.P1));

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/enviar"))
                .andExpect(status().isUnauthorized());
    }

}
