package com.posrestaurante.backend.orden;

import com.posrestaurante.backend.mesa.EstadoMesa;
import com.posrestaurante.backend.mesa.Mesa;
import com.posrestaurante.backend.mesa.MesaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CerrarOrdenControllerTest extends AbstractIntegrationTest {

    @Autowired
    OrdenRepository ordenRepository;

    @Autowired
    MesaRepository mesaRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private Orden unaOrden(EstadoOrden estado, Mesa mesa) {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesa(mesa);
        orden.setMesero(mesero);
        orden.setTipo(mesa == null ? TipoOrden.PARA_LLEVAR : TipoOrden.CONSUMO_LOCAL);
        orden.setEstado(estado);
        orden.setFechaApertura(Instant.now());
        return ordenRepository.save(orden);
    }

    @Test
    void meseroClosesSentOrderAndMesaStaysOcupada() throws Exception {
        Mesa mesa = new Mesa();
        mesa.setNumero(11);
        mesa.setEstado(EstadoMesa.OCUPADA);
        mesaRepository.save(mesa);
        Orden orden = unaOrden(EstadoOrden.ENVIADA, mesa);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/cerrar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"));

        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoMesa.OCUPADA);
    }

    @Test
    void closingOpenOrderFails() throws Exception {
        Orden orden = unaOrden(EstadoOrden.ABIERTA, null);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/cerrar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isConflict());
    }

    @Test
    void closingAlreadyClosedOrderFails() throws Exception {
        Orden orden = unaOrden(EstadoOrden.CERRADA, null);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/cerrar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownOrdenIdReturns404() throws Exception {
        mockMvc.perform(post("/api/ordenes/" + UUID.randomUUID() + "/cerrar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cajeroCanCloseOrderForVentaDirecta() throws Exception {
        Orden orden = unaOrden(EstadoOrden.ENVIADA, null);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/cerrar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CAJERO)))
                .andExpect(status().isOk());
    }

    @Test
    void chefCannotCloseOrder() throws Exception {
        Orden orden = unaOrden(EstadoOrden.ENVIADA, null);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/cerrar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CHEF)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotCloseOrder() throws Exception {
        Orden orden = unaOrden(EstadoOrden.ENVIADA, null);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/cerrar"))
                .andExpect(status().isUnauthorized());
    }

}
