# Aprovisionamiento de Personal por el Dueño — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the dueño a way to create, list, activate/deactivate, and edit staff accounts (mesero, cajero, chef) over the API — replacing the single Flyway-seeded dev dueño as the only way any account exists in the system.

**Architecture:** Four new endpoints under `/api/usuarios`, all `DUENO`-only, added to the existing `UsuarioAdminController` one task at a time. No new infrastructure: this plan is pure CRUD on the `Usuario` entity, reusing `UsuarioRepository`, `PasswordEncoder`, `JwtService`, and `SecurityConfig` exactly as `docs/superpowers/plans/2026-11-19-backend-fundamentos.md` left them.

**Tech Stack:** Same as backend-fundamentos — Java 21, Spring Boot 3.5.x, Spring Data JPA, Spring Security, Testcontainers-backed integration tests. No new dependencies.

**Spec:** `docs/2026-11-13-pos-restaurante-design.md` (historia de usuario #8 "Como dueño, quiero crear usuarios... y asignarles su PIN"; §8 "Modelo de datos" — `USUARIO`; §8 "Reglas de negocio confirmadas" / "Notas del modelo" — `estacion` solo para CHEF, `rol` de un único valor, PIN guardado con hash). Also depends on `docs/superpowers/plans/2026-11-19-backend-fundamentos.md` (already implemented) for `Usuario`, `UsuarioRepository`, `PasswordEncoder`, `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`.

## Global Constraints

- `Usuario.rol` is single-valued and **immutable after creation** in this plan's scope: no endpoint here lets a caller set `rol = DUENO`, and no endpoint lets a caller change an existing usuario's `rol`. To change someone's role, deactivate the old account and create a new one.
- `Estacion` applies only to `CHEF`; it must be `null` for every other role, enforced on both create and edit (spec §8 nota: "`USUARIO.estacion` es nulo para DUENO/MESERO/CAJERO y solo aplica a CHEF").
- PIN is a 4-digit numeric string, stored only as a BCrypt hash via the same `PasswordEncoder` bean `AuthController` already uses — never stored or returned in plaintext.
- `UsuarioResponse` (the only shape these endpoints ever return) never includes `passwordHash` or `pinHash`, under any circumstance, success or error.
- Every endpoint in this plan requires `ROLE_DUENO`; `JwtAuthenticationFilter` already populates that authority from the token's `rol` claim (backend-fundamentos Task 5), so this is a `SecurityConfig` matcher, not new filter logic.

## Review Focus

- Authorization is the main risk here, not business logic: a missing or misplaced `hasRole("DUENO")` matcher on any of the four endpoints is a privilege-escalation bug (any logged-in mesero could provision accounts), not just a missing feature. Verify 403 for a non-DUENO token and 401 for no token on **every** endpoint, not just the first one.
- `estacion` consistency (required for CHEF, forbidden otherwise) is easy to validate only on create and forget on edit, or vice versa — Tasks 1 and 4 both need it, independently.
- **Known limitation, not a bug to fix here:** deactivating a usuario does not revoke a JWT that was already issued to them. `JwtAuthenticationFilter` is stateless and never re-checks the database per request (by design, see backend-fundamentos Task 5's rationale for `/api/auth/me`) — so a deactivated employee's current session keeps working until that token's natural expiration (480 minutes), even though a *new* PIN login attempt for them is rejected immediately. This plan only guarantees the latter. Don't let a reviewer mistake "deactivation blocks new logins" for "deactivation ends active sessions" — they're different guarantees, and only the first one is in scope.
- A successful PIN-login regression test (deactivate → PIN login now fails; reactivate / change PIN → it works again with the right credentials) is worth more here than another pure-validation test, since it proves this plan's writes actually affect the login path from backend-fundamentos, not just the `usuario` row in isolation.

---

## Task 1: Crear usuario (POST /api/usuarios)

**Files:**
- Create: `backend/src/main/java/com/posrestaurante/backend/usuario/dto/CrearUsuarioRequest.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/usuario/dto/UsuarioResponse.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/usuario/UsuarioAdminController.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java` (add one `requestMatchers` rule)
- Test: `backend/src/test/java/com/posrestaurante/backend/usuario/CrearUsuarioControllerTest.java`

**Interfaces:**
- Consumes: `UsuarioRepository`, `PasswordEncoder`, `Rol`, `Estacion`, `Usuario` (backend-fundamentos Task 2); `JwtService` (backend-fundamentos Task 3, used by tests to mint tokens — note it needs no DB row to mint a token for an arbitrary `usuarioId`/`rol`, since the filter never looks the user up).
- Produces: `CrearUsuarioRequest(String nombre, Rol rol, Estacion estacion, String pin)`; `UsuarioResponse(UUID id, String nombre, Rol rol, Estacion estacion, String username, boolean activo)` (reused unchanged by Tasks 2–4); `POST /api/usuarios` — `201` + `UsuarioResponse` on success, `400` when `rol` is `DUENO` or missing, when `rol == CHEF` and `estacion` is missing, when `rol != CHEF` and `estacion` is present, or when `pin` isn't exactly 4 digits; `403` for a valid token with any `rol` other than `DUENO`; `401` with no token. Adds `.requestMatchers("/api/usuarios/**").hasRole("DUENO")` to `SecurityConfig`, placed before the existing `.anyRequest().authenticated()` — this one rule covers every endpoint Tasks 2–4 add too.

- [x] **Step 1: Write the failing controller test**

```java
class CrearUsuarioControllerTest extends AbstractIntegrationTest {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void duenoCreatesMeseroSuccessfully() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Luis","rol":"MESERO","pin":"4821"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Luis"))
                .andExpect(jsonPath("$.rol").value("MESERO"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.pin").doesNotExist())
                .andExpect(jsonPath("$.pinHash").doesNotExist());

        assertThat(usuarioRepository.findAll())
                .anySatisfy(u -> assertThat(u.getNombre()).isEqualTo("Luis"));
    }

    @Test
    void creatingChefRequiresEstacion() throws Exception {
        // POST rol=CHEF, no estacion -> 400
    }

    @Test
    void creatingChefWithEstacionSucceeds() throws Exception {
        // POST rol=CHEF, estacion=P2, pin=1234 -> 201, $.estacion == "P2"
    }

    @Test
    void nonChefWithEstacionIsRejected() throws Exception {
        // POST rol=MESERO, estacion=P1 -> 400
    }

    @Test
    void cannotCreateAnotherDueno() throws Exception {
        // POST rol=DUENO -> 400
    }

    @Test
    void pinMustBeFourDigits() throws Exception {
        // POST rol=MESERO, pin="12" -> 400
    }

    @Test
    void nonDuenoCannotCreateUsuarios() throws Exception {
        // same valid MESERO payload, Authorization: Bearer tokenFor(Rol.MESERO) -> 403
    }

    @Test
    void unauthenticatedCannotCreateUsuarios() throws Exception {
        // same valid payload, no Authorization header -> 401
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=CrearUsuarioControllerTest`
Expected: FAIL — every case 404s (no route yet); this also means `nonDuenoCannotCreateUsuarios` fails with 404 instead of the 403 it asserts, which is fine at this checkpoint (it'll be exercised for real once both the route and the `hasRole` rule exist in Step 4).

- [x] **Step 3: Add the `SecurityConfig` rule**

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health", "/api/auth/login/**", "/api/auth/staff").permitAll()
        .requestMatchers("/api/usuarios/**").hasRole("DUENO")
        .anyRequest().authenticated())
```

- [x] **Step 4: Implement `CrearUsuarioRequest`, `UsuarioResponse`, `UsuarioAdminController.crearUsuario`**

```java
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioAdminController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioAdminController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crearUsuario(@RequestBody CrearUsuarioRequest request) {
        if (request.rol() == null || request.rol() == Rol.DUENO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rol invalido");
        }
        boolean esChef = request.rol() == Rol.CHEF;
        if (esChef && request.estacion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estacion requerida para CHEF");
        }
        if (!esChef && request.estacion() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estacion solo aplica a CHEF");
        }
        if (request.pin() == null || !request.pin().matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pin debe ser de 4 digitos");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre());
        usuario.setRol(request.rol());
        usuario.setEstacion(esChef ? request.estacion() : null);
        usuario.setPinHash(passwordEncoder.encode(request.pin()));
        usuario.setActivo(true);
        usuarioRepository.save(usuario);

        return toResponse(usuario);
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getRol(),
                usuario.getEstacion(), usuario.getUsername(), usuario.isActivo());
    }

}
```

- [x] **Step 5: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=CrearUsuarioControllerTest`
Expected: PASS, all 8 cases.

- [x] **Step 6: Run the full suite**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test`
Expected: PASS — this task's new tests plus every test from backend-fundamentos, together.

- [x] **Step 7: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/usuario backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java backend/src/test/java/com/posrestaurante/backend/usuario/CrearUsuarioControllerTest.java
git commit -m "feat: dueno can create staff accounts (mesero/cajero/chef)"
```

---

## Task 2: Listar usuarios (GET /api/usuarios)

**Files:**
- Modify: `backend/src/main/java/com/posrestaurante/backend/usuario/UsuarioAdminController.java` (add one endpoint)
- Test: `backend/src/test/java/com/posrestaurante/backend/usuario/ListarUsuariosControllerTest.java`

**Interfaces:**
- Consumes: `UsuarioAdminController.toResponse` (Task 1).
- Produces: `GET /api/usuarios` — `200` + `List<UsuarioResponse>` of **every** usuario, active or not, `DUENO` included (unlike `/api/auth/staff`, which is the PIN-login "pick your name" list and deliberately excludes `DUENO` and inactive staff — this is a different, admin-facing listing with a different purpose, so it doesn't filter); `403`/`401` same as Task 1.

- [x] **Step 1: Write the failing test**

```java
class ListarUsuariosControllerTest extends AbstractIntegrationTest {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void duenoListsAllUsuariosIncludingInactiveAndDueno() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        Usuario chefInactivo = TestUsuarios.staff("Mario", Rol.CHEF, "hash", false);
        usuarioRepository.save(mesero);
        usuarioRepository.save(chefInactivo);
        // Note: V3's seeded "admin" DUENO is also always present in this
        // shared Testcontainers database — don't assert an exact list size,
        // assert the entries this test cares about are present.

        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Luis')]").exists())
                .andExpect(jsonPath("$[?(@.nombre == 'Mario' && @.activo == false)]").exists())
                .andExpect(jsonPath("$[?(@.username == 'admin')]").exists());
    }

    @Test
    void nonDuenoCannotListUsuarios() throws Exception {
        // GET with tokenFor(Rol.CAJERO) -> 403
    }

    @Test
    void unauthenticatedCannotListUsuarios() throws Exception {
        // GET with no Authorization header -> 401
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=ListarUsuariosControllerTest`
Expected: FAIL (404 — no `GET` route yet; the `hasRole` matcher already in place from Task 1 means the two access-control cases already fail for the right reason once the route exists, but right now everything 404s).

- [x] **Step 3: Implement `UsuarioAdminController.listarUsuarios`**

```java
@GetMapping
public List<UsuarioResponse> listarUsuarios() {
    return usuarioRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
}
```

- [x] **Step 4: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=ListarUsuariosControllerTest`
Expected: PASS, all 3 cases.

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/usuario/UsuarioAdminController.java backend/src/test/java/com/posrestaurante/backend/usuario/ListarUsuariosControllerTest.java
git commit -m "feat: dueno can list all usuarios"
```

---

## Task 3: Activar / desactivar usuario (PATCH /api/usuarios/{id}/activo)

**Files:**
- Create: `backend/src/main/java/com/posrestaurante/backend/usuario/dto/ActivarUsuarioRequest.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/usuario/UsuarioAdminController.java` (add one endpoint)
- Test: `backend/src/test/java/com/posrestaurante/backend/usuario/ActivarUsuarioControllerTest.java`

**Interfaces:**
- Consumes: `UsuarioAdminController.toResponse` (Task 1); `AuthController`'s `POST /api/auth/login/pin` (backend-fundamentos Task 4) — this task's tests chain into it to prove deactivation actually blocks a real login, not just a database column.
- Produces: `ActivarUsuarioRequest(Boolean activo)`; `PATCH /api/usuarios/{id}/activo` — `200` + `UsuarioResponse` on success, `400` if `activo` is missing, `404` if `id` doesn't match any usuario, `403`/`401` same as Task 1.

- [x] **Step 1: Write the failing test**

```java
class ActivarUsuarioControllerTest extends AbstractIntegrationTest {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private String duenoToken() {
        return jwtService.generateToken(UUID.randomUUID(), "Ana", Rol.DUENO);
    }

    @Test
    void deactivatingUsuarioBlocksTheirNextPinLogin() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, passwordEncoder.encode("4821"), true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId() + "/activo")
                        .header("Authorization", "Bearer " + duenoToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(post("/api/auth/login/pin")
                        .contentType(APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + mesero.getId() + "\",\"pin\":\"4821\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reactivatingUsuarioAllowsLoginAgain() throws Exception {
        // seed activo=false with a known pin, PATCH activo=true -> 200,
        // then POST /api/auth/login/pin with the right pin -> 200
    }

    @Test
    void activoFieldIsRequired() throws Exception {
        // PATCH with {} -> 400
    }

    @Test
    void unknownUsuarioIdReturns404() throws Exception {
        // PATCH /api/usuarios/<random uuid>/activo {"activo":false} -> 404
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

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=ActivarUsuarioControllerTest`
Expected: FAIL (404 — no `PATCH .../activo` route yet).

- [x] **Step 3: Implement `ActivarUsuarioRequest` and `UsuarioAdminController.cambiarActivo`**

```java
@PatchMapping("/{id}/activo")
public UsuarioResponse cambiarActivo(@PathVariable UUID id, @RequestBody ActivarUsuarioRequest request) {
    if (request.activo() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "activo es requerido");
    }
    Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    usuario.setActivo(request.activo());
    usuarioRepository.save(usuario);
    return toResponse(usuario);
}
```

- [x] **Step 4: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=ActivarUsuarioControllerTest`
Expected: PASS, all 6 cases.

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/usuario backend/src/test/java/com/posrestaurante/backend/usuario/ActivarUsuarioControllerTest.java
git commit -m "feat: dueno can activate/deactivate usuarios"
```

---

## Task 4: Editar nombre, estación y PIN (PATCH /api/usuarios/{id})

**Files:**
- Create: `backend/src/main/java/com/posrestaurante/backend/usuario/dto/EditarUsuarioRequest.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/usuario/UsuarioAdminController.java` (add one endpoint)
- Test: `backend/src/test/java/com/posrestaurante/backend/usuario/EditarUsuarioControllerTest.java`

**Interfaces:**
- Consumes: `UsuarioAdminController.toResponse` (Task 1); `POST /api/auth/login/pin` (same cross-task regression pattern as Task 3, now for PIN changes).
- Produces: `EditarUsuarioRequest(String nombre, Estacion estacion, String pin)` — every field optional; a `null` field means "don't change it" (there is deliberately no way to *clear* a CHEF's `estacion` back to `null` through this endpoint — not a realistic need, since a chef always belongs to a station). `PATCH /api/usuarios/{id}` — `200` + `UsuarioResponse` on success, `404` if `id` doesn't match any usuario, `400` if `estacion` is set on a non-`CHEF` usuario, if `pin` is set on the `DUENO` usuario (who has no PIN), or if `pin` isn't exactly 4 digits; `403`/`401` same as Task 1. `rol` is intentionally not editable here (see Global Constraints).

- [ ] **Step 1: Write the failing test**

```java
class EditarUsuarioControllerTest extends AbstractIntegrationTest {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private String duenoToken() {
        return jwtService.generateToken(UUID.randomUUID(), "Ana", Rol.DUENO);
    }

    @Test
    void duenoRenamesUsuario() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId())
                        .header("Authorization", "Bearer " + duenoToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Luis Actualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Luis Actualizado"));
    }

    @Test
    void duenoChangesEstacionForChef() throws Exception {
        // seed CHEF at P1, PATCH {"estacion":"P2"} -> 200, $.estacion == "P2"
    }

    @Test
    void changingEstacionForNonChefIsRejected() throws Exception {
        // seed MESERO, PATCH {"estacion":"P1"} -> 400
    }

    @Test
    void duenoRegeneratesPinAndOldPinStopsWorking() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, passwordEncoder.encode("1111"), true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId())
                        .header("Authorization", "Bearer " + duenoToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"pin\":\"2222\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login/pin")
                        .contentType(APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + mesero.getId() + "\",\"pin\":\"1111\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login/pin")
                        .contentType(APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + mesero.getId() + "\",\"pin\":\"2222\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void settingPinOnDuenoIsRejected() throws Exception {
        // seed a second dueno (username != "admin" — that's taken by the V3 seed),
        // PATCH {"pin":"1234"} on it -> 400
    }

    @Test
    void unknownUsuarioIdReturns404() throws Exception {
        // PATCH /api/usuarios/<random uuid> {"nombre":"X"} -> 404
    }

    @Test
    void nonDuenoCannotEditUsuarios() throws Exception {
        // PATCH with tokenFor(Rol.MESERO) -> 403
    }

    @Test
    void unauthenticatedCannotEditUsuarios() throws Exception {
        // PATCH with no Authorization header -> 401
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=EditarUsuarioControllerTest`
Expected: FAIL (404 — no generic `PATCH /{id}` route yet; note it's registered *after* `/{id}/activo` in the controller, but Spring MVC path matching doesn't care about declaration order for distinct literal suffixes, so this is just a code-organization note, not a correctness risk).

- [ ] **Step 3: Implement `EditarUsuarioRequest` and `UsuarioAdminController.editarUsuario`**

```java
@PatchMapping("/{id}")
public UsuarioResponse editarUsuario(@PathVariable UUID id, @RequestBody EditarUsuarioRequest request) {
    Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (request.nombre() != null && !request.nombre().isBlank()) {
        usuario.setNombre(request.nombre());
    }
    if (request.estacion() != null) {
        if (usuario.getRol() != Rol.CHEF) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estacion solo aplica a CHEF");
        }
        usuario.setEstacion(request.estacion());
    }
    if (request.pin() != null) {
        if (usuario.getRol() == Rol.DUENO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dueno no usa PIN");
        }
        if (!request.pin().matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pin debe ser de 4 digitos");
        }
        usuario.setPinHash(passwordEncoder.encode(request.pin()));
    }

    usuarioRepository.save(usuario);
    return toResponse(usuario);
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test -Dtest=EditarUsuarioControllerTest`
Expected: PASS, all 8 cases.

- [ ] **Step 5: Run the full suite**

Run: `cd backend && JWT_SECRET=<any value> mvn -q test`
Expected: PASS — every test from this plan and from backend-fundamentos, together.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/usuario backend/src/test/java/com/posrestaurante/backend/usuario/EditarUsuarioControllerTest.java
git commit -m "feat: dueno can edit usuario nombre, estacion, and PIN"
```

---

## Non-Goals (this plan)

- Editing or transferring `rol` after creation — deactivate and create a new account instead.
- Deleting usuarios — only deactivation (`activo = false`), consistent with the soft-delete pattern the login flow already relies on.
- PIN uniqueness across staff — two people may share a PIN; the PIN-login flow already disambiguates by picking a name first (spec §8 nota), so nothing here needs to change that.
- Revoking an already-issued JWT on deactivation — see Review Focus. A future plan could add a token-revocation or short-expiration strategy if this gap ever matters in practice; it's out of scope here.
- A dueño-facing screen/UI for any of this — this plan is the API only, same split as backend-fundamentos.
