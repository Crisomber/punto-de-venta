package com.posrestaurante.backend.reporte;

import com.posrestaurante.backend.caja.MetodoPago;
import com.posrestaurante.backend.caja.Pago;
import com.posrestaurante.backend.caja.PagoRepository;
import com.posrestaurante.backend.reporte.dto.ReporteVentasResponse;
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
import java.util.List;

@RestController
@RequestMapping("/api/reportes")
@Transactional
public class ReporteController {

    private final PagoRepository pagoRepository;

    public ReporteController(PagoRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
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

}
