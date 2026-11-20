package com.posrestaurante.backend.orden;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.modificador.ModificadorRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgregarItemsControllerTest extends AbstractIntegrationTest {

    @Autowired
    OrdenRepository ordenRepository;

    @Autowired
    OrdenItemRepository ordenItemRepository;

    @Autowired
    PlatilloRepository platilloRepository;

    @Autowired
    CategoriaRepository categoriaRepository;

    @Autowired
    ModificadorRepository modificadorRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private Orden unaOrdenAbierta() {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.setFechaApertura(Instant.now());
        return ordenRepository.save(orden);
    }

    private Platillo unPlatillo(boolean activo, Modificador... modificadoresPermitidos) {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);

        Platillo platillo = new Platillo();
        platillo.setNombre("Tacos");
        platillo.setPrecio(new BigDecimal("85.00"));
        platillo.setCategoria(categoria);
        platillo.setEstacion(Estacion.P2);
        platillo.setActivo(activo);
        platillo.setModificadores(new HashSet<>(java.util.List.of(modificadoresPermitidos)));
        return platilloRepository.save(platillo);
    }

    @Test
    void meseroAddsItemToOpenOrder() throws Exception {
        Orden orden = unaOrdenAbierta();
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].platilloNombre").value("Tacos"))
                .andExpect(jsonPath("$.items[0].cantidad").value(2))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(85.00))
                .andExpect(jsonPath("$.items[0].estado").value("PENDIENTE"));

        assertThat(ordenItemRepository.findByOrden(orden)).hasSize(1);
    }

    @Test
    void addingModificadorNotAllowedForThatPlatilloFails() throws Exception {
        Orden orden = unaOrdenAbierta();
        Modificador sinCebolla = new Modificador();
        sinCebolla.setNombre("Sin cebolla");
        modificadorRepository.save(sinCebolla);
        Platillo platillo = unPlatillo(true); // no allowed modificadores

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId()
                                + "\",\"cantidad\":1,\"modificadorIds\":[\"" + sinCebolla.getId() + "\"]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addingInactivePlatilloFails() throws Exception {
        Orden orden = unaOrdenAbierta();
        Platillo platillo = unPlatillo(false);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addingToAClosedOrderFails() throws Exception {
        Orden orden = unaOrdenAbierta();
        orden.setEstado(EstadoOrden.CERRADA);
        ordenRepository.save(orden);
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":1}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownOrdenIdReturns404() throws Exception {
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(post("/api/ordenes/" + UUID.randomUUID() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":1}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidCantidadFails() throws Exception {
        Orden orden = unaOrdenAbierta();
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":0}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonMeseroCannotAddItems() throws Exception {
        Orden orden = unaOrdenAbierta();
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CHEF))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":1}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAddItems() throws Exception {
        Orden orden = unaOrdenAbierta();
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":1}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addingItemToSentOrderImmediatelyCreatesNewTicketForItsEstacion() throws Exception {
        Orden orden = unaOrdenAbierta();
        orden.setEstado(EstadoOrden.ENVIADA);
        ordenRepository.save(orden);
        Platillo platillo = unPlatillo(true); // estacion P2 per the helper's default

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":1}]}"))
                .andExpect(status().isOk());

        List<OrdenItem> items = ordenItemRepository.findByOrden(orden);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getTicket()).isNotNull();
        assertThat(items.get(0).getTicket().getEstacion()).isEqualTo(Estacion.P2);
    }

    @Test
    void addingItemsOfDifferentEstacionesToSentOrderCreatesTwoNewTickets() throws Exception {
        Orden orden = unaOrdenAbierta();
        orden.setEstado(EstadoOrden.ENVIADA);
        ordenRepository.save(orden);
        Platillo p1 = unPlatillo(true);
        p1.setEstacion(Estacion.P1);
        platilloRepository.save(p1);
        Platillo p2 = unPlatillo(true);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + p1.getId() + "\",\"cantidad\":1},"
                                + "{\"platilloId\":\"" + p2.getId() + "\",\"cantidad\":1}]}"))
                .andExpect(status().isOk());

        assertThat(ticketRepository.findAll()).hasSize(2);
    }

    @Test
    void addingItemToOpenOrderStillDoesNotCreateATicket() throws Exception {
        // regression: Task 2's core behavior on an ABIERTA orden must still hold
        Orden orden = unaOrdenAbierta();
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":1}]}"))
                .andExpect(status().isOk());

        assertThat(ticketRepository.findAll()).isEmpty();
    }

}
