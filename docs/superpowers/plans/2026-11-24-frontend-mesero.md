# Frontend — Mesero — Plan de Implementación

**Objetivo:** Reemplazar el stub de `mesero.html` por la pantalla real de toma de órdenes — historias #11-16: elegir mesa o para llevar, agregar platillos con modificadores, enviar a cocina, agregar platillos después del envío, cerrar la cuenta.

**Arquitectura:** Una sola página (`mesero.html` + `js/mesero.js`), dos vistas controladas por JS (sin router, sin recarga de página): **Mesas** (lista de mesas + botón "para llevar" + lista de órdenes para llevar abiertas) y **Orden** (detalle de una orden: items, formulario para agregar, botones de acción según el estado). Reutiliza `api.js` (ya existe `requerirSesion`, `cerrarSesion`, y los helpers autenticados `apiGet`/`apiPostAuth`/`apiPatchAuth` agregados hoy mismo para esta pantalla).

**Decisiones de diseño:**
- **No existe un endpoint "orden por mesa".** No hace falta crear uno: se resuelve en el cliente filtrando `GET /api/ordenes` (que ya trae todas, con sus items) por `estado` en `[ABIERTA, ENVIADA]` y `mesaNumero` igual al de la mesa elegida. No es un problema de performance real al tamaño de un restaurante.
- **El frontend no reimplementa ninguna regla de negocio.** Solo llama la API y muestra el `message` de error que regrese (ya disponible gracias al fix de hoy a `/error`). Si el servidor rechaza algo (mesa ocupada, platillo inactivo, modificador inválido), el mensaje se muestra tal cual.
- **Los modificadores de un platillo son los que trae `platillo.modificadorIds`** — el formulario de agregar ítem solo ofrece como casillas los modificadores permitidos para el platillo seleccionado, igual que ya valida el servidor.
- **Sin polling en esta pantalla.** El mesero actúa, no necesita ver cambios en vivo de otros — a diferencia de Cocina, que sí lo necesitará.
- **Las acciones disponibles dependen de `orden.estado`:** `ABIERTA` → agregar ítems y "Enviar a cocina"; `ENVIADA` → agregar ítems (se tickean solos) y "Cerrar cuenta"; `CERRADA`/`PAGADA` → solo lectura (la orden ya no es del mesero).

**Verificación:** Manual con Playwright contra un backend real (igual que el login), sin JUnit — no hay infraestructura de pruebas de frontend.
