package com.posrestaurante.backend.reporte;

import com.posrestaurante.backend.caja.MetodoPago;
import com.posrestaurante.backend.caja.Pago;
import com.posrestaurante.backend.caja.PagoRepository;
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

class PlatillosMasVendidosControllerTest extends AbstractIntegrationTest {

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

    private void unaVentaPagada(Instant fecha, Platillo platillo, int cantidad) {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
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

        Usuario cajero = TestUsuarios.staff("Caja", Rol.CAJERO, "hash", true);
        usuarioRepository.save(cajero);
        Pago pago = new Pago();
        pago.setOrden(orden);
        pago.setCajero(cajero);
        pago.setMetodo(MetodoPago.EFECTIVO);
        pago.setMonto(platillo.getPrecio().multiply(BigDecimal.valueOf(cantidad)));
        pago.setFecha(fecha);
        pagoRepository.save(pago);
    }

    @Test
    void duenoVePlatillosOrdenadosPorCantidad() throws Exception {
        Platillo tacos = unPlatillo("Tacos");
        Platillo pizza = unPlatillo("Pizza");
        unaVentaPagada(Instant.parse("2026-11-15T12:00:00Z"), tacos, 3);
        unaVentaPagada(Instant.parse("2026-11-16T12:00:00Z"), tacos, 2);
        unaVentaPagada(Instant.parse("2026-11-17T12:00:00Z"), pizza, 2);

        mockMvc.perform(get("/api/reportes/platillos-mas-vendidos?desde=2026-11-10&hasta=2026-11-20")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].platilloNombre").value("Tacos"))
                .andExpect(jsonPath("$[0].cantidadVendida").value(5))
                .andExpect(jsonPath("$[1].platilloNombre").value("Pizza"))
                .andExpect(jsonPath("$[1].cantidadVendida").value(2));
    }

    @Test
    void ventaFueraDeRangoNoSeCuenta() throws Exception {
        Platillo tacos = unPlatillo("Tacos");
        unaVentaPagada(Instant.parse("2026-11-25T12:00:00Z"), tacos, 10);

        mockMvc.perform(get("/api/reportes/platillos-mas-vendidos?desde=2026-11-10&hasta=2026-11-20")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sinVentasEnRangoDaListaVacia() throws Exception {
        mockMvc.perform(get("/api/reportes/platillos-mas-vendidos?desde=2030-01-01&hasta=2030-01-31")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void desdePosteriorAHastaFalla400() throws Exception {
        mockMvc.perform(get("/api/reportes/platillos-mas-vendidos?desde=2026-11-20&hasta=2026-11-10")
                        .header("Authorization", "Bearer " + duenoToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonDuenoCannotAccessRanking() throws Exception {
        mockMvc.perform(get("/api/reportes/platillos-mas-vendidos?desde=2026-11-10&hasta=2026-11-20")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CAJERO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAccessRanking() throws Exception {
        mockMvc.perform(get("/api/reportes/platillos-mas-vendidos?desde=2026-11-10&hasta=2026-11-20"))
                .andExpect(status().isUnauthorized());
    }

}
