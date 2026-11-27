package com.posrestaurante.backend.reporte;

import com.posrestaurante.backend.caja.MetodoPago;
import com.posrestaurante.backend.caja.Pago;
import com.posrestaurante.backend.caja.PagoRepository;
import com.posrestaurante.backend.modificador.Modificador;
import com.posrestaurante.backend.orden.Orden;
import com.posrestaurante.backend.orden.OrdenItem;
import com.posrestaurante.backend.orden.OrdenItemRepository;
import com.posrestaurante.backend.orden.dto.OrdenItemResponse;
import com.posrestaurante.backend.reporte.dto.PlatilloMasVendidoResponse;
import com.posrestaurante.backend.reporte.dto.ReporteVentasResponse;
import com.posrestaurante.backend.reporte.dto.TicketVentaResponse;
import com.posrestaurante.backend.reporte.dto.VentaDiaResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reportes")
@Transactional
public class ReporteController {

    private final PagoRepository pagoRepository;
    private final OrdenItemRepository ordenItemRepository;

    public ReporteController(PagoRepository pagoRepository, OrdenItemRepository ordenItemRepository) {
        this.pagoRepository = pagoRepository;
        this.ordenItemRepository = ordenItemRepository;
    }

    @GetMapping("/ventas")
    public ReporteVentasResponse ventas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        validarRango(desde, hasta);
        List<Pago> pagos = pagosEnRango(desde, hasta);

        BigDecimal totalVendido = pagos.stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEfectivo = pagos.stream().filter(p -> p.getMetodo() == MetodoPago.EFECTIVO)
                .map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTarjeta = pagos.stream().filter(p -> p.getMetodo() == MetodoPago.TARJETA)
                .map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ticketPromedio = pagos.isEmpty() ? BigDecimal.ZERO
                : totalVendido.divide(BigDecimal.valueOf(pagos.size()), 2, RoundingMode.HALF_UP);

        return new ReporteVentasResponse(totalVendido, totalEfectivo, totalTarjeta, ticketPromedio, pagos.size());
    }

    @GetMapping("/platillos-mas-vendidos")
    public List<PlatilloMasVendidoResponse> platillosMasVendidos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        validarRango(desde, hasta);
        List<Pago> pagos = pagosEnRango(desde, hasta);
        List<UUID> ordenIds = pagos.stream().map(pago -> pago.getOrden().getId()).toList();
        if (ordenIds.isEmpty()) {
            return List.of();
        }

        List<OrdenItem> items = ordenItemRepository.findByOrden_IdIn(ordenIds);
        Map<UUID, List<OrdenItem>> porPlatillo = items.stream()
                .collect(Collectors.groupingBy(item -> item.getPlatillo().getId()));

        return porPlatillo.values().stream()
                .map(lista -> new PlatilloMasVendidoResponse(lista.get(0).getPlatillo().getId(),
                        lista.get(0).getPlatillo().getNombre(),
                        lista.stream().mapToInt(OrdenItem::getCantidad).sum()))
                .sorted(Comparator.comparingInt(PlatilloMasVendidoResponse::cantidadVendida).reversed())
                .toList();
    }

    @GetMapping("/tickets")
    public List<TicketVentaResponse> tickets(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        validarRango(desde, hasta);
        return pagosEnRango(desde, hasta).stream()
                .sorted(Comparator.comparing(Pago::getFecha).reversed())
                .map(this::toTicketResponse)
                .toList();
    }

    @GetMapping("/ventas-por-dia")
    public List<VentaDiaResponse> ventasPorDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        validarRango(desde, hasta);

        Map<LocalDate, BigDecimal> porDia = new TreeMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            porDia.put(dia, BigDecimal.ZERO);
        }
        for (Pago pago : pagosEnRango(desde, hasta)) {
            LocalDate dia = pago.getFecha().atZone(ZoneOffset.UTC).toLocalDate();
            porDia.merge(dia, pago.getMonto(), BigDecimal::add);
        }

        return porDia.entrySet().stream()
                .map(entrada -> new VentaDiaResponse(entrada.getKey(), entrada.getValue()))
                .toList();
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "desde no puede ser posterior a hasta");
        }
    }

    private List<Pago> pagosEnRango(LocalDate desde, LocalDate hasta) {
        Instant desdeInstant = desde.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant hastaInstant = hasta.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return pagoRepository.findByFechaGreaterThanEqualAndFechaLessThan(desdeInstant, hastaInstant);
    }

    private TicketVentaResponse toTicketResponse(Pago pago) {
        Orden orden = pago.getOrden();
        List<OrdenItemResponse> items = ordenItemRepository.findByOrden(orden).stream()
                .map(this::toItemResponse)
                .toList();
        return new TicketVentaResponse(orden.getId(),
                orden.getMesa() == null ? null : orden.getMesa().getNumero(),
                orden.getMesero().getNombre(), pago.getCajero().getNombre(),
                pago.getMetodo(), pago.getMonto(), pago.getFecha(), items);
    }

    private OrdenItemResponse toItemResponse(OrdenItem item) {
        Set<UUID> modIds = item.getModificadores().stream().map(Modificador::getId).collect(Collectors.toSet());
        Set<String> modNombres = item.getModificadores().stream().map(Modificador::getNombre).collect(Collectors.toSet());
        return new OrdenItemResponse(item.getId(), item.getPlatillo().getId(), item.getPlatillo().getNombre(),
                item.getCantidad(), item.getPrecioUnitario(), item.getVarianteNombre(), item.getEstado(),
                modIds, modNombres);
    }

}
