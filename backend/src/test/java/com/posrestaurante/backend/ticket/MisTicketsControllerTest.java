package com.posrestaurante.backend.ticket;

import com.posrestaurante.backend.orden.EstadoOrden;
import com.posrestaurante.backend.orden.Orden;
import com.posrestaurante.backend.orden.OrdenItemRepository;
import com.posrestaurante.backend.orden.OrdenRepository;
import com.posrestaurante.backend.orden.TipoOrden;
import com.posrestaurante.backend.platillo.PlatilloRepository;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
import com.posrestaurante.backend.usuario.Estacion;
import com.posrestaurante.backend.usuario.Rol;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MisTicketsControllerTest extends AbstractIntegrationTest {

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

    // A diferencia de tokenFor, este sí persiste un Usuario real: el endpoint
    // resuelve la estacion de quien llama consultando su propio Usuario por id,
    // no por el JWT — un token para un id nunca guardado no encontraria estacion.
    private String chefToken(Estacion estacion) {
        Usuario chef = TestUsuarios.staff("Chef", Rol.CHEF, "hash", true);
        chef.setEstacion(estacion);
        usuarioRepository.save(chef);
        return jwtService.generateToken(chef.getId(), "Chef", Rol.CHEF);
    }

    private Ticket unTicket(Estacion estacion) {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
        orden.setEstado(EstadoOrden.ENVIADA);
        orden.setFechaApertura(Instant.now());
        ordenRepository.save(orden);

        Ticket ticket = new Ticket();
        ticket.setOrden(orden);
        ticket.setEstacion(estacion);
        ticket.setFechaGenerado(Instant.now());
        ticket.setEstado(TicketEstado.PENDIENTE);
        return ticketRepository.save(ticket);
    }

    @Test
    void chefSeesOnlyOwnEstacionTickets() throws Exception {
        unTicket(Estacion.P1);
        unTicket(Estacion.P2);

        mockMvc.perform(get("/api/tickets/propios").header("Authorization", "Bearer " + chefToken(Estacion.P1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estacion").value("P1"));
    }

    @Test
    void nonChefCannotAccessPropios() throws Exception {
        mockMvc.perform(get("/api/tickets/propios").header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAccessPropios() throws Exception {
        mockMvc.perform(get("/api/tickets/propios")).andExpect(status().isUnauthorized());
    }

}
