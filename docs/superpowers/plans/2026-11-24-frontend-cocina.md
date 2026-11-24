# Frontend — Cocina — Plan de Implementación

**Objetivo:** Reemplazar el stub de `cocina.html` por la pantalla real del chef — historias #17-19: ver solo los tickets de su propia estación, y marcar un platillo como "en preparación" o "listo" (el servidor ya restringe esto último a P2, ver plan de Cocina del backend).

**Arquitectura:** Página única (`cocina.html` + `js/cocina.js`). Consume `GET /api/tickets/propios` (ya filtra por la estación real del chef en el servidor) y `PATCH /api/ordenes/items/{id}/estado`.

**Decisiones de diseño:**
- **El frontend no decide si el chef es P1 o P2.** No hay ningún endpoint que exponga la estación del usuario logueado al cliente (el JWT no la trae, por diseño — ver Restricciones Globales del plan de backend-fundamentos), y agregar uno solo para esto sería más cambio del necesario. En su lugar, los botones "En preparación"/"Listo" siempre se muestran en todos los tickets; si el chef es de P1, el clic falla con el `403` que el servidor ya explica ("esta estacion no usa confirmacion de estado"), y ese mensaje se muestra tal cual. Es el mismo principio de "no reimplementar reglas de negocio" ya aplicado en Mesero.
- **Polling cada 8 segundos**, como ya documenta la spec §8 para las pantallas de cocina/caja ("se actualizan por consulta periódica"). También hay un botón manual "Actualizar" para no depender solo del temporizador.
- **Los tickets se muestran ordenados por `fechaGenerado` ascendente** (el más viejo primero — orden natural de cocina, FIFO).
- **Un ticket por tarjeta**, con sus items y, junto a cada item, su estado actual (pill) y los dos botones de acción.

**Verificación:** Manual con Playwright contra un backend real, igual que Mesero.
