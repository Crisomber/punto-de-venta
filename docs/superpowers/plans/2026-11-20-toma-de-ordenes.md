# Toma de Órdenes (Mesero) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a mesero run the core transactional flow the whole system exists for: open an order (on a mesa, or para llevar), add platillos with modificadores, send it to the kitchen (splitting it into one ticket per estación automatically), keep adding items after that first send without losing track of what's new, and close the account to hand it off to Caja.

**Scope boundary:** This plan is the **mesero's side only**. Two things it deliberately stops short of, both left for their own plans:
- **Cocina** — a chef's per-estación ticket screen and marking items `EN_PREPARACION`/`LISTO` (historias #17–19). This plan creates `TICKET` and `ORDEN_ITEM` rows with the right `estacion`/`estado`; nothing reads them from the kitchen side yet.
- **Caja** — cobro, descuento de inventario, and freeing the mesa back to `LIBRE` (historias #21–24, flujo operativo step 8–9). This plan's `cerrar` action only gets the order to `CERRADA`; `PAGADA` and everything that implies is a later plan.

**Architecture:** Two new packages: `orden` (`Orden`, `OrdenItem`, their enums, DTOs, and a single `OrdenController` built up across all six tasks) and `ticket` (`Ticket`, `TicketEstado`) — mirroring how `usuario`/`asistencia` split a tightly-coupled pair into two packages in backend-fundamentos. Mutating endpoints are `MESERO`-only (a new pattern: every prior plan's mutations were `DUENO`-only); reading is open to any authenticated role, same as the menu-y-mesas plan, since a future caja/cocina plan will need to read these same rows.

**Tech Stack:** Same as every prior plan — Java 21, Spring Boot 3.5.x, Spring Data JPA, Spring Security, Testcontainers-backed integration tests. No new dependencies.

**Spec:** `docs/2026-11-13-pos-restaurante-design.md` — §4 "Flujo operativo" steps 1–7 (step 8–9 are Caja, out of scope), §8 "Modelo de datos" (`ORDEN`, `ORDEN_ITEM`, `ORDEN_ITEM_MODIFICADOR`, `TICKET`) and its "Reglas de negocio confirmadas", historias de usuario #11–16. Depends on `docs/superpowers/plans/2026-11-19-backend-fundamentos.md` (auth, `AuthenticatedUser`) and `docs/superpowers/plans/2026-11-20-menu-y-mesas.md` (`Mesa`, `Platillo`, `Modificador`), both already implemented.

## Global Constraints

- **`mesero_id` always comes from the caller's own JWT, never from the request body.** `AbrirOrdenRequest` has no `meseroId` field at all — the endpoint reads it off `Authentication`'s `AuthenticatedUser` principal, the same pattern `AuthController.me()` already uses. A mesero must never be able to open an order "as" someone else.
- **`ORDEN.mesa_id` is `null` exactly when `tipo = PARA_LLEVAR`**, per the spec's explicit "Reglas de negocio confirmadas" — not the other way around. (The ER diagram's inline field comment, `"nullable, solo CONSUMO_LOCAL"`, reads backwards from that confirmed rule; go by the confirmed-rules prose, not the diagram shorthand, if the two ever seem to disagree.)
- **A mesa must be `LIBRE` to be opened on**, and opening a `CONSUMO_LOCAL` order immediately sets it to `OCUPADA`. This plan never sets a mesa back to `LIBRE` — that only happens when a future Caja plan marks the order `PAGADA` (a table is occupied for as long as it has an unpaid order, not just until the mesero's part is done). Flagged as a deliberate decision, not an oversight: `cerrar` (Task 5) moves the **order** to `CERRADA` and touches nothing on the `Mesa`.
- **`OrdenItem.precioUnitario` is a snapshot of `Platillo.precio` taken when the item is added**, not a live join. A later price change on the platillo must never retroactively change what an existing order charges.
- **Only an `activo` platillo can be added to an order.** This is the filtered read the menu-y-mesas plan's Non-Goals deferred to "whichever plan actually needs it" — this is that plan.
- **A requested `modificadorId` must be in *that specific platillo's* allowed set** (`Platillo.modificadores`, from menu-y-mesas), not just any existing `Modificador` row. Checking existence alone isn't enough.
- **`abrirOrden` is the only endpoint in this plan that resolves the caller's own `Usuario` row** (to set `Orden.mesero`, a `NOT NULL` FK). Every other mutating endpoint (`items`, `enviar`, `cerrar`) only cares about the caller's *role* (enforced by `SecurityConfig`, never re-checked in the controller body) and never looks the caller up by id. So `AbrirOrdenControllerTest`'s success-path tests need a token minted for a `Usuario` actually saved in the test (`meseroToken()`, not the bare `tokenFor(Rol.MESERO)` every other test file in this plan uses) — a token for a random, never-persisted id would 401 there even though the request is otherwise perfectly valid, since `usuarioRepository.findById(...)` would find nothing.
- **The ticket-grouping rule is one rule, used twice, not two rules.** "Group the affected items by `estacion`, one `Ticket` per represented `estacion`" is exactly what happens on the first `enviar` (Task 3) and on every item added afterward (Task 4) — implemented as one shared private method so the two call sites can't drift apart.

## Review Focus

- Opening a second order on a mesa that's already `OCUPADA` must `409`, not silently create two concurrent orders on the same physical table.
- In a single send/add batch containing items from both `P1` and `P2`, they must land in **two** tickets, never merged into one; items of the *same* estación in the same batch must land in **one** ticket, never split across several.
- State-machine discipline: `enviar` only from `ABIERTA`, `cerrar` only from `ENVIADA`, items only addable while `ABIERTA` or `ENVIADA` — every wrong-state call must `409`, never silently succeed and never `500`.
- `precio_unitario` must never be re-read from `Platillo` after the item is created — verify this with a test that changes a platillo's price *after* an order already references it and asserts the existing order is unaffected.
- A `modificadorId` that exists in the database but isn't in the target platillo's own allowed set must `400` — the easy mistake is checking "does this modificador exist" instead of "does this *platillo* allow it."
- `open-in-view` is `false` (backend-fundamentos Task 1's `application.yml`). Every repository call opens and closes its *own* short transaction/session unless something wraps a wider one around it, so any `@ManyToOne`/`@ManyToMany` field read while building a response must come from an already-*loaded* entity — a lazy proxy fetched via `getReferenceById`, or a freshly-queried entity (e.g. from `findAll()`/`findByOrden(...)`) whose association was never touched inside that same query's transaction, throws `LazyInitializationException` the moment something later reads it. **`OrdenController` is `@Transactional` at the class level from Task 1 onward specifically to make this safe**: every endpoint builds its whole response (including every lazy `platillo`/`mesa`/`mesero`/`ticket`/`modificadores` read inside `toResponse`/`toItemResponse`) within one transaction, so those reads always succeed. Without it, `GET /api/ordenes` would 500 the moment it tried to serialize `OrdenItemResponse.platilloNombre` for an item whose `platillo` association hadn't been touched in that exact query.
- **This same risk already exists, unfixed, in the already-shipped `PlatilloController`** (`backend/src/main/java/com/posrestaurante/backend/platillo/PlatilloController.java` — reading `platillo.getCategoria().getNombre()`/`platillo.getModificadores()` inside `toResponse` for entities `findAll()`/`findById()` returned). Its tests don't catch it because `AbstractIntegrationTest`'s class-level `@Transactional` wraps each *test method* (MockMvc call included) in one transaction, which masks exactly this problem in every integration test in this codebase — it would still 500 for a real, non-test HTTP request. Not this plan's bug to fix, but worth a short follow-up task once this plan lands: add `@Transactional` (or `@Transactional(readOnly = true)` on the `GET`s) to `PlatilloController`, the same fix this plan applies to `OrdenController` from the start.

---

## Task 1: Abrir orden (POST /api/ordenes)

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__create_orden.sql`
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/TipoOrden.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/EstadoOrden.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/Orden.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/OrdenRepository.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/dto/AbrirOrdenRequest.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/dto/OrdenResponse.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/OrdenController.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/posrestaurante/backend/orden/AbrirOrdenControllerTest.java`

**Interfaces:**
- Consumes: `Mesa`/`MesaRepository`/`EstadoMesa` (menu-y-mesas); `Usuario`/`UsuarioRepository`, `AuthenticatedUser`, `JwtService` (backend-fundamentos).
- Produces: `TipoOrden {CONSUMO_LOCAL, PARA_LLEVAR}`; `EstadoOrden {ABIERTA, ENVIADA, CERRADA, PAGADA}` (the full spec enum, even though this plan only ever sets the first two — `PAGADA` exists in the DB `CHECK` from day one so a future Caja plan needs no migration just to widen it); `Orden(UUID id, Mesa mesa /* nullable */, Usuario mesero, TipoOrden tipo, EstadoOrden estado, Instant fechaApertura, Instant fechaCierre /* nullable */)`; `OrdenRepository extends JpaRepository<Orden, UUID>`; `AbrirOrdenRequest(TipoOrden tipo, UUID mesaId)`; `OrdenResponse(UUID id, Integer mesaNumero, UUID meseroId, String meseroNombre, TipoOrden tipo, EstadoOrden estado, Instant fechaApertura, Instant fechaCierre, List<OrdenItemResponse> items)` — `items` is always `[]` for this task (Task 2 is what populates it; the shape is defined now so later tasks don't need to touch the response record). `POST /api/ordenes` — `201` + `OrdenResponse` on success; `400` if `tipo` is missing, if `CONSUMO_LOCAL` and `mesaId` is missing or doesn't match a `Mesa`, or if `PARA_LLEVAR` and `mesaId` is present; `409` if the referenced mesa is already `OCUPADA`; `403` for a non-`MESERO` token; `401` with no token. Adds, before `.anyRequest().authenticated()`: `.requestMatchers(HttpMethod.GET, "/api/ordenes/**").authenticated()` then `.requestMatchers("/api/ordenes/**").hasRole("MESERO")` — this pair covers every endpoint Tasks 2–6 add too (Task 6's `GET`s included).

- [x] **Step 1: Write the failing controller test**

```java
class AbrirOrdenControllerTest extends AbstractIntegrationTest {

    @Autowired OrdenRepository ordenRepository;
    @Autowired MesaRepository mesaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    // Unlike tokenFor, this persists a real Usuario first: abrirOrden looks the
    // caller up by usuarioId to set Orden.mesero (a NOT NULL FK), so a token for
    // an id that was never saved would 401 even on an otherwise-valid request.
    private String meseroToken() {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        return jwtService.generateToken(mesero.getId(), "Luis", Rol.MESERO);
    }

    private Mesa unaMesaLibre(int numero) {
        Mesa mesa = new Mesa();
        mesa.setNumero(numero);
        mesa.setEstado(EstadoMesa.LIBRE);
        return mesaRepository.save(mesa);
    }

    @Test
    void meseroOpensConsumoLocalOrder() throws Exception {
        Mesa mesa = unaMesaLibre(5);

        mockMvc.perform(post("/api/ordenes")
                        .header("Authorization", "Bearer " + meseroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"CONSUMO_LOCAL\",\"mesaId\":\"" + mesa.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("CONSUMO_LOCAL"))
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.mesaNumero").value(5));

        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoMesa.OCUPADA);
    }

    @Test
    void meseroOpensParaLlevarOrderWithoutMesa() throws Exception {
        mockMvc.perform(post("/api/ordenes")
                        .header("Authorization", "Bearer " + meseroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"PARA_LLEVAR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mesaNumero").doesNotExist());
    }

    @Test
    void consumoLocalWithoutMesaFails() throws Exception {
        // POST {"tipo":"CONSUMO_LOCAL"} -> 400
    }

    @Test
    void paraLlevarWithMesaFails() throws Exception {
        // POST {"tipo":"PARA_LLEVAR","mesaId":"<alguna mesa libre>"} -> 400
    }

    @Test
    void openingOrderOnOccupiedMesaFails() throws Exception {
        Mesa mesa = unaMesaLibre(6);
        mesa.setEstado(EstadoMesa.OCUPADA);
        mesaRepository.save(mesa);

        mockMvc.perform(post("/api/ordenes")
                        .header("Authorization", "Bearer " + meseroToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"tipo\":\"CONSUMO_LOCAL\",\"mesaId\":\"" + mesa.getId() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void openingOrderWithUnknownMesaFails() throws Exception {
        // POST {"tipo":"CONSUMO_LOCAL","mesaId":"<random uuid>"} -> 400
    }

    @Test
    void nonMeseroCannotOpenOrder() throws Exception {
        // same valid PARA_LLEVAR payload, tokenFor(Rol.CAJERO) -> 403
    }

    @Test
    void unauthenticatedCannotOpenOrder() throws Exception {
        // same valid payload, no Authorization header -> 401
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=AbrirOrdenControllerTest`
Expected: FAIL — every case 404s (no route yet).

- [x] **Step 3: Write `V9__create_orden.sql`**

```sql
CREATE TABLE orden (
    id              UUID PRIMARY KEY,
    mesa_id         UUID REFERENCES mesa(id),
    mesero_id       UUID NOT NULL REFERENCES usuario(id),
    tipo            VARCHAR(20) NOT NULL CHECK (tipo IN ('CONSUMO_LOCAL','PARA_LLEVAR')),
    estado          VARCHAR(20) NOT NULL CHECK (estado IN ('ABIERTA','ENVIADA','CERRADA','PAGADA')),
    fecha_apertura  TIMESTAMPTZ NOT NULL,
    fecha_cierre    TIMESTAMPTZ
);
```

- [x] **Step 4: Add the `SecurityConfig` rules**

```java
.requestMatchers(HttpMethod.GET, "/api/ordenes/**").authenticated()
.requestMatchers("/api/ordenes/**").hasRole("MESERO")
```

- [x] **Step 5: Implement `TipoOrden`, `EstadoOrden`, `Orden`, `OrdenRepository`, `AbrirOrdenRequest`, `OrdenResponse` (with its nested `OrdenItemResponse`, defined now but unused until Task 2), `OrdenController.abrirOrden`**

```java
@Entity
@Table(name = "orden")
@Getter @Setter
public class Orden {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesero_id")
    private Usuario mesero;

    @Enumerated(EnumType.STRING)
    private TipoOrden tipo;

    @Enumerated(EnumType.STRING)
    private EstadoOrden estado;

    private Instant fechaApertura;
    private Instant fechaCierre;

}
```

```java
@RestController
@RequestMapping("/api/ordenes")
@Transactional
public class OrdenController {

    private final OrdenRepository ordenRepository;
    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;

    public OrdenController(OrdenRepository ordenRepository, MesaRepository mesaRepository,
                            UsuarioRepository usuarioRepository) {
        this.ordenRepository = ordenRepository;
        this.mesaRepository = mesaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenResponse abrirOrden(@RequestBody AbrirOrdenRequest request, Authentication authentication) {
        if (request.tipo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo requerido");
        }
        boolean esLocal = request.tipo() == TipoOrden.CONSUMO_LOCAL;

        Mesa mesa = null;
        if (esLocal) {
            if (request.mesaId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mesa requerida para consumo local");
            }
            mesa = mesaRepository.findById(request.mesaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "mesa invalida"));
            if (mesa.getEstado() == EstadoMesa.OCUPADA) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "la mesa ya esta ocupada");
            }
        } else if (request.mesaId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "para llevar no debe especificar mesa");
        }

        AuthenticatedUser caller = (AuthenticatedUser) authentication.getPrincipal();
        Usuario mesero = usuarioRepository.findById(caller.usuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        Orden orden = new Orden();
        orden.setMesa(mesa);
        orden.setMesero(mesero);
        orden.setTipo(request.tipo());
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.setFechaApertura(Instant.now());
        ordenRepository.save(orden);

        if (mesa != null) {
            mesa.setEstado(EstadoMesa.OCUPADA);
            mesaRepository.save(mesa);
        }

        return toResponse(orden, List.of());
    }

    private OrdenResponse toResponse(Orden orden, List<OrdenItemResponse> items) {
        return new OrdenResponse(orden.getId(),
                orden.getMesa() == null ? null : orden.getMesa().getNumero(),
                orden.getMesero().getId(), orden.getMesero().getNombre(),
                orden.getTipo(), orden.getEstado(), orden.getFechaApertura(), orden.getFechaCierre(),
                items);
    }

}
```

- [x] **Step 6: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=AbrirOrdenControllerTest`
Expected: PASS, all 8 cases.

- [x] **Step 7: Run the full suite**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test`
Expected: PASS — this task's tests plus every test from every prior plan, together.

- [x] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V9__create_orden.sql backend/src/main/java/com/posrestaurante/backend/orden backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java backend/src/test/java/com/posrestaurante/backend/orden
git commit -m "feat: mesero can open an orden on a mesa or para llevar"
```

---

## Task 2: Agregar platillos a una orden abierta (POST /api/ordenes/{id}/items)

**Files:**
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/EstadoOrdenItem.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/OrdenItem.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/OrdenItemRepository.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/dto/AgregarItemsRequest.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/orden/dto/OrdenItemResponse.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/orden/OrdenController.java` (add one endpoint; `toResponse` now actually uses `OrdenItemRepository`)
- Create: `backend/src/main/resources/db/migration/V10__create_orden_item.sql`
- Create: `backend/src/main/resources/db/migration/V11__create_orden_item_modificador.sql`
- Test: `backend/src/test/java/com/posrestaurante/backend/orden/AgregarItemsControllerTest.java`

**Interfaces:**
- Consumes: `Platillo`/`PlatilloRepository`, `Modificador` (menu-y-mesas); `Orden` (Task 1).
- Produces: `EstadoOrdenItem {PENDIENTE, EN_PREPARACION, LISTO, ENTREGADO}` (this task only ever creates items as `PENDIENTE` — the rest belong to the future cocina plan); `OrdenItem(UUID id, Orden orden, Platillo platillo, int cantidad, BigDecimal precioUnitario, EstadoOrdenItem estado, Set<Modificador> modificadores)` — no `ticket` field yet; `Ticket` doesn't exist until Task 3, which adds that field (and its column) once it does. `modificadores` is `@ManyToMany` through a new `orden_item_modificador` join table, same shape as `Platillo.modificadores`; `OrdenItemRepository extends JpaRepository<OrdenItem, UUID>` with `List<OrdenItem> findByOrden(Orden orden)`; `AgregarItemsRequest(List<ItemRequest> items)` where `ItemRequest(UUID platilloId, Integer cantidad, Set<UUID> modificadorIds)`; `OrdenItemResponse(UUID id, UUID platilloId, String platilloNombre, int cantidad, BigDecimal precioUnitario, EstadoOrdenItem estado, Set<UUID> modificadorIds, Set<String> modificadorNombres)`. `POST /api/ordenes/{id}/items` — `200` + the updated `OrdenResponse` (now with non-empty `items`) on success, `404` if `id` doesn't match any orden, `409` if the orden's `estado` isn't `ABIERTA` (the `ENVIADA` case is Task 4), `400` if `items` is empty/missing, if any `cantidad` isn't `>= 1`, if any `platilloId` doesn't match an **active** `Platillo`, or if any `modificadorId` isn't in *that platillo's* allowed set.

- [x] **Step 1: Write the failing controller test**

```java
class AgregarItemsControllerTest extends AbstractIntegrationTest {

    @Autowired OrdenRepository ordenRepository;
    @Autowired OrdenItemRepository ordenItemRepository;
    @Autowired PlatilloRepository platilloRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired ModificadorRepository modificadorRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private Orden unaOrdenAbierta() {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.setFechaApertura(Instant.now());
        return ordenRepository.save(orden);
    }

    private Platillo unPlatillo(boolean activo, Modificador... modificadoresPermitidos) {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);

        Platillo platillo = new Platillo();
        platillo.setNombre("Tacos");
        platillo.setPrecio(new BigDecimal("85.00"));
        platillo.setCategoria(categoria);
        platillo.setEstacion(Estacion.P2);
        platillo.setActivo(activo);
        platillo.setModificadores(new HashSet<>(List.of(modificadoresPermitidos)));
        return platilloRepository.save(platillo);
    }

    @Test
    void meseroAddsItemToOpenOrder() throws Exception {
        Orden orden = unaOrdenAbierta();
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].platilloNombre").value("Tacos"))
                .andExpect(jsonPath("$.items[0].cantidad").value(2))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(85.00))
                .andExpect(jsonPath("$.items[0].estado").value("PENDIENTE"));

        assertThat(ordenItemRepository.findByOrden(orden)).hasSize(1);
    }

    @Test
    void addingModificadorNotAllowedForThatPlatilloFails() throws Exception {
        Orden orden = unaOrdenAbierta();
        Modificador sinCebolla = new Modificador();
        sinCebolla.setNombre("Sin cebolla");
        modificadorRepository.save(sinCebolla);
        Platillo platillo = unPlatillo(true); // no allowed modificadores

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId()
                                + "\",\"cantidad\":1,\"modificadorIds\":[\"" + sinCebolla.getId() + "\"]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addingInactivePlatilloFails() throws Exception {
        Orden orden = unaOrdenAbierta();
        Platillo platillo = unPlatillo(false);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addingToAClosedOrderFails() throws Exception {
        Orden orden = unaOrdenAbierta();
        orden.setEstado(EstadoOrden.CERRADA);
        ordenRepository.save(orden);
        Platillo platillo = unPlatillo(true);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":1}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownOrdenIdReturns404() throws Exception {
        // POST /api/ordenes/<random uuid>/items {...} -> 404
    }

    @Test
    void invalidCantidadFails() throws Exception {
        // cantidad: 0 -> 400
    }

    @Test
    void nonMeseroCannotAddItems() throws Exception {
        // tokenFor(Rol.CHEF) -> 403
    }

    @Test
    void unauthenticatedCannotAddItems() throws Exception {
        // no Authorization header -> 401
    }

}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=AgregarItemsControllerTest`
Expected: FAIL — every case 404s.

- [x] **Step 3: Write `V10__create_orden_item.sql` and `V11__create_orden_item_modificador.sql`**

```sql
-- V10__create_orden_item.sql
-- No ticket_id column yet — Ticket doesn't exist until Task 3, which adds that
-- column (and its FK) via ALTER TABLE once `ticket` exists, rather than this
-- migration forward-referencing a table that isn't there yet.
CREATE TABLE orden_item (
    id              UUID PRIMARY KEY,
    orden_id        UUID NOT NULL REFERENCES orden(id),
    platillo_id     UUID NOT NULL REFERENCES platillo(id),
    cantidad        INT NOT NULL,
    precio_unitario NUMERIC(10,2) NOT NULL,
    estado          VARCHAR(20) NOT NULL CHECK (estado IN ('PENDIENTE','EN_PREPARACION','LISTO','ENTREGADO'))
);
```

```sql
-- V11__create_orden_item_modificador.sql
CREATE TABLE orden_item_modificador (
    orden_item_id   UUID NOT NULL REFERENCES orden_item(id),
    modificador_id  UUID NOT NULL REFERENCES modificador(id),
    PRIMARY KEY (orden_item_id, modificador_id)
);
```

- [x] **Step 4: Implement `EstadoOrdenItem`, `OrdenItem`, `OrdenItemRepository`, `AgregarItemsRequest`, `OrdenItemResponse`, `OrdenController.agregarItems`**

```java
@Entity
@Table(name = "orden_item")
@Getter @Setter
public class OrdenItem {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id")
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platillo_id")
    private Platillo platillo;

    // no `ticket` field yet — Task 3 adds it once the Ticket table exists

    private int cantidad;
    private BigDecimal precioUnitario;

    @Enumerated(EnumType.STRING)
    private EstadoOrdenItem estado;

    @ManyToMany
    @JoinTable(
            name = "orden_item_modificador",
            joinColumns = @JoinColumn(name = "orden_item_id"),
            inverseJoinColumns = @JoinColumn(name = "modificador_id"))
    private Set<Modificador> modificadores = new HashSet<>();

}
```

```java
// in OrdenController:

@PostMapping("/{id}/items")
public OrdenResponse agregarItems(@PathVariable UUID id, @RequestBody AgregarItemsRequest request) {
    Orden orden = ordenRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (orden.getEstado() != EstadoOrden.ABIERTA) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "la orden no acepta mas platillos en su estado actual");
    }
    if (request.items() == null || request.items().isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items requeridos");
    }

    for (ItemRequest itemRequest : request.items()) {
        crearOrdenItem(orden, itemRequest);
    }

    return toResponse(orden, obtenerItems(orden));
}

private OrdenItem crearOrdenItem(Orden orden, ItemRequest itemRequest) {
    if (itemRequest.cantidad() == null || itemRequest.cantidad() <= 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cantidad invalida");
    }
    Platillo platillo = platilloRepository.findById(itemRequest.platilloId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "platillo invalido"));
    if (!platillo.isActivo()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platillo no disponible");
    }

    Set<UUID> modificadorIds = itemRequest.modificadorIds() == null ? Set.of() : itemRequest.modificadorIds();
    Set<UUID> permitidos = platillo.getModificadores().stream()
            .map(Modificador::getId).collect(Collectors.toSet());
    if (!permitidos.containsAll(modificadorIds)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modificador no disponible para este platillo");
    }
    Set<Modificador> modificadores = platillo.getModificadores().stream()
            .filter(m -> modificadorIds.contains(m.getId()))
            .collect(Collectors.toSet());

    OrdenItem item = new OrdenItem();
    item.setOrden(orden);
    item.setPlatillo(platillo);
    item.setCantidad(itemRequest.cantidad());
    item.setPrecioUnitario(platillo.getPrecio());
    item.setEstado(EstadoOrdenItem.PENDIENTE);
    item.setModificadores(modificadores);
    return ordenItemRepository.save(item);
}

private List<OrdenItemResponse> obtenerItems(Orden orden) {
    return ordenItemRepository.findByOrden(orden).stream().map(this::toItemResponse).toList();
}

private OrdenItemResponse toItemResponse(OrdenItem item) {
    Set<UUID> modIds = item.getModificadores().stream().map(Modificador::getId).collect(Collectors.toSet());
    Set<String> modNombres = item.getModificadores().stream().map(Modificador::getNombre).collect(Collectors.toSet());
    return new OrdenItemResponse(item.getId(), item.getPlatillo().getId(), item.getPlatillo().getNombre(),
            item.getCantidad(), item.getPrecioUnitario(), item.getEstado(), modIds, modNombres);
}
```

Also update `abrirOrden`'s call to `toResponse(orden, List.of())` — leave it as-is; `List.of()` is correct there since a just-opened orden has no items yet.

- [x] **Step 5: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=AgregarItemsControllerTest`
Expected: PASS, all 8 cases.

- [x] **Step 6: Run the full suite**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test`
Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V10__create_orden_item.sql backend/src/main/resources/db/migration/V11__create_orden_item_modificador.sql backend/src/main/java/com/posrestaurante/backend/orden backend/src/test/java/com/posrestaurante/backend/orden/AgregarItemsControllerTest.java
git commit -m "feat: mesero can add platillos to an open orden"
```

---

## Task 3: Enviar orden a cocina (POST /api/ordenes/{id}/enviar)

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__create_ticket.sql` (creates `ticket`, then adds `orden_item.ticket_id` via `ALTER TABLE` now that `ticket` exists to reference)
- Create: `backend/src/main/java/com/posrestaurante/backend/ticket/TicketEstado.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/ticket/Ticket.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/ticket/TicketRepository.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/orden/OrdenItem.java` (add the `@ManyToOne Ticket ticket` field Task 2 deliberately left out)
- Modify: `backend/src/main/java/com/posrestaurante/backend/orden/OrdenController.java` (add one endpoint + the shared `generarTickets` helper)
- Modify: `backend/src/main/java/com/posrestaurante/backend/orden/OrdenItemRepository.java` (add `findByOrdenAndTicketIsNull`)
- Test: `backend/src/test/java/com/posrestaurante/backend/orden/EnviarOrdenControllerTest.java`

**Interfaces:**
- Consumes: `OrdenItem`/`OrdenItemRepository` (Task 2); `Estacion` (backend-fundamentos, reused — same enum `Platillo.estacion` already uses).
- Produces: `TicketEstado {PENDIENTE, IMPRESO}` (only `PENDIENTE` is ever set — `IMPRESO` is Fase 2 printer integration, spec §7); `Ticket(UUID id, Orden orden, Estacion estacion, Instant fechaGenerado, TicketEstado estado)`; `TicketRepository extends JpaRepository<Ticket, UUID>`; `List<OrdenItem> OrdenItemRepository.findByOrdenAndTicketIsNull(Orden orden)`. `POST /api/ordenes/{id}/enviar` — `200` + the updated `OrdenResponse` on success (orden now `ENVIADA`, every previously-ticketless item now has a `ticket`), `404` if `id` doesn't match any orden, `409` if the orden's `estado` isn't `ABIERTA`, `400` if there are no ticketless items to send. Also produces the private `generarTickets(Orden, List<OrdenItem>)` helper — group by `estacion`, one `Ticket` per represented `estacion`, reused as-is by Task 4.

- [x] **Step 1: Write the failing controller test**

```java
class EnviarOrdenControllerTest extends AbstractIntegrationTest {

    @Autowired OrdenRepository ordenRepository;
    @Autowired OrdenItemRepository ordenItemRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired PlatilloRepository platilloRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private Categoria unaCategoria() {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        return categoriaRepository.save(categoria);
    }

    private Platillo unPlatillo(Estacion estacion) {
        Platillo platillo = new Platillo();
        platillo.setNombre("Item");
        platillo.setPrecio(new BigDecimal("50.00"));
        platillo.setCategoria(unaCategoria());
        platillo.setEstacion(estacion);
        platillo.setActivo(true);
        return platilloRepository.save(platillo);
    }

    private Orden unaOrdenAbiertaConItems(Platillo... platillos) {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.setFechaApertura(Instant.now());
        ordenRepository.save(orden);

        for (Platillo platillo : platillos) {
            OrdenItem item = new OrdenItem();
            item.setOrden(orden);
            item.setPlatillo(platillo);
            item.setCantidad(1);
            item.setPrecioUnitario(platillo.getPrecio());
            item.setEstado(EstadoOrdenItem.PENDIENTE);
            ordenItemRepository.save(item);
        }
        return orden;
    }

    @Test
    void sendingOrderGroupsItemsIntoOneTicketPerEstacion() throws Exception {
        Orden orden = unaOrdenAbiertaConItems(unPlatillo(Estacion.P1), unPlatillo(Estacion.P2));

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/enviar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADA"));

        assertThat(ticketRepository.findAll()).hasSize(2);
        assertThat(ordenItemRepository.findByOrden(orden))
                .allSatisfy(item -> assertThat(item.getTicket()).isNotNull());
    }

    @Test
    void sendingOrderWithOnlyOneEstacionCreatesOneTicket() throws Exception {
        Orden orden = unaOrdenAbiertaConItems(unPlatillo(Estacion.P2), unPlatillo(Estacion.P2));

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/enviar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isOk());

        assertThat(ticketRepository.findAll()).hasSize(1);
    }

    @Test
    void sendingAlreadySentOrderFails() throws Exception {
        Orden orden = unaOrdenAbiertaConItems(unPlatillo(Estacion.P1));
        orden.setEstado(EstadoOrden.ENVIADA);
        ordenRepository.save(orden);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/enviar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isConflict());
    }

    @Test
    void sendingOrderWithNoPendingItemsFails() throws Exception {
        Orden orden = unaOrdenAbiertaConItems(); // no items

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/enviar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrdenIdReturns404() throws Exception {
        // POST /api/ordenes/<random uuid>/enviar -> 404
    }

    @Test
    void nonMeseroCannotSendOrder() throws Exception {
        // tokenFor(Rol.DUENO) -> 403
    }

    @Test
    void unauthenticatedCannotSendOrder() throws Exception {
        // no Authorization header -> 401
    }

}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=EnviarOrdenControllerTest`
Expected: FAIL — every case 404s.

- [x] **Step 3: Write `V12__create_ticket.sql`**

```sql
CREATE TABLE ticket (
    id              UUID PRIMARY KEY,
    orden_id        UUID NOT NULL REFERENCES orden(id),
    estacion        VARCHAR(10) NOT NULL CHECK (estacion IN ('P1','P2')),
    fecha_generado  TIMESTAMPTZ NOT NULL,
    estado          VARCHAR(10) NOT NULL CHECK (estado IN ('PENDIENTE','IMPRESO'))
);

ALTER TABLE orden_item
    ADD COLUMN ticket_id UUID REFERENCES ticket(id);
```

- [x] **Step 4: Implement `TicketEstado`, `Ticket`, `TicketRepository`, `OrdenItemRepository.findByOrdenAndTicketIsNull`, `OrdenController.enviarOrden` and the shared `generarTickets` helper**

```java
@Entity
@Table(name = "ticket")
@Getter @Setter
public class Ticket {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id")
    private Orden orden;

    @Enumerated(EnumType.STRING)
    private Estacion estacion;

    private Instant fechaGenerado;

    @Enumerated(EnumType.STRING)
    private TicketEstado estado;

}
```

```java
// in OrdenItem.java — add alongside the existing `platillo` field:

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "ticket_id")
private Ticket ticket;
```

```java
// in OrdenController:

@PostMapping("/{id}/enviar")
public OrdenResponse enviarOrden(@PathVariable UUID id) {
    Orden orden = ordenRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (orden.getEstado() != EstadoOrden.ABIERTA) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "la orden ya fue enviada o no esta abierta");
    }
    List<OrdenItem> pendientes = ordenItemRepository.findByOrdenAndTicketIsNull(orden);
    if (pendientes.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no hay platillos para enviar");
    }

    generarTickets(orden, pendientes);
    orden.setEstado(EstadoOrden.ENVIADA);
    ordenRepository.save(orden);

    return toResponse(orden, obtenerItems(orden));
}

private void generarTickets(Orden orden, List<OrdenItem> items) {
    Map<Estacion, Ticket> ticketsPorEstacion = new HashMap<>();
    for (OrdenItem item : items) {
        Estacion estacion = item.getPlatillo().getEstacion();
        Ticket ticket = ticketsPorEstacion.computeIfAbsent(estacion, e -> {
            Ticket nuevo = new Ticket();
            nuevo.setOrden(orden);
            nuevo.setEstacion(e);
            nuevo.setFechaGenerado(Instant.now());
            nuevo.setEstado(TicketEstado.PENDIENTE);
            return ticketRepository.save(nuevo);
        });
        item.setTicket(ticket);
        ordenItemRepository.save(item);
    }
}
```

(Add `TicketRepository ticketRepository` as a new constructor-injected dependency of `OrdenController`.)

- [x] **Step 5: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=EnviarOrdenControllerTest`
Expected: PASS, all 7 cases.

- [x] **Step 6: Run the full suite**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test`
Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V12__create_ticket.sql backend/src/main/java/com/posrestaurante/backend/ticket backend/src/main/java/com/posrestaurante/backend/orden backend/src/test/java/com/posrestaurante/backend/orden/EnviarOrdenControllerTest.java
git commit -m "feat: mesero can send an orden to cocina, splitting it into one ticket per estacion"
```

---

## Task 4: Agregar platillos después del envío (extends Task 2's endpoint)

**Files:**
- Modify: `backend/src/main/java/com/posrestaurante/backend/orden/OrdenController.java` (widen `agregarItems`'s state check; call `generarTickets` when the orden is already `ENVIADA`)
- Modify: `backend/src/test/java/com/posrestaurante/backend/orden/AgregarItemsControllerTest.java` (add test methods — same file, same endpoint, new behavior for a state that didn't exist to test against until Task 3)

**Interfaces:**
- Consumes: `generarTickets` (Task 3).
- Produces no new types. `POST /api/ordenes/{id}/items` now also accepts an orden in `ENVIADA` (in addition to `ABIERTA`); when it's `ENVIADA`, every item just created in this call is immediately grouped into ticket(s) by `estacion` via `generarTickets`, same grouping rule as Task 3, applied only to *this call's* items (not re-grouping anything already ticketed).

- [x] **Step 1: Write the failing test additions**

```java
// added to AgregarItemsControllerTest:

@Test
void addingItemToSentOrderImmediatelyCreatesNewTicketForItsEstacion() throws Exception {
    Orden orden = unaOrdenAbierta();
    orden.setEstado(EstadoOrden.ENVIADA);
    ordenRepository.save(orden);
    Platillo platillo = unPlatillo(true); // estacion P2 per the helper's default

    mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                    .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                    .contentType(APPLICATION_JSON)
                    .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":1}]}"))
            .andExpect(status().isOk());

    List<OrdenItem> items = ordenItemRepository.findByOrden(orden);
    assertThat(items).hasSize(1);
    assertThat(items.get(0).getTicket()).isNotNull();
    assertThat(items.get(0).getTicket().getEstacion()).isEqualTo(Estacion.P2);
}

@Test
void addingItemsOfDifferentEstacionesToSentOrderCreatesTwoNewTickets() throws Exception {
    Orden orden = unaOrdenAbierta();
    orden.setEstado(EstadoOrden.ENVIADA);
    ordenRepository.save(orden);
    Platillo p1 = unPlatillo(true);
    p1.setEstacion(Estacion.P1);
    platilloRepository.save(p1);
    Platillo p2 = unPlatillo(true);

    mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                    .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                    .contentType(APPLICATION_JSON)
                    .content("{\"items\":[{\"platilloId\":\"" + p1.getId() + "\",\"cantidad\":1},"
                            + "{\"platilloId\":\"" + p2.getId() + "\",\"cantidad\":1}]}"))
            .andExpect(status().isOk());

    assertThat(ticketRepository.findAll()).hasSize(2);
}

@Test
void addingItemToOpenOrderStillDoesNotCreateATicket() throws Exception {
    // regression: Task 2's core behavior on an ABIERTA orden must still hold
    Orden orden = unaOrdenAbierta();
    Platillo platillo = unPlatillo(true);

    mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/items")
                    .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                    .contentType(APPLICATION_JSON)
                    .content("{\"items\":[{\"platilloId\":\"" + platillo.getId() + "\",\"cantidad\":1}]}"))
            .andExpect(status().isOk());

    assertThat(ticketRepository.findAll()).isEmpty();
}
```

(`AgregarItemsControllerTest` needs a new `@Autowired TicketRepository ticketRepository` field for these.)

- [x] **Step 2: Run the test to verify the new cases fail**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=AgregarItemsControllerTest`
Expected: FAIL on the two new `ENVIADA` cases (`409` instead of `200` — Task 2's check still only accepts `ABIERTA`); the regression case passes already.

- [x] **Step 3: Widen `OrdenController.agregarItems`**

```java
@PostMapping("/{id}/items")
public OrdenResponse agregarItems(@PathVariable UUID id, @RequestBody AgregarItemsRequest request) {
    Orden orden = ordenRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (orden.getEstado() != EstadoOrden.ABIERTA && orden.getEstado() != EstadoOrden.ENVIADA) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "la orden no acepta mas platillos en su estado actual");
    }
    if (request.items() == null || request.items().isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items requeridos");
    }

    List<OrdenItem> nuevos = new ArrayList<>();
    for (ItemRequest itemRequest : request.items()) {
        nuevos.add(crearOrdenItem(orden, itemRequest));
    }

    if (orden.getEstado() == EstadoOrden.ENVIADA) {
        generarTickets(orden, nuevos);
    }

    return toResponse(orden, obtenerItems(orden));
}
```

(`crearOrdenItem` must return the saved `OrdenItem` — it already does, from Task 2 — so `nuevos` collects exactly the items this call created, nothing pre-existing.)

- [x] **Step 4: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=AgregarItemsControllerTest`
Expected: PASS, all 11 cases (8 from Task 2 + 3 new).

- [x] **Step 5: Run the full suite**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test`
Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/orden/OrdenController.java backend/src/test/java/com/posrestaurante/backend/orden/AgregarItemsControllerTest.java
git commit -m "feat: adding platillos to an already-sent orden immediately tickets them"
```

---

## Task 5: Cerrar cuenta (POST /api/ordenes/{id}/cerrar)

**Files:**
- Modify: `backend/src/main/java/com/posrestaurante/backend/orden/OrdenController.java` (add one endpoint)
- Test: `backend/src/test/java/com/posrestaurante/backend/orden/CerrarOrdenControllerTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `POST /api/ordenes/{id}/cerrar` — `200` + the updated `OrdenResponse` (`estado` now `CERRADA`, `fechaCierre` set) on success, `404` if `id` doesn't match any orden, `409` if the orden's `estado` isn't `ENVIADA`. **Does not touch `Mesa`** — see Global Constraints; a test here explicitly asserts the mesa is still `OCUPADA` afterward, since that's the one thing in this task someone might "fix" by reflex.

- [x] **Step 1: Write the failing controller test**

```java
class CerrarOrdenControllerTest extends AbstractIntegrationTest {

    @Autowired OrdenRepository ordenRepository;
    @Autowired MesaRepository mesaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
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
    void meseroClosesSentOrderAndMesaStaysOcupada() throws Exception {
        Mesa mesa = new Mesa();
        mesa.setNumero(11);
        mesa.setEstado(EstadoMesa.OCUPADA);
        mesaRepository.save(mesa);
        Orden orden = unaOrden(EstadoOrden.ENVIADA, mesa);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/cerrar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"));

        assertThat(mesaRepository.findById(mesa.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoMesa.OCUPADA);
    }

    @Test
    void closingOpenOrderFails() throws Exception {
        Orden orden = unaOrden(EstadoOrden.ABIERTA, null);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/cerrar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isConflict());
    }

    @Test
    void closingAlreadyClosedOrderFails() throws Exception {
        Orden orden = unaOrden(EstadoOrden.CERRADA, null);

        mockMvc.perform(post("/api/ordenes/" + orden.getId() + "/cerrar")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownOrdenIdReturns404() throws Exception {
        // POST /api/ordenes/<random uuid>/cerrar -> 404
    }

    @Test
    void nonMeseroCannotCloseOrder() throws Exception {
        // tokenFor(Rol.CAJERO) -> 403
    }

    @Test
    void unauthenticatedCannotCloseOrder() throws Exception {
        // no Authorization header -> 401
    }

}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=CerrarOrdenControllerTest`
Expected: FAIL — every case 404s.

- [x] **Step 3: Implement `OrdenController.cerrarOrden`**

```java
@PostMapping("/{id}/cerrar")
public OrdenResponse cerrarOrden(@PathVariable UUID id) {
    Orden orden = ordenRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (orden.getEstado() != EstadoOrden.ENVIADA) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "solo se puede cerrar una orden enviada");
    }
    orden.setEstado(EstadoOrden.CERRADA);
    orden.setFechaCierre(Instant.now());
    ordenRepository.save(orden);
    return toResponse(orden, obtenerItems(orden));
}
```

- [x] **Step 4: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=CerrarOrdenControllerTest`
Expected: PASS, all 6 cases.

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/orden/OrdenController.java backend/src/test/java/com/posrestaurante/backend/orden/CerrarOrdenControllerTest.java
git commit -m "feat: mesero can cerrar an orden, handing it off to caja"
```

---

## Task 6: Listar y consultar órdenes (GET /api/ordenes, GET /api/ordenes/{id})

**Files:**
- Modify: `backend/src/main/java/com/posrestaurante/backend/orden/OrdenController.java` (add two endpoints)
- Test: `backend/src/test/java/com/posrestaurante/backend/orden/ListarOrdenesControllerTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `GET /api/ordenes` — `200` + `List<OrdenResponse>` of every orden regardless of `estado`, items nested, any authenticated role (already permitted by Task 1's `SecurityConfig` rule — no `SecurityConfig` change needed here); `GET /api/ordenes/{id}` — `200` + one `OrdenResponse` with full item/modificador detail, `404` if unknown.

- [ ] **Step 1: Write the failing test**

```java
class ListarOrdenesControllerTest extends AbstractIntegrationTest {

    @Autowired OrdenRepository ordenRepository;
    @Autowired OrdenItemRepository ordenItemRepository;
    @Autowired PlatilloRepository platilloRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private Orden unaOrdenConUnItem() {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        Orden orden = new Orden();
        orden.setMesero(mesero);
        orden.setTipo(TipoOrden.PARA_LLEVAR);
        orden.setEstado(EstadoOrden.ABIERTA);
        orden.setFechaApertura(Instant.now());
        ordenRepository.save(orden);

        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);
        Platillo platillo = new Platillo();
        platillo.setNombre("Tacos");
        platillo.setPrecio(new BigDecimal("85.00"));
        platillo.setCategoria(categoria);
        platillo.setEstacion(Estacion.P2);
        platillo.setActivo(true);
        platilloRepository.save(platillo);

        OrdenItem item = new OrdenItem();
        item.setOrden(orden);
        item.setPlatillo(platillo);
        item.setCantidad(3);
        item.setPrecioUnitario(platillo.getPrecio());
        item.setEstado(EstadoOrdenItem.PENDIENTE);
        ordenItemRepository.save(item);

        return orden;
    }

    @Test
    void anyAuthenticatedRoleCanListOrdenesWithNestedItems() throws Exception {
        unaOrdenConUnItem();

        mockMvc.perform(get("/api/ordenes").header("Authorization", "Bearer " + tokenFor(Rol.CHEF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.items[0].platilloNombre == 'Tacos')]").exists());
    }

    @Test
    void anyAuthenticatedRoleCanGetOrdenById() throws Exception {
        Orden orden = unaOrdenConUnItem();

        mockMvc.perform(get("/api/ordenes/" + orden.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.CAJERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].cantidad").value(3));
    }

    @Test
    void gettingUnknownOrdenReturns404() throws Exception {
        mockMvc.perform(get("/api/ordenes/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedCannotListOrdenes() throws Exception {
        mockMvc.perform(get("/api/ordenes")).andExpect(status().isUnauthorized());
    }

}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=ListarOrdenesControllerTest`
Expected: FAIL (404 — no `GET` routes yet).

- [ ] **Step 3: Implement `OrdenController.listarOrdenes` and `obtenerOrden`**

```java
@GetMapping
public List<OrdenResponse> listarOrdenes() {
    return ordenRepository.findAll().stream()
            .map(orden -> toResponse(orden, obtenerItems(orden)))
            .toList();
}

@GetMapping("/{id}")
public OrdenResponse obtenerOrden(@PathVariable UUID id) {
    Orden orden = ordenRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return toResponse(orden, obtenerItems(orden));
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=ListarOrdenesControllerTest`
Expected: PASS, all 4 cases.

- [ ] **Step 5: Run the full suite**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test`
Expected: PASS — every test from this plan and every prior plan, together.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/orden/OrdenController.java backend/src/test/java/com/posrestaurante/backend/orden/ListarOrdenesControllerTest.java
git commit -m "feat: any authenticated role can list and view ordenes"
```

---

## Non-Goals (this plan)

- **Cocina**: a chef's per-estación ticket screen, and marking `OrdenItem.estado` as `EN_PREPARACION`/`LISTO`/`ENTREGADO` (historias #17–19). This plan creates the rows with the right shape; nothing reads or mutates them from the kitchen side yet. Next plan.
- **Caja**: cobro, método de pago, descuento de inventario por receta, generating the consolidated Caja ticket, and marking `ORDEN.estado = PAGADA` (historias #21–24, flujo §4 steps 8–9). Also the plan responsible for finally setting `Mesa.estado = LIBRE`.
- **Cancelar una orden** — no story asks for it, and the spec only ever discusses cancellation in passing (§4 step 8, in the context of inventory *not* being affected by one) without specifying who can cancel or when. Out of scope until a story actually asks for it.
- **Editing or removing an already-added `OrdenItem`** (wrong quantity, wrong modificador) — not in any user story; the only supported corrections right now are "add more" (Task 2/4). A mis-ordered item has no fix in this plan.
- **Impresión térmica real de tickets** — `Ticket.estado` exists with `IMPRESO` as a value the spec's schema defines, but nothing in this plan (or planned soon) ever sets it; that's Fase 2 (spec §7).
