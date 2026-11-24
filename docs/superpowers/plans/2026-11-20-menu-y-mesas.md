# Menú y Mesas — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the dueño CRUD over the two things a mesero will need before a single order can ever be taken: the numbered table list (`Mesa`) and the menu (`Platillo`, with its `Categoria` and `Modificador` support data). This is the last purely-administrative plan before the order-taking flow — once this lands, every piece of static setup data the rest of the system depends on exists.

**Architecture:** Four new entities, one package each (`mesa`, `categoria`, `modificador`, `platillo`), following the exact shape of `docs/superpowers/plans/2026-11-19-aprovisionamiento-personal.md`'s `UsuarioAdminController`: a controller per entity, DTOs that never leak more than the client needs, `ResponseStatusException` for validation failures. The one structural difference from that plan: **listing** is open to any authenticated role here, not `DUENO`-only — a mesero will need to read the menu and the table list once the order-taking plan exists, and there's no sensitive data in either. Mutating endpoints (create/edit/activate/delete) stay `DUENO`-only throughout.

**Tech Stack:** Same as backend-fundamentos and aprovisionamiento-personal — Java 21, Spring Boot 3.5.x, Spring Data JPA, Spring Security, Testcontainers-backed integration tests. No new dependencies.

**Spec:** `docs/2026-11-13-pos-restaurante-design.md` — §5 "Menú e inventario", §7 "Alcance" ("Gestión de mesas", "Gestión de menú"), §8 "Modelo de datos" (`MESA`, `CATEGORIA`, `PLATILLO`, `MODIFICADOR`, `PLATILLO_MODIFICADOR`), historia de usuario #2 ("crear, editar y desactivar platillos...") and #9 ("agregar o quitar mesas"), §7 anti-meta ("que modificar un platillo implique responder preguntas una por una en vez de un panel de toggles" — this plan is the data layer that panel reads from, not the panel itself). Depends on `docs/superpowers/plans/2026-11-19-backend-fundamentos.md` for `JwtService`/`SecurityConfig`/`JwtAuthenticationFilter`, already implemented.

## Global Constraints

- `Platillo.estacion` reuses the existing `com.posrestaurante.backend.usuario.Estacion` enum (`P1`/`P2`) — same domain concept (which kitchen station), not duplicated. Unlike `Usuario.estacion`, which is nullable for every role except `CHEF`, `Platillo.estacion` is **always required** — every dish belongs to exactly one station (spec §8: `PLATILLO.estacion` has no "nullable" annotation, unlike `USUARIO.estacion`).
- `Platillo.activo` follows the same soft-delete pattern as `Usuario.activo` (deactivate, never delete) — a future plan's orders will reference platillos by id, and a deleted row would break that history.
- `Mesa` has no `activo` flag in the spec's data model (§8) — removing one is a **hard delete** in this plan. That's safe only because nothing references `Mesa` yet; the moment a future órdenes plan adds `ORDEN.mesa_id`, deleting a mesa with order history will need revisiting (soft-delete, or a guard against deleting a referenced row). Not solved here — flagged for that plan.
- `Mesa.estado` (`LIBRE`/`OCUPADA`) is not settable through any endpoint in this plan. It's derived state a future toma-de-órdenes plan will manage (open an order on a mesa → `OCUPADA`; close it → `LIBRE`). Every mesa this plan creates starts `LIBRE` and stays there.
- `GET` on all four resources (`/api/mesas`, `/api/categorias`, `/api/modificadores`, `/api/platillos`) is open to **any authenticated role**, not `DUENO`-only — see Architecture. Every other verb on those paths (`POST`, `PATCH`, `DELETE`) stays `DUENO`-only, same as the usuarios-admin plan.
- `GET /api/platillos` returns every platillo, active and inactive, same admin-listing precedent as `GET /api/usuarios`. Filtering to "only sellable right now" is the order-taking plan's job when it actually needs that, not this one's.
- `Categoria` and `Modificador` are intentionally minimal (just a `nombre`) — the spec names no user story for managing them directly; they only exist because `Platillo` needs something to reference. No rename/delete endpoints for either in this plan.

## Review Focus

- `precio` must be validated as strictly positive (`> 0`), not just "present" — a platillo priced at `0` or negative is a business-rule bug that will silently corrupt totals the moment a future order/payment plan sums it up.
- `categoriaId` and every id in `modificadorIds` must be validated as **existing rows**, and a missing/invalid one must 400, not 500. The easy mistake: calling `categoriaRepository.findById(null)` when the client simply omits `categoriaId` — that throws `IllegalArgumentException` from Spring Data, not a clean 400, unless it's null-checked first.
- `estacion` is required on every platillo — don't copy the "nullable unless CHEF" pattern from `Usuario.estacion` by reflex; here it's unconditional.
- Every mutating endpoint across all four controllers must reject non-`DUENO` (403) and unauthenticated (401) callers, while every `GET` on the same four resources must accept **any** authenticated role and only reject unauthenticated callers (401) — getting either direction backward is a real bug (hiding the menu from a mesero who needs it, or letting a mesero rewrite prices).
- `Mesa.numero` must be unique — two mesas sharing a visible number is exactly the kind of bug a mesero would discover mid-shift, not in testing.
- `EditarPlatilloRequest.modificadorIds`: a **missing** field (`null`) must leave the platillo's modificadores untouched; an **empty array** (`[]`) must clear them. These are different inputs with different meanings — don't collapse them into the same "falsy → do nothing" check.

---

## Task 1: Mesas (crear, listar, eliminar)

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__create_mesa.sql`
- Create: `backend/src/main/java/com/posrestaurante/backend/mesa/EstadoMesa.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/mesa/Mesa.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/mesa/MesaRepository.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/mesa/dto/CrearMesaRequest.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/mesa/dto/MesaResponse.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/mesa/MesaController.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/posrestaurante/backend/mesa/MesaControllerTest.java`

**Interfaces:**
- Consumes: `JwtService`, `Rol` (used by tests to mint tokens for various roles — no DB row needed, same pattern as the usuarios-admin plan's tests).
- Produces: `EstadoMesa {LIBRE, OCUPADA}`; `Mesa(UUID id, int numero, EstadoMesa estado)`; `MesaRepository extends JpaRepository<Mesa, UUID>` with `Optional<Mesa> findByNumero(int numero)`; `CrearMesaRequest(Integer numero)`; `MesaResponse(UUID id, int numero, EstadoMesa estado)`; `POST /api/mesas` — `201` + `MesaResponse` (`estado` always `LIBRE`) on success, `400` if `numero` is missing or `<= 0`, `409` if a mesa with that `numero` already exists; `GET /api/mesas` — `200` + `List<MesaResponse>`, any authenticated role; `DELETE /api/mesas/{id}` — `204` on success, `404` if `id` doesn't match any mesa. Adds, in this order (first match wins), to `SecurityConfig`'s `authorizeHttpRequests`: `.requestMatchers(HttpMethod.GET, "/api/mesas/**").authenticated()` then `.requestMatchers("/api/mesas/**").hasRole("DUENO")` — both before the existing `.anyRequest().authenticated()`.

- [x] **Step 1: Write the failing controller test**

```java
class MesaControllerTest extends AbstractIntegrationTest {

    @Autowired MesaRepository mesaRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void duenoCreatesMesaSuccessfully() throws Exception {
        mockMvc.perform(post("/api/mesas")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"numero\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(5))
                .andExpect(jsonPath("$.estado").value("LIBRE"));
    }

    @Test
    void creatingMesaWithDuplicateNumeroFails() throws Exception {
        // POST {"numero":7} twice with the dueno token -> second one is 409
    }

    @Test
    void creatingMesaWithInvalidNumeroFails() throws Exception {
        // POST {"numero":0} -> 400
    }

    @Test
    void anyAuthenticatedRoleCanListMesas() throws Exception {
        Mesa mesa = new Mesa();
        mesa.setNumero(3);
        mesa.setEstado(EstadoMesa.LIBRE);
        mesaRepository.save(mesa);

        mockMvc.perform(get("/api/mesas").header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numero == 3)]").exists());
    }

    @Test
    void duenoDeletesMesa() throws Exception {
        // create a mesa as dueno, DELETE it as dueno -> 204,
        // then GET /api/mesas no longer contains it
    }

    @Test
    void deletingUnknownMesaReturns404() throws Exception {
        // DELETE /api/mesas/<random uuid> -> 404
    }

    @Test
    void nonDuenoCannotCreateMesa() throws Exception {
        // POST {"numero":9} with tokenFor(Rol.CAJERO) -> 403
    }

    @Test
    void unauthenticatedCannotListMesas() throws Exception {
        // GET /api/mesas, no Authorization header -> 401
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=MesaControllerTest`
Expected: FAIL — every case 404s (no route yet).

- [x] **Step 3: Write `V4__create_mesa.sql`**

```sql
CREATE TABLE mesa (
    id      UUID PRIMARY KEY,
    numero  INT NOT NULL UNIQUE,
    estado  VARCHAR(10) NOT NULL CHECK (estado IN ('LIBRE','OCUPADA'))
);
```

- [x] **Step 4: Add the `SecurityConfig` rules**

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health", "/api/auth/login/**", "/api/auth/staff").permitAll()
        .requestMatchers("/api/usuarios/**").hasRole("DUENO")
        .requestMatchers(HttpMethod.GET, "/api/mesas/**").authenticated()
        .requestMatchers("/api/mesas/**").hasRole("DUENO")
        .anyRequest().authenticated())
```

- [x] **Step 5: Implement `EstadoMesa`, `Mesa`, `MesaRepository`, `CrearMesaRequest`, `MesaResponse`, `MesaController`**

```java
@RestController
@RequestMapping("/api/mesas")
public class MesaController {

    private final MesaRepository mesaRepository;

    public MesaController(MesaRepository mesaRepository) {
        this.mesaRepository = mesaRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MesaResponse crearMesa(@RequestBody CrearMesaRequest request) {
        if (request.numero() == null || request.numero() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numero invalido");
        }
        if (mesaRepository.findByNumero(request.numero()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ya existe una mesa con ese numero");
        }

        Mesa mesa = new Mesa();
        mesa.setNumero(request.numero());
        mesa.setEstado(EstadoMesa.LIBRE);
        mesaRepository.save(mesa);

        return toResponse(mesa);
    }

    @GetMapping
    public List<MesaResponse> listarMesas() {
        return mesaRepository.findAll().stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarMesa(@PathVariable UUID id) {
        if (!mesaRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        mesaRepository.deleteById(id);
    }

    private MesaResponse toResponse(Mesa mesa) {
        return new MesaResponse(mesa.getId(), mesa.getNumero(), mesa.getEstado());
    }

}
```

- [x] **Step 6: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=MesaControllerTest`
Expected: PASS, all 8 cases.

- [x] **Step 7: Run the full suite**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test`
Expected: PASS — this task's tests plus every test from backend-fundamentos and aprovisionamiento-personal, together.

- [x] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V4__create_mesa.sql backend/src/main/java/com/posrestaurante/backend/mesa backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java backend/src/test/java/com/posrestaurante/backend/mesa
git commit -m "feat: dueno can create, list, and delete mesas"
```

---

## Task 2: Categorías y Modificadores (datos de apoyo del menú)

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__create_categoria.sql`
- Create: `backend/src/main/resources/db/migration/V6__create_modificador.sql`
- Create: `backend/src/main/java/com/posrestaurante/backend/categoria/Categoria.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/categoria/CategoriaRepository.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/categoria/dto/CrearCategoriaRequest.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/categoria/dto/CategoriaResponse.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/categoria/CategoriaController.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/modificador/Modificador.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/modificador/ModificadorRepository.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/modificador/dto/CrearModificadorRequest.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/modificador/dto/ModificadorResponse.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/modificador/ModificadorController.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/posrestaurante/backend/categoria/CategoriaControllerTest.java`
- Test: `backend/src/test/java/com/posrestaurante/backend/modificador/ModificadorControllerTest.java`

**Interfaces:**
- Consumes: `JwtService`, `Rol`.
- Produces (identical shape, twice — once per entity): `Categoria(UUID id, String nombre)` / `Modificador(UUID id, String nombre)`; `*Repository extends JpaRepository<X, UUID>` (no custom queries needed — `Platillo`, built in Task 3, is what looks these up by id); `Crear*Request(String nombre)`; `*Response(UUID id, String nombre)`; `POST /api/categorias` / `POST /api/modificadores` — `201` + response on success, `400` if `nombre` is blank; `GET /api/categorias` / `GET /api/modificadores` — `200` + list, any authenticated role. Adds, before `.anyRequest().authenticated()`: `.requestMatchers(HttpMethod.GET, "/api/categorias/**", "/api/modificadores/**").authenticated()` then `.requestMatchers("/api/categorias/**", "/api/modificadores/**").hasRole("DUENO")`.

- [x] **Step 1: Write the failing tests**

```java
class CategoriaControllerTest extends AbstractIntegrationTest {

    @Autowired CategoriaRepository categoriaRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void duenoCreatesCategoriaSuccessfully() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Entradas\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Entradas"));
    }

    @Test
    void creatingCategoriaWithBlankNombreFails() throws Exception {
        // POST {"nombre":""} -> 400
    }

    @Test
    void anyAuthenticatedRoleCanListCategorias() throws Exception {
        // seed one via the repository, GET with tokenFor(Rol.CHEF) -> 200, contains it
    }

    @Test
    void nonDuenoCannotCreateCategoria() throws Exception {
        // POST with tokenFor(Rol.MESERO) -> 403
    }

    @Test
    void unauthenticatedCannotCreateCategoria() throws Exception {
        // POST with no Authorization header -> 401
    }
}
```

`ModificadorControllerTest` mirrors this exactly against `/api/modificadores`.

- [x] **Step 2: Run both test classes to verify they fail**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=CategoriaControllerTest,ModificadorControllerTest`
Expected: FAIL — every case 404s.

- [x] **Step 3: Write `V5__create_categoria.sql` and `V6__create_modificador.sql`**

```sql
-- V5__create_categoria.sql
CREATE TABLE categoria (
    id      UUID PRIMARY KEY,
    nombre  VARCHAR(80) NOT NULL
);
```

```sql
-- V6__create_modificador.sql
CREATE TABLE modificador (
    id      UUID PRIMARY KEY,
    nombre  VARCHAR(80) NOT NULL
);
```

- [x] **Step 4: Add the `SecurityConfig` rules**

```java
.requestMatchers(HttpMethod.GET, "/api/categorias/**", "/api/modificadores/**").authenticated()
.requestMatchers("/api/categorias/**", "/api/modificadores/**").hasRole("DUENO")
```

(placed with the other specific rules, before `.anyRequest().authenticated()`)

- [x] **Step 5: Implement `Categoria`/`CategoriaRepository`/DTOs/`CategoriaController` and `Modificador`/`ModificadorRepository`/DTOs/`ModificadorController`**

```java
@Entity
@Table(name = "categoria")
@Getter @Setter
public class Categoria {
    @Id
    private UUID id = UUID.randomUUID();
    private String nombre;
}
```

```java
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;

    public CategoriaController(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse crearCategoria(@RequestBody CrearCategoriaRequest request) {
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre requerido");
        }
        Categoria categoria = new Categoria();
        categoria.setNombre(request.nombre());
        categoriaRepository.save(categoria);
        return toResponse(categoria);
    }

    @GetMapping
    public List<CategoriaResponse> listarCategorias() {
        return categoriaRepository.findAll().stream().map(this::toResponse).toList();
    }

    private CategoriaResponse toResponse(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNombre());
    }

}
```

`Modificador`/`ModificadorRepository`/`ModificadorController` are the same shape against `/api/modificadores`.

- [x] **Step 6: Run both test classes and verify they pass**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=CategoriaControllerTest,ModificadorControllerTest`
Expected: PASS, all 10 cases (5 + 5).

- [x] **Step 7: Run the full suite**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test`
Expected: PASS.

- [x] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V5__create_categoria.sql backend/src/main/resources/db/migration/V6__create_modificador.sql backend/src/main/java/com/posrestaurante/backend/categoria backend/src/main/java/com/posrestaurante/backend/modificador backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java backend/src/test/java/com/posrestaurante/backend/categoria backend/src/test/java/com/posrestaurante/backend/modificador
git commit -m "feat: dueno can create and list categorias and modificadores"
```

---

## Task 3: Crear platillo (POST /api/platillos)

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__create_platillo.sql`
- Create: `backend/src/main/resources/db/migration/V8__create_platillo_modificador.sql`
- Create: `backend/src/main/java/com/posrestaurante/backend/platillo/Platillo.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/platillo/PlatilloRepository.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/platillo/dto/CrearPlatilloRequest.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/platillo/dto/PlatilloResponse.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/platillo/PlatilloController.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/posrestaurante/backend/platillo/CrearPlatilloControllerTest.java`

**Interfaces:**
- Consumes: `Categoria`/`CategoriaRepository`, `Modificador`/`ModificadorRepository` (Task 2); `com.posrestaurante.backend.usuario.Estacion` (backend-fundamentos).
- Produces: `Platillo(UUID id, String nombre, String descripcion, BigDecimal precio, Categoria categoria, Estacion estacion, Set<Modificador> modificadores, boolean activo)` — `categoria` is `@ManyToOne`, `modificadores` is `@ManyToMany` through the `platillo_modificador` join table; `PlatilloRepository extends JpaRepository<Platillo, UUID>`; `CrearPlatilloRequest(String nombre, String descripcion, BigDecimal precio, UUID categoriaId, Estacion estacion, Set<UUID> modificadorIds)`; `PlatilloResponse(UUID id, String nombre, String descripcion, BigDecimal precio, UUID categoriaId, String categoriaNombre, Estacion estacion, Set<UUID> modificadorIds, boolean activo)` (flat ids for `modificadorIds`, not nested objects — and `categoriaNombre` inlined so a client never needs a second round trip just to show it); `POST /api/platillos` — `201` + `PlatilloResponse` on success, `400` if `nombre` is blank, `precio` is missing or `<= 0`, `estacion` is missing, `categoriaId` is missing or doesn't match a `Categoria`, or any id in `modificadorIds` doesn't match a `Modificador`. Adds, before `.anyRequest().authenticated()`: `.requestMatchers(HttpMethod.GET, "/api/platillos/**").authenticated()` then `.requestMatchers("/api/platillos/**").hasRole("DUENO")` — this one rule pair also covers Tasks 4–6.

- [x] **Step 1: Write the failing controller test**

```java
class CrearPlatilloControllerTest extends AbstractIntegrationTest {

    @Autowired PlatilloRepository platilloRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired ModificadorRepository modificadorRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private Categoria unaCategoria() {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        return categoriaRepository.save(categoria);
    }

    @Test
    void duenoCreatesPlatilloSuccessfully() throws Exception {
        Categoria categoria = unaCategoria();

        mockMvc.perform(post("/api/platillos")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Tacos al pastor","precio":85.00,"categoriaId":"%s","estacion":"P2"}"""
                                .formatted(categoria.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Tacos al pastor"))
                .andExpect(jsonPath("$.categoriaNombre").value("Entradas"))
                .andExpect(jsonPath("$.estacion").value("P2"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void duenoCreatesPlatilloWithModificadores() throws Exception {
        // create a categoria and two modificadores, POST referencing both
        // modificadorIds -> 201, $.modificadorIds has both ids
    }

    @Test
    void creatingPlatilloWithUnknownCategoriaFails() throws Exception {
        // POST with a random categoriaId that doesn't exist -> 400
    }

    @Test
    void creatingPlatilloWithUnknownModificadorFails() throws Exception {
        // POST with a valid categoriaId but a random modificadorId -> 400
    }

    @Test
    void creatingPlatilloWithNonPositivePrecioFails() throws Exception {
        // POST with precio=0 -> 400
    }

    @Test
    void creatingPlatilloWithoutEstacionFails() throws Exception {
        // POST omitting estacion -> 400
    }

    @Test
    void nonDuenoCannotCreatePlatillo() throws Exception {
        // same valid payload, tokenFor(Rol.CAJERO) -> 403
    }

    @Test
    void unauthenticatedCannotCreatePlatillo() throws Exception {
        // same valid payload, no Authorization header -> 401
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=CrearPlatilloControllerTest`
Expected: FAIL — every case 404s.

- [x] **Step 3: Write `V7__create_platillo.sql` and `V8__create_platillo_modificador.sql`**

```sql
-- V7__create_platillo.sql
CREATE TABLE platillo (
    id            UUID PRIMARY KEY,
    nombre        VARCHAR(120) NOT NULL,
    descripcion   VARCHAR(500),
    precio        NUMERIC(10,2) NOT NULL,
    categoria_id  UUID NOT NULL REFERENCES categoria(id),
    estacion      VARCHAR(10) NOT NULL CHECK (estacion IN ('P1','P2')),
    activo        BOOLEAN NOT NULL DEFAULT TRUE
);
```

```sql
-- V8__create_platillo_modificador.sql
CREATE TABLE platillo_modificador (
    platillo_id    UUID NOT NULL REFERENCES platillo(id),
    modificador_id UUID NOT NULL REFERENCES modificador(id),
    PRIMARY KEY (platillo_id, modificador_id)
);
```

- [x] **Step 4: Add the `SecurityConfig` rules**

```java
.requestMatchers(HttpMethod.GET, "/api/platillos/**").authenticated()
.requestMatchers("/api/platillos/**").hasRole("DUENO")
```

- [x] **Step 5: Implement `Platillo`, `PlatilloRepository`, `CrearPlatilloRequest`, `PlatilloResponse`, `PlatilloController.crearPlatillo`**

```java
@Entity
@Table(name = "platillo")
@Getter @Setter
public class Platillo {

    @Id
    private UUID id = UUID.randomUUID();
    private String nombre;
    private String descripcion;
    private BigDecimal precio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    private Estacion estacion;

    @ManyToMany
    @JoinTable(
            name = "platillo_modificador",
            joinColumns = @JoinColumn(name = "platillo_id"),
            inverseJoinColumns = @JoinColumn(name = "modificador_id"))
    private Set<Modificador> modificadores = new HashSet<>();

    private boolean activo;

}
```

```java
@RestController
@RequestMapping("/api/platillos")
public class PlatilloController {

    private final PlatilloRepository platilloRepository;
    private final CategoriaRepository categoriaRepository;
    private final ModificadorRepository modificadorRepository;

    public PlatilloController(PlatilloRepository platilloRepository,
                               CategoriaRepository categoriaRepository,
                               ModificadorRepository modificadorRepository) {
        this.platilloRepository = platilloRepository;
        this.categoriaRepository = categoriaRepository;
        this.modificadorRepository = modificadorRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatilloResponse crearPlatillo(@RequestBody CrearPlatilloRequest request) {
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre requerido");
        }
        if (request.precio() == null || request.precio().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "precio debe ser positivo");
        }
        if (request.estacion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estacion requerida");
        }
        if (request.categoriaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria requerida");
        }
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria invalida"));

        Set<UUID> modificadorIds = request.modificadorIds() == null ? Set.of() : request.modificadorIds();
        Set<Modificador> modificadores = new HashSet<>(modificadorRepository.findAllById(modificadorIds));
        if (modificadores.size() != modificadorIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modificador invalido");
        }

        Platillo platillo = new Platillo();
        platillo.setNombre(request.nombre());
        platillo.setDescripcion(request.descripcion());
        platillo.setPrecio(request.precio());
        platillo.setCategoria(categoria);
        platillo.setEstacion(request.estacion());
        platillo.setModificadores(modificadores);
        platillo.setActivo(true);
        platilloRepository.save(platillo);

        return toResponse(platillo);
    }

    private PlatilloResponse toResponse(Platillo platillo) {
        Set<UUID> modificadorIds = platillo.getModificadores().stream()
                .map(Modificador::getId)
                .collect(Collectors.toSet());
        return new PlatilloResponse(platillo.getId(), platillo.getNombre(), platillo.getDescripcion(),
                platillo.getPrecio(), platillo.getCategoria().getId(), platillo.getCategoria().getNombre(),
                platillo.getEstacion(), modificadorIds, platillo.isActivo());
    }

}
```

- [x] **Step 6: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=CrearPlatilloControllerTest`
Expected: PASS, all 8 cases.

- [x] **Step 7: Run the full suite**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test`
Expected: PASS.

- [x] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V7__create_platillo.sql backend/src/main/resources/db/migration/V8__create_platillo_modificador.sql backend/src/main/java/com/posrestaurante/backend/platillo backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java backend/src/test/java/com/posrestaurante/backend/platillo/CrearPlatilloControllerTest.java
git commit -m "feat: dueno can create platillos with categoria and modificadores"
```

---

## Task 4: Listar platillos (GET /api/platillos)

**Files:**
- Modify: `backend/src/main/java/com/posrestaurante/backend/platillo/PlatilloController.java` (add one endpoint)
- Test: `backend/src/test/java/com/posrestaurante/backend/platillo/ListarPlatillosControllerTest.java`

**Interfaces:**
- Consumes: `PlatilloController.toResponse` (Task 3).
- Produces: `GET /api/platillos` — `200` + `List<PlatilloResponse>` of **every** platillo, active or not (same admin-listing precedent as `GET /api/usuarios`), any authenticated role; `401` with no token. No `403` case here — unlike the usuarios-admin plan, listing is intentionally open to every role (see Global Constraints), so there's no non-DUENO-rejection to test.

- [x] **Step 1: Write the failing test**

```java
class ListarPlatillosControllerTest extends AbstractIntegrationTest {

    @Autowired PlatilloRepository platilloRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void anyAuthenticatedRoleCanListPlatillosIncludingInactive() throws Exception {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);

        Platillo activo = new Platillo();
        activo.setNombre("Tacos");
        activo.setPrecio(new BigDecimal("85.00"));
        activo.setCategoria(categoria);
        activo.setEstacion(Estacion.P2);
        activo.setActivo(true);
        platilloRepository.save(activo);

        Platillo inactivo = new Platillo();
        inactivo.setNombre("Descontinuado");
        inactivo.setPrecio(new BigDecimal("50.00"));
        inactivo.setCategoria(categoria);
        inactivo.setEstacion(Estacion.P1);
        inactivo.setActivo(false);
        platilloRepository.save(inactivo);

        mockMvc.perform(get("/api/platillos").header("Authorization", "Bearer " + tokenFor(Rol.MESERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Tacos')]").exists())
                .andExpect(jsonPath("$[?(@.nombre == 'Descontinuado' && @.activo == false)]").exists());
    }

    @Test
    void unauthenticatedCannotListPlatillos() throws Exception {
        mockMvc.perform(get("/api/platillos")).andExpect(status().isUnauthorized());
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=ListarPlatillosControllerTest`
Expected: FAIL (404 — no `GET` route yet).

- [x] **Step 3: Implement `PlatilloController.listarPlatillos`**

```java
@GetMapping
public List<PlatilloResponse> listarPlatillos() {
    return platilloRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
}
```

- [x] **Step 4: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=ListarPlatillosControllerTest`
Expected: PASS, both cases.

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/platillo/PlatilloController.java backend/src/test/java/com/posrestaurante/backend/platillo/ListarPlatillosControllerTest.java
git commit -m "feat: any authenticated role can list platillos"
```

---

## Task 5: Activar / desactivar platillo (PATCH /api/platillos/{id}/activo)

**Files:**
- Create: `backend/src/main/java/com/posrestaurante/backend/platillo/dto/ActivarPlatilloRequest.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/platillo/PlatilloController.java` (add one endpoint)
- Test: `backend/src/test/java/com/posrestaurante/backend/platillo/ActivarPlatilloControllerTest.java`

**Interfaces:**
- Consumes: `PlatilloController.toResponse` (Task 3).
- Produces: `ActivarPlatilloRequest(Boolean activo)`; `PATCH /api/platillos/{id}/activo` — `200` + `PlatilloResponse` on success, `400` if `activo` is missing, `404` if `id` doesn't match any platillo, `403` for a non-`DUENO` token, `401` with no token.

- [x] **Step 1: Write the failing test**

```java
class ActivarPlatilloControllerTest extends AbstractIntegrationTest {

    @Autowired PlatilloRepository platilloRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private Platillo unPlatilloActivo() {
        Categoria categoria = new Categoria();
        categoria.setNombre("Entradas");
        categoriaRepository.save(categoria);

        Platillo platillo = new Platillo();
        platillo.setNombre("Tacos");
        platillo.setPrecio(new BigDecimal("85.00"));
        platillo.setCategoria(categoria);
        platillo.setEstacion(Estacion.P2);
        platillo.setActivo(true);
        return platilloRepository.save(platillo);
    }

    @Test
    void duenoDeactivatesPlatillo() throws Exception {
        Platillo platillo = unPlatilloActivo();

        mockMvc.perform(patch("/api/platillos/" + platillo.getId() + "/activo")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void duenoReactivatesPlatillo() throws Exception {
        // same setup with activo=false, PATCH {"activo":true} -> 200, $.activo == true
    }

    @Test
    void activoFieldIsRequired() throws Exception {
        // PATCH {} -> 400
    }

    @Test
    void unknownPlatilloIdReturns404() throws Exception {
        // PATCH /api/platillos/<random uuid>/activo {"activo":false} -> 404
    }

    @Test
    void nonDuenoCannotToggleActivo() throws Exception {
        // PATCH with tokenFor(Rol.CHEF) -> 403
    }

    @Test
    void unauthenticatedCannotToggleActivo() throws Exception {
        // PATCH with no Authorization header -> 401
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=ActivarPlatilloControllerTest`
Expected: FAIL (404 — no `PATCH .../activo` route yet).

- [x] **Step 3: Implement `ActivarPlatilloRequest` and `PlatilloController.cambiarActivo`**

```java
@PatchMapping("/{id}/activo")
public PlatilloResponse cambiarActivo(@PathVariable UUID id, @RequestBody ActivarPlatilloRequest request) {
    if (request.activo() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "activo es requerido");
    }
    Platillo platillo = platilloRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    platillo.setActivo(request.activo());
    platilloRepository.save(platillo);
    return toResponse(platillo);
}
```

- [x] **Step 4: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=ActivarPlatilloControllerTest`
Expected: PASS, all 6 cases.

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/platillo backend/src/test/java/com/posrestaurante/backend/platillo/ActivarPlatilloControllerTest.java
git commit -m "feat: dueno can activate/deactivate platillos"
```

---

## Task 6: Editar platillo (PATCH /api/platillos/{id})

**Files:**
- Create: `backend/src/main/java/com/posrestaurante/backend/platillo/dto/EditarPlatilloRequest.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/platillo/PlatilloController.java` (add one endpoint)
- Test: `backend/src/test/java/com/posrestaurante/backend/platillo/EditarPlatilloControllerTest.java`

**Interfaces:**
- Consumes: `PlatilloController.toResponse` (Task 3).
- Produces: `EditarPlatilloRequest(String nombre, String descripcion, BigDecimal precio, UUID categoriaId, Estacion estacion, Set<UUID> modificadorIds)` — every field optional, `null` means "don't change it", **except** `modificadorIds`, where `null` means "don't change" but an explicit empty set `[]` means "clear all modificadores" (see Review Focus — these are genuinely different inputs, both expressible because a JSON array's absence and its emptiness are distinguishable, unlike a bare boolean). `PATCH /api/platillos/{id}` — `200` + `PlatilloResponse` on success, `404` if `id` doesn't match any platillo, `400` if `precio` is provided and `<= 0`, or if `categoriaId`/any `modificadorIds` entry is provided and doesn't match an existing row; `403`/`401` same as every other mutating endpoint in this plan.

- [x] **Step 1: Write the failing test**

```java
class EditarPlatilloControllerTest extends AbstractIntegrationTest {

    @Autowired PlatilloRepository platilloRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired ModificadorRepository modificadorRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private Categoria unaCategoria(String nombre) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        return categoriaRepository.save(categoria);
    }

    private Platillo unPlatillo(Categoria categoria) {
        Platillo platillo = new Platillo();
        platillo.setNombre("Tacos");
        platillo.setPrecio(new BigDecimal("85.00"));
        platillo.setCategoria(categoria);
        platillo.setEstacion(Estacion.P2);
        platillo.setActivo(true);
        return platilloRepository.save(platillo);
    }

    @Test
    void duenoRenamesPlatillo() throws Exception {
        Platillo platillo = unPlatillo(unaCategoria("Entradas"));

        mockMvc.perform(patch("/api/platillos/" + platillo.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Tacos al pastor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Tacos al pastor"));
    }

    @Test
    void duenoChangesCategoriaAndPrecio() throws Exception {
        // seed platillo in categoria "Entradas", a second categoria "Platos fuertes",
        // PATCH {"categoriaId": "<segunda>", "precio": 120.00} -> 200,
        // $.categoriaNombre == "Platos fuertes", $.precio == 120.00
    }

    @Test
    void duenoReplacesModificadores() throws Exception {
        // seed platillo with modificador A attached, create modificador B,
        // PATCH {"modificadorIds": ["<B>"]} -> 200, $.modificadorIds == [B] only
    }

    @Test
    void duenoClearsModificadoresWithEmptyList() throws Exception {
        // seed platillo with modificador A attached,
        // PATCH {"modificadorIds": []} -> 200, $.modificadorIds is empty
    }

    @Test
    void editingWithUnknownCategoriaFails() throws Exception {
        // PATCH {"categoriaId": "<random uuid>"} -> 400
    }

    @Test
    void editingWithNonPositivePrecioFails() throws Exception {
        // PATCH {"precio": 0} -> 400
    }

    @Test
    void unknownPlatilloIdReturns404() throws Exception {
        // PATCH /api/platillos/<random uuid> {"nombre":"X"} -> 404
    }

    @Test
    void nonDuenoCannotEditPlatillo() throws Exception {
        // PATCH with tokenFor(Rol.MESERO) -> 403
    }

    @Test
    void unauthenticatedCannotEditPlatillo() throws Exception {
        // PATCH with no Authorization header -> 401
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=EditarPlatilloControllerTest`
Expected: FAIL (404 — no generic `PATCH /{id}` route yet).

- [x] **Step 3: Implement `EditarPlatilloRequest` and `PlatilloController.editarPlatillo`**

```java
@PatchMapping("/{id}")
public PlatilloResponse editarPlatillo(@PathVariable UUID id, @RequestBody EditarPlatilloRequest request) {
    Platillo platillo = platilloRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (request.nombre() != null && !request.nombre().isBlank()) {
        platillo.setNombre(request.nombre());
    }
    if (request.descripcion() != null) {
        platillo.setDescripcion(request.descripcion());
    }
    if (request.precio() != null) {
        if (request.precio().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "precio debe ser positivo");
        }
        platillo.setPrecio(request.precio());
    }
    if (request.categoriaId() != null) {
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria invalida"));
        platillo.setCategoria(categoria);
    }
    if (request.estacion() != null) {
        platillo.setEstacion(request.estacion());
    }
    if (request.modificadorIds() != null) {
        Set<Modificador> modificadores = new HashSet<>(modificadorRepository.findAllById(request.modificadorIds()));
        if (modificadores.size() != request.modificadorIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modificador invalido");
        }
        platillo.setModificadores(modificadores);
    }

    platilloRepository.save(platillo);
    return toResponse(platillo);
}
```

- [x] **Step 4: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=EditarPlatilloControllerTest`
Expected: PASS, all 9 cases.

- [x] **Step 5: Run the full suite**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test`
Expected: PASS — every test from this plan and both earlier plans, together.

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/platillo backend/src/test/java/com/posrestaurante/backend/platillo/EditarPlatilloControllerTest.java
git commit -m "feat: dueno can edit platillo nombre, precio, categoria, estacion, and modificadores"
```

---

## Non-Goals (this plan)

- Filtering `GET /api/platillos` to "only active" or by `categoria`/`estacion` — this is the admin listing; a filtered "what's sellable right now" view belongs to the toma-de-órdenes plan, built against its actual needs.
- Renaming or deleting a `Categoria`/`Modificador` once created — no user story calls for it, and `Platillo` already references them by id, so deleting one would need the same referenced-row-guard question `Mesa` deferred (see Global Constraints).
- Manually setting `Mesa.estado` — it's derived state the order-taking flow will own.
- Deleting (vs. deactivating) a `Platillo` — soft-delete only, matching `Usuario`.
- Photos on platillos (spec §7: text-only menu for now).
- Recetas / ingredientes / descuento de inventario (spec §5) — a separate plan, intentionally sequenced *after* this one but *before* the payment flow, since stock only needs to be correct once something can actually be sold against it.
