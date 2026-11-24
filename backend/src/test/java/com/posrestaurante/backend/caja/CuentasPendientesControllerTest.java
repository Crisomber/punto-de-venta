package com.posrestaurante.backend.caja;

import com.posrestaurante.backend.orden.EstadoOrden;
import com.posrestaurante.backend.orden.Orden;
import com.posrestaurante.backend.orden.OrdenRepository;
import com.posrestaurante.backend.orden.TipoOrden;
import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
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

class CuentasPendientesControllerTest extends AbstractIntegrationTest {

    @Autowired
    OrdenRepository ordenRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private String cajeroToken() {
        Usuario cajero = TestUsuarios.staff("Caja", Rol.CAJERO, "hash", true);
        usuarioRepository.save(cajero);
        return jwtService.generateToken(cajero.getId(), "Caja", Rol.CAJERO);
    }

    private Orden unaOrden(EstadoOrden estado) {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
        orden.setEstado(estado);
        orden.setFechaApertura(Instant.now());
        return ordenRepository.save(orden);
    }

    @Test
    void cajeroVeSoloOrdenesCerradas() throws Exception {
        unaOrden(EstadoOrden.ABIERTA);
        unaOrden(EstadoOrden.ENVIADA);
        Orden cerrada = unaOrden(EstadoOrden.CERRADA);
        unaOrden(EstadoOrden.PAGADA);

        mockMvc.perform(get("/api/caja/pendientes").header("Authorization", "Bearer " + cajeroToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(cerrada.getId().toString()));
    }

    @Test
    void nonCajeroCannotAccessPendientes() throws Exception {
        mockMvc.perform(get("/api/caja/pendientes").header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAccessPendientes() throws Exception {
        mockMvc.perform(get("/api/caja/pendientes")).andExpect(status().isUnauthorized());
    }

}
