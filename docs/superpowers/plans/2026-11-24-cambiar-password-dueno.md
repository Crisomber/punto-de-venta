# Cambiar contraseña del dueño — Plan de Implementación

**Objetivo:** Cerrar un hueco real encontrado al revisar qué falta para usar el sistema de verdad — hoy no existe ningún camino, ni de API ni de UI, para que el dueño cambie su contraseña. `UsuarioAdminController` rechaza explícitamente crear o editar cuentas `DUENO`, y la única existente es la sembrada por migración (`admin`/`cambiame123`).

**Diseño:** Un endpoint nuevo, separado de `UsuarioAdminController` (que es sobre administrar *a otros*, no sobre la propia cuenta): `PATCH /api/auth/password` en `AuthController`, `DUENO`-only, que exige la contraseña actual antes de aceptar la nueva — mismo patrón de "resolver al usuario por id desde el JWT, nunca confiar en el cliente" ya usado en todo el proyecto.

**Restricciones:**
- Requiere `passwordActual` y que coincida con el hash guardado — si no, `401` (es, en esencia, una re-autenticación, igual que un login fallido en este mismo controlador).
- `passwordNueva` solo se valida como no vacía — ninguna historia define una política de complejidad/longitud, así que no se inventa una.
- No se emite un nuevo JWT ni se cierra la sesión actual — el token ya emitido sigue siendo válido hasta que expire (no hay revocación de tokens en este proyecto); cambiar la contraseña solo afecta logins futuros.

- [ ] **Paso 1: Prueba que falla**

```java
class CambiarPasswordControllerTest extends AbstractIntegrationTest {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private String duenoTokenConPassword(String password) {
        Usuario dueno = TestUsuarios.dueno("admin", passwordEncoder.encode(password), true);
        usuarioRepository.save(dueno);
        return jwtService.generateToken(dueno.getId(), "admin", Rol.DUENO);
    }

    @Test
    void duenoCambiaSuPassword() throws Exception {
        String token = duenoTokenConPassword("vieja123");

        mockMvc.perform(patch("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"passwordActual\":\"vieja123\",\"passwordNueva\":\"nueva456\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login/dueno")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nueva456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void passwordActualIncorrectaFalla401() throws Exception {
        String token = duenoTokenConPassword("vieja123");

        mockMvc.perform(patch("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"passwordActual\":\"mal\",\"passwordNueva\":\"nueva456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordNuevaVaciaFalla400() throws Exception {
        String token = duenoTokenConPassword("vieja123");

        mockMvc.perform(patch("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"passwordActual\":\"vieja123\",\"passwordNueva\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonDuenoCannotUseEndpoint() throws Exception {
        mockMvc.perform(patch("/api/auth/password")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"passwordActual\":\"x\",\"passwordNueva\":\"y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotUseEndpoint() throws Exception {
        mockMvc.perform(patch("/api/auth/password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"passwordActual\":\"x\",\"passwordNueva\":\"y\"}"))
                .andExpect(status().isUnauthorized());
    }

}
```

Esperado: FALLA en 3 de 5 — la ruta no existe todavía, así que `duenoCambiaSuPassword` y `passwordActualIncorrectaFalla401` y `passwordNuevaVaciaFalla400` dan `404` (un `DUENO` autenticado pasa de seguridad bajo `.anyRequest().authenticated()` y llega al `DispatcherServlet`, que no tiene mapping). `nonDuenoCannotUseEndpoint` también da `404` en vez de `403` (todavía no hay regla `hasRole("DUENO")` específica para esta ruta). Solo `unauthenticatedCannotUseEndpoint` ya pasa (`401`).

- [ ] **Paso 2: Implementar `CambiarPasswordRequest`, `AuthController.cambiarPassword` y la regla de `SecurityConfig`**

```java
public record CambiarPasswordRequest(String passwordActual, String passwordNueva) {
}
```

```java
@PatchMapping("/password")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void cambiarPassword(@RequestBody CambiarPasswordRequest request, Authentication authentication) {
    AuthenticatedUser caller = (AuthenticatedUser) authentication.getPrincipal();
    Usuario usuario = usuarioRepository.findById(caller.usuarioId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

    if (!passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "contrasena actual incorrecta");
    }
    if (request.passwordNueva() == null || request.passwordNueva().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contrasena nueva requerida");
    }

    usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
    usuarioRepository.save(usuario);
}
```

`SecurityConfig` (agregar, antes de `.anyRequest().authenticated()`):

```java
.requestMatchers("/api/auth/password").hasRole("DUENO")
```

- [ ] **Paso 3: Correr la prueba, confirmar que pasa, correr toda la suite, commit**
