package com.posrestaurante.backend.orden;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.platillo.Platillo;
import com.posrestaurante.backend.platillo.PlatilloRepository;
import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
import com.posrestaurante.backend.ticket.Ticket;
import com.posrestaurante.backend.ticket.TicketEstado;
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

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CambiarEstadoItemControllerTest extends AbstractIntegrationTest {

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

    private String chefToken(Estacion estacion) {
        Usuario chef = TestUsuarios.staff("Chef", Rol.CHEF, "hash", true);
        chef.setEstacion(estacion);
        usuarioRepository.save(chef);
        return jwtService.generateToken(chef.getId(), "Chef", Rol.CHEF);
    }

    private Orden unaOrdenEnviada() {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
        orden.setEstado(EstadoOrden.ENVIADA);
        orden.setFechaApertura(Instant.now());
        return ordenRepository.save(orden);
    }

    private Platillo unPlatillo(Estacion estacion) {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);
        Platillo platillo = new Platillo();
        platillo.setNombre("Tacos");
        platillo.setPrecio(new BigDecimal("85.00"));
        platillo.setCategoria(categoria);
        platillo.setEstacion(estacion);
        platillo.setActivo(true);
        return platilloRepository.save(platillo);
    }

    private OrdenItem unItemConTicket(Orden orden, Estacion estacion) {
        Platillo platillo = unPlatillo(estacion);
        Ticket ticket = new Ticket();
        ticket.setOrden(orden);
        ticket.setEstacion(estacion);
        ticket.setFechaGenerado(Instant.now());
        ticket.setEstado(TicketEstado.PENDIENTE);
        ticketRepository.save(ticket);

        OrdenItem item = new OrdenItem();
        item.setOrden(orden);
        item.setPlatillo(platillo);
        item.setTicket(ticket);
        item.setCantidad(1);
        item.setPrecioUnitario(platillo.getPrecio());
        item.setEstado(EstadoOrdenItem.PENDIENTE);
        return ordenItemRepository.save(item);
    }

    @Test
    void chefMarksOwnStationItemAsEnPreparacion() throws Exception {
        OrdenItem item = unItemConTicket(unaOrdenEnviada(), Estacion.P2);

        mockMvc.perform(patch("/api/ordenes/items/" + item.getId() + "/estado")
                        .header("Authorization", "Bearer " + chefToken(Estacion.P2))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"EN_PREPARACION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PREPARACION"));
    }

    @Test
    void chefMarksOwnStationItemAsListo() throws Exception {
        OrdenItem item = unItemConTicket(unaOrdenEnviada(), Estacion.P2);

        mockMvc.perform(patch("/api/ordenes/items/" + item.getId() + "/estado")
                        .header("Authorization", "Bearer " + chefToken(Estacion.P2))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"LISTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO"));
    }

    @Test
    void chefFromP1CannotMarkEstadoAtAll() throws Exception {
        OrdenItem item = unItemConTicket(unaOrdenEnviada(), Estacion.P1);

        mockMvc.perform(patch("/api/ordenes/items/" + item.getId() + "/estado")
                        .header("Authorization", "Bearer " + chefToken(Estacion.P1))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"LISTO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void chefCannotMarkOtherStationItem() throws Exception {
        OrdenItem item = unItemConTicket(unaOrdenEnviada(), Estacion.P1);

        mockMvc.perform(patch("/api/ordenes/items/" + item.getId() + "/estado")
                        .header("Authorization", "Bearer " + chefToken(Estacion.P2))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"LISTO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void markingUnsentItemFails() throws Exception {
        Orden orden = unaOrdenEnviada();
        Platillo platillo = unPlatillo(Estacion.P2);
        OrdenItem item = new OrdenItem();
        item.setOrden(orden);
        item.setPlatillo(platillo);
        item.setCantidad(1);
        item.setPrecioUnitario(platillo.getPrecio());
        item.setEstado(EstadoOrdenItem.PENDIENTE);
        ordenItemRepository.save(item); // sin ticket

        mockMvc.perform(patch("/api/ordenes/items/" + item.getId() + "/estado")
                        .header("Authorization", "Bearer " + chefToken(Estacion.P2))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"EN_PREPARACION\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidEstadoValueFails() throws Exception {
        OrdenItem item = unItemConTicket(unaOrdenEnviada(), Estacion.P2);

        mockMvc.perform(patch("/api/ordenes/items/" + item.getId() + "/estado")
                        .header("Authorization", "Bearer " + chefToken(Estacion.P2))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownItemIdReturns404() throws Exception {
        mockMvc.perform(patch("/api/ordenes/items/" + UUID.randomUUID() + "/estado")
                        .header("Authorization", "Bearer " + chefToken(Estacion.P2))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"LISTO\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonChefCannotMarkEstado() throws Exception {
        OrdenItem item = unItemConTicket(unaOrdenEnviada(), Estacion.P2);

        mockMvc.perform(patch("/api/ordenes/items/" + item.getId() + "/estado")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"LISTO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotMarkEstado() throws Exception {
        OrdenItem item = unItemConTicket(unaOrdenEnviada(), Estacion.P2);

        mockMvc.perform(patch("/api/ordenes/items/" + item.getId() + "/estado")
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"LISTO\"}"))
                .andExpect(status().isUnauthorized());
    }

}
