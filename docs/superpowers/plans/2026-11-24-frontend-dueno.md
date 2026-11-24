# Frontend — Dueño — Plan de Implementación

**Objetivo:** Reemplazar el stub de `dueno.html` por el panel de administración — historias #2 (platillos), #8-9 (usuarios, mesas) y #6-7 (reportes). Cubre todo lo que el dueño ya puede hacer vía API salvo recetas/ingredientes (fuera de alcance del backend, inventario no implementado).

**Arquitectura:** Página única (`dueno.html` + `js/dueno.js`) con pestañas (reutilizando el estilo `.tabs` ya usado en el login): **Mesas**, **Menú** (categorías, modificadores y platillos en un mismo panel), **Usuarios**, **Reportes**.

**Decisiones de diseño:**
- **Eliminar una mesa pide confirmación con `confirm()` nativo del navegador** — es la única excepción a "nunca alert()" de los planes anteriores: esa regla era sobre errores (que sí deben mostrarse inline), no sobre confirmar una acción destructiva. Construir un modal propio solo para esto sería más complejidad de la que el caso amerita.
- **El formulario de platillo sirve para crear y editar a la vez** — "Editar" en una fila precarga el formulario y el envío hace `PATCH` en vez de `POST`; evita duplicar el formulario completo.
- **Los modificadores de un platillo se eligen con casillas** sobre la lista completa de modificadores (igual que en Mesero al agregar un ítem).
- **Reportes pide `desde`/`hasta` explícitos** (inputs `type=date`) y solo consulta al enviar — igual que exige el backend, sin rango por omisión.
- **Ningún dato sensible se cachea más de lo necesario**: cada pestaña recarga sus datos al abrirse.

**Verificación:** Manual con Playwright contra un backend real, igual que las pantallas anteriores.
