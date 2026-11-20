package com.posrestaurante.backend.caja;

import com.posrestaurante.backend.categoria.Categoria;
import com.posrestaurante.backend.categoria.CategoriaRepository;
import com.posrestaurante.backend.mesa.EstadoMesa;
import com.posrestaurante.backend.mesa.Mesa;
import com.posrestaurante.backend.mesa.MesaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PagarCuentaControllerTest extends AbstractIntegrationTest {

    @Autowired
    OrdenRepository ordenRepository;

    @Autowired
    OrdenItemRepository ordenItemRepository;

    @Autowired
    MesaRepository mesaRepository;

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

    private String cajeroToken() {
        Usuario cajero = TestUsuarios.staff("Caja", Rol.CAJERO, "hash", true);
        usuarioRepository.save(cajero);
        return jwtService.generateToken(cajero.getId(), "Caja", Rol.CAJERO);
    }

    private Platillo unPlatillo(String nombre, String precio) {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);
        Platillo platillo = new Platillo();
        platillo.setNombre(nombre);
        platillo.setPrecio(new BigDecimal(precio));
        platillo.setCategoria(categoria);
        platillo.setEstacion(Estacion.P1);
        platillo.setActivo(true);
        return platilloRepository.save(platillo);
    }

    private void agregarItem(Orden orden, Platillo platillo, int cantidad) {
        OrdenItem item = new OrdenItem();
        item.setOrden(orden);
        item.setPlatillo(platillo);
        item.setCantidad(cantidad);
        item.setPrecioUnitario(platillo.getPrecio());
        item.setEstado(EstadoOrdenItem.PENDIENTE);
        ordenItemRepository.save(item);
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
    void cajeroPagaCuentaConMesaLiberaMesaYMarcaPagada() throws Exception {
        Mesa mesa = new Mesa();
        mesa.setNumero(5);
        mesa.setEstado(EstadoMesa.OCUPADA);
        mesaRepository.save(mesa);
        Orden orden = unaOrden(EstadoOrden.CERRADA, mesa);
        agregarItem(orden, unPlatillo("Tacos", "85.00"), 2);
        agregarItem(orden, unPlatillo("Agua", "20.00"), 1);

        mockMvc.perform(post("/api/caja/" + orden.getId() + "/pagar")
                        .header("Authorization", "Bearer " + cajeroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"metodo\":\"EFECTIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(190.00))
                .andExpect(jsonPath("$.mesaNumero").value(5))
                .andExpect(jsonPath("$.items.length()").value(2));

        assertThat(ordenRepository.findById(orden.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoOrden.PAGADA);
        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoMesa.LIBRE);
    }

    @Test
    void cajeroPagaOrdenParaLlevarSinMesa() throws Exception {
        Orden orden = unaOrden(EstadoOrden.CERRADA, null);
        agregarItem(orden, unPlatillo("Tacos", "85.00"), 1);

        mockMvc.perform(post("/api/caja/" + orden.getId() + "/pagar")
                        .header("Authorization", "Bearer " + cajeroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"metodo\":\"TARJETA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaNumero").doesNotExist())
                .andExpect(jsonPath("$.monto").value(85.00));
    }

    @Test
    void pagarOrdenAbiertaFalla409() throws Exception {
        Orden orden = unaOrden(EstadoOrden.ABIERTA, null);

        mockMvc.perform(post("/api/caja/" + orden.getId() + "/pagar")
                        .header("Authorization", "Bearer " + cajeroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"metodo\":\"EFECTIVO\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void pagarOrdenYaPagadaFalla409() throws Exception {
        Orden orden = unaOrden(EstadoOrden.PAGADA, null);

        mockMvc.perform(post("/api/caja/" + orden.getId() + "/pagar")
                        .header("Authorization", "Bearer " + cajeroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"metodo\":\"EFECTIVO\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void pagarSinMetodoFalla400() throws Exception {
        Orden orden = unaOrden(EstadoOrden.CERRADA, null);

        mockMvc.perform(post("/api/caja/" + orden.getId() + "/pagar")
                        .header("Authorization", "Bearer " + cajeroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagarMetodoInvalidoFalla400() throws Exception {
        Orden orden = unaOrden(EstadoOrden.CERRADA, null);

        mockMvc.perform(post("/api/caja/" + orden.getId() + "/pagar")
                        .header("Authorization", "Bearer " + cajeroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"metodo\":\"BITCOIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagarOrdenInexistenteFalla404() throws Exception {
        mockMvc.perform(post("/api/caja/" + UUID.randomUUID() + "/pagar")
                        .header("Authorization", "Bearer " + cajeroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"metodo\":\"EFECTIVO\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonCajeroCannotPagar() throws Exception {
        Orden orden = unaOrden(EstadoOrden.CERRADA, null);

        mockMvc.perform(post("/api/caja/" + orden.getId() + "/pagar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"metodo\":\"EFECTIVO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotPagar() throws Exception {
        Orden orden = unaOrden(EstadoOrden.CERRADA, null);

        mockMvc.perform(post("/api/caja/" + orden.getId() + "/pagar")
                        .contentType(APPLICATION_JSON)
                        .content("{\"metodo\":\"EFECTIVO\"}"))
                .andExpect(status().isUnauthorized());
    }

}
