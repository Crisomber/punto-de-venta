# Frontend — Caja — Plan de Implementación

**Objetivo:** Reemplazar el stub de `caja.html` por la pantalla real del cajero — historias #22-24 (menos el descuento de inventario, fuera de alcance del backend): ver cuentas cerradas pendientes de cobro, cobrarlas registrando el método de pago, ver el recibo consolidado.

**Arquitectura:** Página única (`caja.html` + `js/caja.js`). Consume `GET /api/caja/pendientes` y `POST /api/caja/{ordenId}/pagar`. Dos vistas: lista de pendientes, y detalle/recibo de una orden al cobrarla.

**Decisiones de diseño:**
- **El total que se ve en la lista de pendientes es solo un estimado calculado en el cliente** (suma de `precioUnitario * cantidad` de los items que ya trae `GET /api/caja/pendientes`) — es puramente informativo. El monto real y autoritativo es el que regresa `POST /api/caja/{id}/pagar` en el recibo, calculado en el servidor; nunca se usa el estimado del cliente para nada que importe.
- **El cajero elige el método de pago (efectivo/tarjeta) antes de confirmar** — un paso de confirmación explícito, ya que cobrar es una acción irreversible (no hay endpoint para anular un pago).
- **Tras cobrar, se muestra el recibo tal cual lo regresa el servidor** (items, monto, método, cajero, fecha) antes de volver a la lista — el cajero necesita verlo para dárselo al cliente.
- **Sin polling** — a diferencia de Cocina, el cajero decide cuándo actualizar la lista (botón manual), porque cobrar es una acción deliberada y no se necesita ver cambios en vivo de otros mientras se está a mitad de un cobro.

**Verificación:** Manual con Playwright contra un backend real, igual que Mesero y Cocina.
