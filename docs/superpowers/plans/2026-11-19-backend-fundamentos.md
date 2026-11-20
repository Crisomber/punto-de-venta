# Backend: Fundamentos (Auth + Proyecto) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Spring Boot backend project wired to Supabase Postgres, with a complete, tested authentication layer: dueño login (usuario/contraseña) and staff login (PIN, which also registers an attendance entry), both issuing JWTs that protect the rest of the API.

**Architecture:** A single Maven/Spring Boot 3 module (`backend/`). Flyway owns schema migrations; JPA/Hibernate maps entities onto that schema. Auth is stateless JWT: a `JwtService` issues and parses tokens, a `JwtAuthenticationFilter` populates Spring Security's context on every request, and `SecurityConfig` declares which endpoints are public. Integration tests run against a real Postgres via Testcontainers (not H2), so Flyway migrations and JPA mappings are verified against the same engine Supabase uses in production.

**Tech Stack:** Java 21, Spring Boot 3.x (latest stable at setup time), Maven, Spring Data JPA, Spring Security, Flyway (`flyway-database-postgresql`), PostgreSQL JDBC driver, JJWT 0.12.x (`io.jsonwebtoken`), Lombok, Testcontainers (postgresql module) + JUnit 5 for integration tests.

**Spec:** `docs/2026-11-13-pos-restaurante-design.md` (sections 3 "Roles y necesidades", 4 "Flujo operativo" steps 1 and 4, 8 "Modelo de datos" — `USUARIO` and `REGISTRO_ASISTENCIA`, 10 "Decisiones técnicas") — the plan argues from this spec; executors should read both.

## Global Constraints

- Backend is Java + Spring Boot; database is PostgreSQL hosted on Supabase (spec §10).
- `USUARIO.rol` is a single value, no multi-role (spec §8 notes).
- Dueño authenticates with usuario/contraseña; every other role authenticates with a PIN that also registers an attendance entry (spec §3, §8 "Reglas de negocio confirmadas" — PIN login selects name from a list, then confirms with the PIN, because `pin_hash` is not searchable by value).
- `Estacion` applies only to `CHEF`; it is null for `DUENO`, `MESERO`, `CAJERO` (spec §8).
- No public self-registration endpoint exists anywhere in this system — accounts are provisioned by the dueño (future plan) or, for the first dueño account, seeded directly (this plan).

## Review Focus

- Login (either kind) for a `usuario` with `activo = false` must be rejected — an employee who left must lose access immediately, and nothing in the user stories says to check this explicitly.
- Wrong password / wrong PIN must return 401 without revealing whether the username or usuario id exists — the spec never says this, but a login endpoint that leaks user existence is a predictable failure a reasonable reviewer expects covered.
- `GET /api/auth/staff` (the "pick your name" list for PIN login) must exclude `DUENO` and must exclude inactive staff, since the dueño never appears in that list and a departed employee shouldn't be selectable.
- An expired or malformed JWT on a protected endpoint must return 401, not a 500 — unhandled parsing exceptions are an easy miss when first wiring a JWT filter.
- A successful PIN login must create **exactly one** `REGISTRO_ASISTENCIA` row per attempt — not zero (forgotten call) and not more than one (e.g. a retried request double-inserting) — since "registra asistencia" is the one business rule this plan cannot get subtly wrong.

---

## Task 1: Project scaffolding and Postgres connectivity

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/posrestaurante/backend/BackendApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/java/com/posrestaurante/backend/testsupport/AbstractIntegrationTest.java`
- Create: `backend/src/test/java/com/posrestaurante/backend/BackendApplicationTests.java`
- Create: `backend/src/main/resources/db/migration/` (empty dir, placeholder for Task 2)

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a bootable Spring Boot app reading DB credentials from `SUPABASE_DB_URL` / `SUPABASE_DB_USERNAME` / `SUPABASE_DB_PASSWORD` env vars; `AbstractIntegrationTest` — an abstract class that starts a shared `PostgreSQLContainer<?>` (image `postgres:16-alpine`) once per test JVM and registers it as the datasource via `@DynamicPropertySource`, annotated `@SpringBootTest` (default **MOCK** web environment — deliberate: `MockMvc` dispatches in-process on the test's own thread, which is what lets `@Transactional` roll back every test method's writes; `RANDOM_PORT` would spin up a real server on another thread and break that rollback) + `@AutoConfigureMockMvc` + `@Transactional`. Every later task's test class extends this, so no test needs to clean up the rows it inserts — each method's writes vanish when it ends, which is also what keeps `username = 'admin'` (seeded once by Flyway in Task 3) and ad hoc test usernames like `"ana"` from ever colliding across test methods.

- [x] **Step 1: Generate the Spring Boot project**

Use Spring Initializr (web UI, `curl start.spring.io`, or your IDE) with: Group `com.posrestaurante`, Artifact `backend`, Java 21, Maven, Spring Boot latest stable 3.x, dependencies: Spring Web, Spring Data JPA, Spring Security, Validation, PostgreSQL Driver, Flyway Migration. Place the generated project at `backend/`.

- [x] **Step 2: Add Lombok, JJWT, and Testcontainers dependencies to `backend/pom.xml`**

Add `org.projectlombok:lombok` (provided scope), `io.jsonwebtoken:jjwt-api:0.12.6`, `io.jsonwebtoken:jjwt-impl:0.12.6` (runtime), `io.jsonwebtoken:jjwt-jackson:0.12.6` (runtime), and test-scope `org.testcontainers:postgresql` + `org.testcontainers:junit-jupiter` (use the Spring Boot-managed Testcontainers BOM version — add `spring-boot-testcontainers` test-scope starter so versions stay aligned).

- [x] **Step 3: Configure `backend/src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: backend
  datasource:
    url: ${SUPABASE_DB_URL}
    username: ${SUPABASE_DB_USERNAME}
    password: ${SUPABASE_DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
management:
  endpoints:
    web:
      exposure:
        include: health
```

`ddl-auto: validate` is deliberate: Flyway (not Hibernate) owns schema changes from Task 2 onward; Hibernate only checks its mappings match.

- [x] **Step 4: Write `AbstractIntegrationTest`**

Do **not** use `@Testcontainers`/`@Container` here: that JUnit5 extension ties a container's start/stop to each *test class's own* lifecycle, so even a `static` field gets torn down between test classes in the same JVM fork — the next class's context then points at a dead container and every test fails with "Connection refused", while each class still passes in isolation. Start the container manually in a static initializer instead (the official Testcontainers singleton pattern); only Ryuk then cleans it up, at JVM exit.

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractIntegrationTest {
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mockMvc;
}
```

- [x] **Step 5: Write `BackendApplicationTests`**

```java
class BackendApplicationTests extends AbstractIntegrationTest {
    @Test
    void contextLoadsAndHealthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

- [x] **Step 6: Run the test and verify it passes**

Run: `cd backend && mvn -q test -Dtest=BackendApplicationTests`
Expected: PASS — Testcontainers pulls `postgres:16-alpine`, the app context boots against it, `/actuator/health` returns `{"status":"UP"}`. (Requires Docker running locally.)

- [x] **Step 7: Commit**

```bash
git add backend/
git commit -m "chore: scaffold Spring Boot backend with Testcontainers-backed integration test base"
```

---

## Task 2: `Usuario` and `RegistroAsistencia` persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_usuario.sql`
- Create: `backend/src/main/resources/db/migration/V2__create_registro_asistencia.sql`
- Create: `backend/src/main/java/com/posrestaurante/backend/usuario/Rol.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/usuario/Estacion.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/usuario/Usuario.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/usuario/UsuarioRepository.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/asistencia/TipoAsistencia.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/asistencia/RegistroAsistencia.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/asistencia/RegistroAsistenciaRepository.java`
- Create: `backend/src/test/java/com/posrestaurante/backend/testsupport/TestUsuarios.java`
- Test: `backend/src/test/java/com/posrestaurante/backend/usuario/UsuarioPersistenceTest.java`

**Interfaces:**
- Consumes: `AbstractIntegrationTest` (Task 1).
- Produces: `Rol` enum `{DUENO, MESERO, CAJERO, CHEF}`; `Estacion` enum `{P1, P2}`; `Usuario` entity (`UUID id`, `String nombre`, `Rol rol`, `Estacion estacion` nullable, `String username` nullable, `String passwordHash` nullable, `String pinHash` nullable, `boolean activo`); `UsuarioRepository extends JpaRepository<Usuario, UUID>` with `Optional<Usuario> findByUsernameAndActivoTrue(String username)` and `List<Usuario> findByActivoTrueAndRolNot(Rol rol)`; `RegistroAsistencia` entity (`UUID id`, `Usuario usuario`, `Instant fechaHora`, `TipoAsistencia tipo`); `RegistroAsistenciaRepository extends JpaRepository<RegistroAsistencia, UUID>` with `long countByUsuarioId(UUID usuarioId)`. Also produces `TestUsuarios`, a test-only factory class with `static Usuario dueno(String username, String passwordHash, boolean activo)` and `static Usuario staff(String nombre, Rol rol, String pinHash, boolean activo)` (sets `estacion = Estacion.P1` automatically when `rol == CHEF`, else `null`) — every later task's tests build their fixtures through this, not ad hoc.

- [x] **Step 1: Write `V1__create_usuario.sql`**

```sql
CREATE TABLE usuario (
    id             UUID PRIMARY KEY,
    nombre         VARCHAR(120) NOT NULL,
    rol            VARCHAR(20)  NOT NULL CHECK (rol IN ('DUENO','MESERO','CAJERO','CHEF')),
    estacion       VARCHAR(10)  CHECK (estacion IN ('P1','P2')),
    username       VARCHAR(60)  UNIQUE,
    password_hash  VARCHAR(255),
    pin_hash       VARCHAR(255),
    activo         BOOLEAN NOT NULL DEFAULT TRUE
);
```

- [x] **Step 2: Write `V2__create_registro_asistencia.sql`**

```sql
CREATE TABLE registro_asistencia (
    id          UUID PRIMARY KEY,
    usuario_id  UUID NOT NULL REFERENCES usuario(id),
    fecha_hora  TIMESTAMPTZ NOT NULL,
    tipo        VARCHAR(10) NOT NULL CHECK (tipo IN ('ENTRADA','SALIDA'))
);
```

- [x] **Step 3: Write the failing persistence test**

```java
class UsuarioPersistenceTest extends AbstractIntegrationTest {
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RegistroAsistenciaRepository registroAsistenciaRepository;

    @Test
    void savesAndReloadsADuenoByUsername() {
        Usuario dueno = TestUsuarios.dueno("ana", "hash", true);
        usuarioRepository.save(dueno);

        Optional<Usuario> found = usuarioRepository.findByUsernameAndActivoTrue("ana");
        assertThat(found).isPresent();
        assertThat(found.get().getRol()).isEqualTo(Rol.DUENO);
    }

    @Test
    void listsActiveNonDuenoStaffOnly() {
        // seed one DUENO, one active MESERO, one inactive CHEF with estacion P1
        // assert findByActivoTrueAndRolNot(Rol.DUENO) returns only the active MESERO
    }

    @Test
    void savesRegistroAsistenciaLinkedToUsuario() {
        // save a usuario, then a RegistroAsistencia(usuario, Instant.now(), TipoAsistencia.ENTRADA)
        // assert registroAsistenciaRepository.countByUsuarioId(usuario.getId()) == 1
    }
}
```

- [x] **Step 4: Run the test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=UsuarioPersistenceTest`
Expected: FAIL (classes `Usuario`, `UsuarioRepository`, etc. don't exist yet).

- [x] **Step 5: Implement `Rol`, `Estacion`, `Usuario`, `UsuarioRepository`, `TipoAsistencia`, `RegistroAsistencia`, `RegistroAsistenciaRepository`, `TestUsuarios`**

`Usuario` and `RegistroAsistencia` are `@Entity` classes mapped with `@Enumerated(EnumType.STRING)` for every enum field, `@Id private UUID id = UUID.randomUUID();` (application-generated, no DB sequence). `RegistroAsistencia.usuario` is `@ManyToOne(fetch = LAZY)` with `@JoinColumn(name = "usuario_id")`. Use Lombok `@Getter @Setter` (or `@Data`) to avoid boilerplate. `TestUsuarios.dueno(...)` builds a `Usuario` with `rol = DUENO`, the given username/passwordHash/activo, `pinHash = null`, `estacion = null`. `TestUsuarios.staff(...)` builds a `Usuario` with the given rol/pinHash/activo, `username = null`, `passwordHash = null`, `estacion = (rol == CHEF ? Estacion.P1 : null)`. Neither helper calls `usuarioRepository.save(...)` — callers save when they need the id populated.

- [x] **Step 6: Run the test and verify it passes**

Run: `cd backend && mvn -q test -Dtest=UsuarioPersistenceTest`
Expected: PASS — Flyway applies V1/V2 against the Testcontainers Postgres, all three assertions hold.

- [x] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration backend/src/main/java/com/posrestaurante/backend/usuario backend/src/main/java/com/posrestaurante/backend/asistencia backend/src/test/java/com/posrestaurante/backend/usuario backend/src/test/java/com/posrestaurante/backend/testsupport/TestUsuarios.java
git commit -m "feat: add Usuario and RegistroAsistencia schema, entities, and repositories"
```

---

## Task 3: Dueño login (usuario/contraseña) issuing a JWT

**Files:**
- Create: `backend/src/main/java/com/posrestaurante/backend/security/JwtService.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java` (minimal version — see deviation note below)
- Create: `backend/src/main/java/com/posrestaurante/backend/auth/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/auth/dto/AuthResponse.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/auth/AuthController.java`
- Create: `backend/src/main/resources/db/migration/V3__seed_dueno_dev.sql`
- Modify: `backend/src/main/resources/application.yml` (add `jwt.secret`, `jwt.expiration-minutes`)
- Test: `backend/src/test/java/com/posrestaurante/backend/auth/DuenoAuthControllerTest.java`

**Interfaces:**
- Consumes: `UsuarioRepository`, `Rol`, `TestUsuarios` (Task 2).
- Produces: `PasswordEncoder` bean (`BCryptPasswordEncoder`, in a new `@Configuration` class `backend/src/main/java/com/posrestaurante/backend/security/PasswordEncoderConfig.java`); `JwtService` with `String generateToken(UUID usuarioId, String nombre, Rol rol)` and `Jws<Claims> parse(String token)` (throws `JwtException` on anything invalid/expired — callers catch this, nothing in `JwtService` swallows it); `LoginRequest(String username, String password)` record; `AuthResponse(String token, String nombre, Rol rol)` record; `POST /api/auth/login/dueno` — 200 + `AuthResponse` on success, 401 (empty body or generic `{"error":"credenciales invalidas"}`) on bad username, bad password, or `activo = false`. Also produces `V3__seed_dueno_dev.sql`, a Flyway migration that inserts exactly one dev dueño account (`username = 'admin'`) so the system is logeable end-to-end without a provisioning UI (none exists yet — the dueño-creates-staff flow is a later plan).

**Deviation from the original plan:** this task also creates a *minimal* `SecurityConfig` (CSRF disabled, `/actuator/health` and `/api/auth/login/**` `permitAll()`, everything else `authenticated()`), even though Task 5 is where `SecurityConfig` was originally meant to appear. Without it, Spring Security's default autoconfiguration (CSRF protection + basic-auth-everything) returns `403` for every `POST`, including `/api/auth/login/dueno`, before the request ever reaches the controller — the test class can't get past step 2 without it. Task 4 extends the `permitAll()` list with `/api/auth/staff`; Task 5 replaces this file's body entirely with the stateless, JWT-aware version (and `addFilterBefore(jwtFilter, ...)`, and the custom 401 entry point) — at that point it stops being a `Create` and becomes a straightforward edit of an existing file.

**Also note:** `V3__seed_dueno_dev.sql`'s seeded `username = 'admin'` is now live data in every test run against the shared Testcontainers Postgres (Task 1's fix made that container a true cross-class singleton). Any later test fixture that needs its own dueño account must pick a username other than `admin` — `UsuarioPersistenceTest.listsActiveNonDuenoStaffOnly` collided with it and was changed to use `otro-admin`.

- [x] **Step 1: Write the failing controller test**

```java
class DuenoAuthControllerTest extends AbstractIntegrationTest {
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void duenoLogsInWithCorrectCredentials() throws Exception {
        Usuario dueno = TestUsuarios.dueno("ana", passwordEncoder.encode("secreto123"), true);
        usuarioRepository.save(dueno);

        mockMvc.perform(post("/api/auth/login/dueno")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"username":"ana","password":"secreto123"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.rol").value("DUENO"));
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        // seed dueno "ana" with a known hash, POST with wrong password -> 401
    }

    @Test
    void rejectsUnknownUsername() throws Exception {
        // POST with a username that was never seeded -> 401 (same shape as wrong password)
    }

    @Test
    void rejectsInactiveDueno() throws Exception {
        // seed dueno with activo = false, correct password -> 401
    }

    @Test
    void seededDevDuenoCanLogIn() throws Exception {
        // no manual insert here: V3__seed_dueno_dev.sql already put it there.
        // POST {"username":"admin","password":"<the documented dev password>"} -> 200 + token
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=DuenoAuthControllerTest`
Expected: FAIL (404 — no `/api/auth/login/dueno` route yet).

- [x] **Step 3: Add `jwt.secret` and `jwt.expiration-minutes` to `application.yml`**

```yaml
jwt:
  secret: ${JWT_SECRET}
  expiration-minutes: 480
```

- [x] **Step 4: Implement `JwtService`**

Use `Jwts.builder()` from JJWT 0.12.x, signing with HMAC-SHA256 from `jwt.secret` (read via `@Value`), embedding claims `usuarioId` (string UUID), `nombre`, `rol`; expiry = now + `jwt.expiration-minutes`. `parse(String token)` calls `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` and lets `JwtException`/`IllegalArgumentException` propagate.

- [x] **Step 5: Implement `PasswordEncoderConfig`, `LoginRequest`, `AuthResponse`, `AuthController.loginDueno`**

`loginDueno` looks up `usuarioRepository.findByUsernameAndActivoTrue(request.username())`; if absent, or `passwordEncoder.matches(request.password(), usuario.getPasswordHash())` is false, return `401` via `ResponseStatusException` — same status and body shape for "not found" and "wrong password" so the two are indistinguishable to a caller. On success, call `jwtService.generateToken(...)` and return `200` with `AuthResponse`.

- [x] **Step 6: Write `V3__seed_dueno_dev.sql`**

Pick a documented dev password (e.g. `cambiame123`), generate its BCrypt hash by running `new BCryptPasswordEncoder().encode("cambiame123")` from a scratch `main`/test (same encoder the app uses — a mismatched work factor or algorithm will make `seededDevDuenoCanLogIn` fail even with the right plaintext), then:

```sql
INSERT INTO usuario (id, nombre, rol, username, password_hash, activo)
VALUES (gen_random_uuid(), 'Administrador', 'DUENO', 'admin', '<paste the generated hash>', TRUE);
```

If `gen_random_uuid()` is unavailable on the target Postgres, generate a literal UUID instead (e.g. with `uuidgen` or `UUID.randomUUID()`) and paste it directly — this row's id never needs to be looked up by the application, only its username.

- [x] **Step 7: Run the test and verify it passes**

Run: `cd backend && mvn -q test -Dtest=DuenoAuthControllerTest`
Expected: PASS, all five cases including `seededDevDuenoCanLogIn`.

- [x] **Step 8: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/security backend/src/main/java/com/posrestaurante/backend/auth backend/src/main/resources/application.yml backend/src/main/resources/db/migration/V3__seed_dueno_dev.sql backend/src/test/java/com/posrestaurante/backend/auth
git commit -m "feat: add dueno username/password login issuing a JWT, seed dev dueno account"
```

---

## Task 4: Staff PIN login + attendance registration

**Files:**
- Create: `backend/src/main/java/com/posrestaurante/backend/auth/dto/PinLoginRequest.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/auth/dto/StaffListItem.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/auth/AuthController.java` (add two endpoints)
- Test: `backend/src/test/java/com/posrestaurante/backend/auth/PinAuthControllerTest.java`

**Interfaces:**
- Consumes: `UsuarioRepository`, `RegistroAsistenciaRepository`, `TestUsuarios` (Task 2), `JwtService`, `PasswordEncoder` (Task 3).
- Produces: `PinLoginRequest(UUID usuarioId, String pin)` record; `StaffListItem(UUID id, String nombre, Rol rol)` record; `GET /api/auth/staff` — 200 + JSON array of `StaffListItem` for every `usuario` where `activo = true` and `rol != DUENO`; `POST /api/auth/login/pin` — 200 + `AuthResponse` on correct PIN for an active non-dueño usuario (and inserts one `RegistroAsistencia` row with `tipo = ENTRADA`), 401 on wrong PIN, unknown id, or `activo = false` (no `RegistroAsistencia` row inserted in any of these cases).

- [x] **Step 1: Write the failing controller test**

```java
class PinAuthControllerTest extends AbstractIntegrationTest {
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RegistroAsistenciaRepository registroAsistenciaRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void staffListExcludesDuenoAndInactiveUsers() throws Exception {
        // seed: active DUENO, active MESERO "Luis", inactive CHEF "Mario"
        mockMvc.perform(get("/api/auth/staff"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].nombre").value("Luis"));
    }

    @Test
    void correctPinLogsInAndRegistersOneAttendanceEntry() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, passwordEncoder.encode("4821"), true);
        usuarioRepository.save(mesero);

        mockMvc.perform(post("/api/auth/login/pin")
                .contentType(APPLICATION_JSON)
                .content("{\"usuarioId\":\"" + mesero.getId() + "\",\"pin\":\"4821\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(registroAsistenciaRepository.countByUsuarioId(mesero.getId())).isEqualTo(1);
    }

    @Test
    void wrongPinIsRejectedAndRegistersNoAttendance() throws Exception {
        // correct setup, wrong pin in request -> 401; countByUsuarioId stays 0
    }

    @Test
    void inactiveStaffCannotLoginEvenWithCorrectPin() throws Exception {
        // activo = false, correct pin -> 401; countByUsuarioId stays 0
    }

    @Test
    void unknownUsuarioIdIsRejected() throws Exception {
        // random UUID not in DB -> 401
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=PinAuthControllerTest`
Expected: FAIL (404s — routes don't exist).

- [x] **Step 3: Implement `PinLoginRequest`, `StaffListItem`, and the two `AuthController` endpoints**

`getStaff()` returns `usuarioRepository.findByActivoTrueAndRolNot(Rol.DUENO)` mapped to `StaffListItem`. `loginPin(request)` loads the `usuario` by id (404→401, same generic error as Task 3); if absent, inactive, or `!passwordEncoder.matches(request.pin(), usuario.getPinHash())`, return 401 **without** touching `registroAsistenciaRepository`. On success: save a new `RegistroAsistencia(usuario, Instant.now(), TipoAsistencia.ENTRADA)` *before* returning the token, then `jwtService.generateToken(...)`.

- [x] **Step 4: Run the test and verify it passes**

Run: `cd backend && mvn -q test -Dtest=PinAuthControllerTest`
Expected: PASS, all five cases.

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/auth backend/src/test/java/com/posrestaurante/backend/auth/PinAuthControllerTest.java
git commit -m "feat: add staff PIN login with attendance registration"
```

---

## Task 5: JWT authorization filter and `/api/auth/me`

**Files:**
- Create: `backend/src/main/java/com/posrestaurante/backend/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java`
- Create: `backend/src/main/java/com/posrestaurante/backend/auth/dto/MeResponse.java`
- Modify: `backend/src/main/java/com/posrestaurante/backend/auth/AuthController.java` (add `GET /api/auth/me`)
- Test: `backend/src/test/java/com/posrestaurante/backend/security/AuthMeControllerTest.java`

**Interfaces:**
- Consumes: `JwtService` (Task 3), `TestUsuarios` (Task 2).
- Produces: `SecurityConfig` — a `SecurityFilterChain` bean that permits `POST /api/auth/login/**` and `GET /api/auth/staff` and `/actuator/health` without authentication, requires a valid JWT for every other path, is stateless (`SessionCreationPolicy.STATELESS`), and registers `authorities` as `ROLE_<rol>` (e.g. `ROLE_DUENO`) so later plans can use `hasRole("DUENO")`; `MeResponse(UUID usuarioId, String nombre, Rol rol)`; `GET /api/auth/me` — 200 + `MeResponse` for the caller identified by their token's claims, 401 if no token, a malformed token, or an expired token.

- [x] **Step 1: Write the failing test**

```java
class AuthMeControllerTest extends AbstractIntegrationTest {
    @Autowired JwtService jwtService;
    @Autowired UsuarioRepository usuarioRepository;

    @Test
    void meReturns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCallerInfoWithValidToken() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);
        String token = jwtService.generateToken(mesero.getId(), "Luis", Rol.MESERO);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("Luis"))
            .andExpect(jsonPath("$.rol").value("MESERO"));
    }

    @Test
    void meReturns401WithMalformedToken() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturns401WithExpiredToken() throws Exception {
        // generate a token with a JwtService configured with a negative/zero expiration,
        // or directly build an already-expired JJWT token in the test, assert 401
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=AuthMeControllerTest`
Expected: FAIL (no route, and/or Spring Security's default config blocks everything with a login page redirect instead of a clean 401).

- [x] **Step 3: Implement `JwtAuthenticationFilter`**

`extends OncePerRequestFilter`. Reads the `Authorization` header; if absent, calls `filterChain.doFilter` and lets `SecurityConfig` deny unauthenticated access downstream. If present, strips `Bearer `, calls `jwtService.parse(token)`; on success, builds a `UsernamePasswordAuthenticationToken` carrying the `usuarioId`/`nombre` as principal details and `List.of(new SimpleGrantedAuthority("ROLE_" + rol))` as authorities, sets it on `SecurityContextHolder`. On any `JwtException`/`IllegalArgumentException` from `parse`, do **not** set the context and let the request continue unauthenticated (so `SecurityConfig` returns 401, not the filter throwing a 500).

- [x] **Step 4: Implement `SecurityConfig`**

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/api/auth/login/**", "/api/auth/staff").permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(e -> e.authenticationEntryPoint(
            (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

- [x] **Step 5: Implement `MeResponse` and `AuthController.me(Authentication authentication)`**

Read `usuarioId`/`nombre`/`rol` back out of the `Authentication` populated by the filter (e.g. from the principal details object the filter attached) and return a `MeResponse`. No repository lookup needed — everything required is already in the token's claims.

- [x] **Step 6: Run the test and verify it passes**

Run: `cd backend && mvn -q test -Dtest=AuthMeControllerTest`
Expected: PASS, all four cases.

- [x] **Step 7: Run the full suite**

Run: `cd backend && mvn -q test`
Expected: PASS — every test from Tasks 1–5 green together.

- [x] **Step 8: Commit**

```bash
git add backend/src/main/java/com/posrestaurante/backend/security backend/src/main/java/com/posrestaurante/backend/auth backend/src/test/java/com/posrestaurante/backend/security
git commit -m "feat: enforce JWT authentication on protected routes and add /api/auth/me"
```
