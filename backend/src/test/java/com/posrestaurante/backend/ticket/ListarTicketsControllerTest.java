package com.posrestaurante.backend.ticket;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.orden.EstadoOrden;
import com.posrestaurante.backend.orden.EstadoOrdenItem;
import com.posrestaurante.backend.orden.Orden;
import com.posrestaurante.backend.orden.OrdenItem;
import com.posrestaurante.backend.orden.OrdenItemRepository;
import com.posrestaurante.backend.orden.OrdenRepository;
import com.posrestaurante.backend.orden.TipoOrden;
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

class ListarTicketsControllerTest extends AbstractIntegrationTest {

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

    private Ticket unTicketConItem(Estacion estacion) {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
        orden.setEstado(EstadoOrden.ENVIADA);
        orden.setFechaApertura(Instant.now());
        ordenRepository.save(orden);

        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);
        Platillo platillo = new Platillo();
        platillo.setNombre("Tacos");
        platillo.setPrecio(new BigDecimal("85.00"));
        platillo.setCategoria(categoria);
        platillo.setEstacion(estacion);
        platillo.setActivo(true);
        platilloRepository.save(platillo);

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
        ordenItemRepository.save(item);

        return ticket;
    }

    @Test
    void anyAuthenticatedRoleCanListTickets() throws Exception {
        unTicketConItem(Estacion.P1);

        mockMvc.perform(get("/api/tickets").header("Authorization", "Bearer " + tokenFor(Rol.DUENO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.items[0].platilloNombre == 'Tacos')]").exists());
    }

    @Test
    void unauthenticatedCannotListTickets() throws Exception {
        mockMvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
    }

}
