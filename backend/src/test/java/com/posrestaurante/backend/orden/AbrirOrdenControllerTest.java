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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AbrirOrdenControllerTest extends AbstractIntegrationTest {

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

    private String meseroToken() {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        return jwtService.generateToken(mesero.getId(), "Luis", Rol.MESERO);
    }

    private Mesa unaMesaLibre(int numero) {
        Mesa mesa = new Mesa();
        mesa.setNumero(numero);
        mesa.setEstado(EstadoMesa.LIBRE);
        return mesaRepository.save(mesa);
    }

    @Test
    void meseroOpensConsumoLocalOrder() throws Exception {
        Mesa mesa = unaMesaLibre(5);

        mockMvc.perform(post("/api/ordenes")
                        .header("Authorization", "Bearer " + meseroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"CONSUMO_LOCAL\",\"mesaId\":\"" + mesa.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("CONSUMO_LOCAL"))
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.mesaNumero").value(5));

        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoMesa.OCUPADA);
    }

    @Test
    void meseroOpensParaLlevarOrderWithoutMesa() throws Exception {
        mockMvc.perform(post("/api/ordenes")
                        .header("Authorization", "Bearer " + meseroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"PARA_LLEVAR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mesaNumero").doesNotExist());
    }

    @Test
    void consumoLocalWithoutMesaFails() throws Exception {
        mockMvc.perform(post("/api/ordenes")
                        .header("Authorization", "Bearer " + meseroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"CONSUMO_LOCAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paraLlevarWithMesaFails() throws Exception {
        Mesa mesa = unaMesaLibre(8);

        mockMvc.perform(post("/api/ordenes")
                        .header("Authorization", "Bearer " + meseroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"PARA_LLEVAR\",\"mesaId\":\"" + mesa.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openingOrderOnOccupiedMesaFails() throws Exception {
        Mesa mesa = unaMesaLibre(6);
        mesa.setEstado(EstadoMesa.OCUPADA);
        mesaRepository.save(mesa);

        mockMvc.perform(post("/api/ordenes")
                        .header("Authorization", "Bearer " + meseroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"CONSUMO_LOCAL\",\"mesaId\":\"" + mesa.getId() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void openingOrderWithUnknownMesaFails() throws Exception {
        mockMvc.perform(post("/api/ordenes")
                        .header("Authorization", "Bearer " + meseroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"CONSUMO_LOCAL\",\"mesaId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cajeroCanOpenOrderForVentaDirecta() throws Exception {
        Usuario cajero = TestUsuarios.staff("Ana", Rol.CAJERO, "hash", true);
        usuarioRepository.save(cajero);

        mockMvc.perform(post("/api/ordenes")
                        .header("Authorization", "Bearer " + jwtService.generateToken(cajero.getId(), "Ana", Rol.CAJERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"PARA_LLEVAR\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void chefCannotOpenOrder() throws Exception {
        mockMvc.perform(post("/api/ordenes")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CHEF))
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"PARA_LLEVAR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotOpenOrder() throws Exception {
        mockMvc.perform(post("/api/ordenes")
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"PARA_LLEVAR\"}"))
                .andExpect(status().isUnauthorized());
    }

}
