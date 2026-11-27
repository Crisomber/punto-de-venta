package com.posrestaurante.backend.reporte;

import com.posrestaurante.backend.caja.MetodoPago;
import com.posrestaurante.backend.caja.Pago;
import com.posrestaurante.backend.caja.PagoRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketsVentaControllerTest extends AbstractIntegrationTest {

    @Autowired
    OrdenRepository ordenRepository;

    @Autowired
    OrdenItemRepository ordenItemRepository;

    @Autowired
    PagoRepository pagoRepository;

    @Autowired
    PlatilloRepository platilloRepository;

    @Autowired
    CategoriaRepository categoriaRepository;

    @Autowired
    MesaRepository mesaRepository;

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

    private Platillo unPlatillo(String nombre) {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);
        Platillo platillo = new Platillo();
        platillo.setNombre(nombre);
        platillo.setPrecio(new BigDecimal("50.00"));
        platillo.setCategoria(categoria);
        platillo.setEstacion(Estacion.P1);
        platillo.setActivo(true);
        return platilloRepository.save(platillo);
    }

    private Orden unaVentaPagada(Instant fecha, Mesa mesa, Platillo platillo, int cantidad) {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesa(mesa);
        orden.setMesero(mesero);
        orden.setTipo(mesa == null ? TipoOrden.PARA_LLEVAR : TipoOrden.CONSUMO_LOCAL);
        orden.setEstado(EstadoOrden.PAGADA);
        orden.setFechaApertura(fecha);
        ordenRepository.save(orden);

        OrdenItem item = new OrdenItem();
        item.setOrden(orden);
        item.setPlatillo(platillo);
        item.setCantidad(cantidad);
        item.setPrecioUnitario(platillo.getPrecio());
        item.setEstado(EstadoOrdenItem.PENDIENTE);
        ordenItemRepository.save(item);

        Usuario cajero = TestUsuarios.staff("Ana", Rol.CAJERO, "hash", true);
        usuarioRepository.save(cajero);
        Pago pago = new Pago();
        pago.setOrden(orden);
        pago.setCajero(cajero);
        pago.setMetodo(MetodoPago.EFECTIVO);
        pago.setMonto(platillo.getPrecio().multiply(BigDecimal.valueOf(cantidad)));
        pago.setFecha(fecha);
        pagoRepository.save(pago);
        return orden;
    }

    @Test
    void duenoVeTicketsConMesaMeseroYCajero() throws Exception {
        Mesa mesa = new Mesa();
        mesa.setNumero(4);
        mesa.setEstado(EstadoMesa.OCUPADA);
        mesaRepository.save(mesa);
        Platillo tacos = unPlatillo("Tacos");
        unaVentaPagada(Instant.parse("2026-11-15T12:00:00Z"), mesa, tacos, 2);

        mockMvc.perform(get("/api/reportes/tickets?desde=2026-11-10&hasta=2026-11-20")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mesaNumero").value(4))
                .andExpect(jsonPath("$[0].meseroNombre").value("Luis"))
                .andExpect(jsonPath("$[0].cajeroNombre").value("Ana"))
                .andExpect(jsonPath("$[0].metodo").value("EFECTIVO"))
                .andExpect(jsonPath("$[0].monto").value(100.00))
                .andExpect(jsonPath("$[0].items.length()").value(1))
                .andExpect(jsonPath("$[0].items[0].platilloNombre").value("Tacos"))
                .andExpect(jsonPath("$[0].items[0].cantidad").value(2));
    }

    @Test
    void ventaParaLlevarNoTraeMesa() throws Exception {
        Platillo tacos = unPlatillo("Tacos");
        unaVentaPagada(Instant.parse("2026-11-15T12:00:00Z"), null, tacos, 1);

        mockMvc.perform(get("/api/reportes/tickets?desde=2026-11-10&hasta=2026-11-20")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mesaNumero").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void ventaFueraDeRangoNoAparece() throws Exception {
        Platillo tacos = unPlatillo("Tacos");
        unaVentaPagada(Instant.parse("2026-11-25T12:00:00Z"), null, tacos, 1);

        mockMvc.perform(get("/api/reportes/tickets?desde=2026-11-10&hasta=2026-11-20")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void desdePosteriorAHastaFalla400() throws Exception {
        mockMvc.perform(get("/api/reportes/tickets?desde=2026-11-20&hasta=2026-11-10")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonDuenoCannotAccessTickets() throws Exception {
        mockMvc.perform(get("/api/reportes/tickets?desde=2026-11-10&hasta=2026-11-20")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CAJERO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAccessTickets() throws Exception {
        mockMvc.perform(get("/api/reportes/tickets?desde=2026-11-10&hasta=2026-11-20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duenoVeVentasAgrupadasPorDiaIncluyendoDiasSinVentas() throws Exception {
        Platillo tacos = unPlatillo("Tacos");
        unaVentaPagada(Instant.parse("2026-11-15T12:00:00Z"), null, tacos, 2); // $100.00
        unaVentaPagada(Instant.parse("2026-11-15T18:00:00Z"), null, tacos, 1); // $50.00
        unaVentaPagada(Instant.parse("2026-11-17T09:00:00Z"), null, tacos, 1); // $50.00

        mockMvc.perform(get("/api/reportes/ventas-por-dia?desde=2026-11-15&hasta=2026-11-17")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].fecha").value("2026-11-15"))
                .andExpect(jsonPath("$[0].total").value(150.00))
                .andExpect(jsonPath("$[1].fecha").value("2026-11-16"))
                .andExpect(jsonPath("$[1].total").value(0))
                .andExpect(jsonPath("$[2].fecha").value("2026-11-17"))
                .andExpect(jsonPath("$[2].total").value(50.00));
    }

    @Test
    void ventasPorDiaDesdePosteriorAHastaFalla400() throws Exception {
        mockMvc.perform(get("/api/reportes/ventas-por-dia?desde=2026-11-20&hasta=2026-11-10")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonDuenoCannotAccessVentasPorDia() throws Exception {
        mockMvc.perform(get("/api/reportes/ventas-por-dia?desde=2026-11-10&hasta=2026-11-20")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CAJERO)))
                .andExpect(status().isForbidden());
    }

}
