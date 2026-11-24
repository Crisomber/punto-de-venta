package com.posrestaurante.backend.reporte;

import com.posrestaurante.backend.caja.MetodoPago;
import com.posrestaurante.backend.caja.Pago;
import com.posrestaurante.backend.caja.PagoRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReporteVentasControllerTest extends AbstractIntegrationTest {

    @Autowired
    OrdenRepository ordenRepository;

    @Autowired
    PagoRepository pagoRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private String duenoToken() {
        Usuario dueno = TestUsuarios.dueno("dueno", "hash", true);
        usuarioRepository.save(dueno);
        return jwtService.generateToken(dueno.getId(), "dueno", Rol.DUENO);
    }

    private void unPago(Instant fecha, MetodoPago metodo, String monto) {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
        orden.setEstado(EstadoOrden.PAGADA);
        orden.setFechaApertura(fecha);
        ordenRepository.save(orden);

        Usuario cajero = TestUsuarios.staff("Caja", Rol.CAJERO, "hash", true);
        usuarioRepository.save(cajero);

        Pago pago = new Pago();
        pago.setOrden(orden);
        pago.setCajero(cajero);
        pago.setMetodo(metodo);
        pago.setMonto(new BigDecimal(monto));
        pago.setFecha(fecha);
        pagoRepository.save(pago);
    }

    @Test
    void duenoVeTotalesEnRango() throws Exception {
        unPago(Instant.parse("2026-11-15T12:00:00Z"), MetodoPago.EFECTIVO, "100.00");
        unPago(Instant.parse("2026-11-16T08:00:00Z"), MetodoPago.TARJETA, "50.00");
        unPago(Instant.parse("2026-11-25T00:00:00Z"), MetodoPago.EFECTIVO, "999.00"); // fuera de rango

        mockMvc.perform(get("/api/reportes/ventas?desde=2026-11-10&hasta=2026-11-20")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVendido").value(150.00))
                .andExpect(jsonPath("$.totalEfectivo").value(100.00))
                .andExpect(jsonPath("$.totalTarjeta").value(50.00))
                .andExpect(jsonPath("$.ticketPromedio").value(75.00))
                .andExpect(jsonPath("$.cantidadCuentas").value(2));
    }

    @Test
    void sinVentasEnRangoDaCeros() throws Exception {
        mockMvc.perform(get("/api/reportes/ventas?desde=2030-01-01&hasta=2030-01-31")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVendido").value(0))
                .andExpect(jsonPath("$.cantidadCuentas").value(0))
                .andExpect(jsonPath("$.ticketPromedio").value(0));
    }

    @Test
    void desdePosteriorAHastaFalla400() throws Exception {
        mockMvc.perform(get("/api/reportes/ventas?desde=2026-11-20&hasta=2026-11-10")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonDuenoCannotAccessReporte() throws Exception {
        mockMvc.perform(get("/api/reportes/ventas?desde=2026-11-10&hasta=2026-11-20")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CAJERO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAccessReporte() throws Exception {
        mockMvc.perform(get("/api/reportes/ventas?desde=2026-11-10&hasta=2026-11-20"))
                .andExpect(status().isUnauthorized());
    }

}
