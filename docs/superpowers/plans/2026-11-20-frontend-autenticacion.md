# Frontend — Autenticación — Plan de Implementación

> **Para agentes:** SUB-SKILL REQUERIDA: usar superpowers:subagent-driven-development (recomendado) o superpowers:executing-plans para implementar este plan tarea por tarea. Los pasos usan sintaxis de checkbox (`- [ ]`) para seguimiento.

**Objetivo:** Primer pedazo de frontend del proyecto — hasta ahora todo era backend puro. Este plan cubre el punto de entrada único de cualquier pantalla: la página de login (dueño con usuario/contraseña, personal con PIN), guardar la sesión en el navegador, y redirigir a cada rol a una pantalla propia. Las pantallas reales de cada rol (tomar pedidos, cocina, caja, panel del dueño) son planes de frontend separados, uno por rol, igual que el backend se dividió por área de negocio — aquí solo se entrega un *stub* mínimo por pantalla ("Bienvenido, {nombre}" + cerrar sesión) para poder verificar el flujo completo de principio a fin.

**Decisión de arquitectura — sin build tool, mismo origen:** La spec dice "Web con HTML, CSS y JavaScript, consumiendo la API" — no menciona React/Vite/ningún framework. Se sirve como archivos estáticos planos desde `backend/src/main/resources/static/`, que Spring Boot ya expone por defecto sin configuración adicional. Esto evita CORS por completo (mismo origen que la API) y no introduce ninguna herramienta de build que nadie pidió, consistente con la preferencia ya establecida en este proyecto de mantener el backend simple (ej. polling en vez de WebSockets).

**Metodología de verificación — distinta a los planes de backend:** No existe infraestructura de pruebas de frontend en este proyecto (ninguna historia la pide, y montar una ahora sería una herramienta no solicitada). En vez de TDD con JUnit, cada tarea se verifica manualmente: se levanta el backend localmente contra una Postgres real, se abre la página con un navegador real (Playwright, ya preinstalado en este entorno) y se confirma el flujo completo — login correcto, login incorrecto, acceso directo sin sesión — con al menos una captura de pantalla de evidencia antes del commit.

**Límite de alcance:** Login de dueño (usuario/contraseña) y de personal (selección de nombre + PIN), guardado de sesión, redirección por rol, cierre de sesión, y un stub por pantalla de destino (`dueno.html`, `mesero.html`, `cocina.html`, `caja.html`). Nada de lo que cada rol hace una vez dentro.

**Arquitectura:** `backend/src/main/resources/static/index.html` (login), `css/estilos.css`, `js/api.js` (sesión + llamadas a la API, compartido por todas las páginas), `js/login.js` (lógica del formulario de login), y un `.html` de destino por rol. Reutiliza endpoints de auth ya existentes (`POST /api/auth/login/dueno`, `GET /api/auth/staff`, `POST /api/auth/login/pin`) sin modificarlos.

**Stack técnico:** HTML, CSS y JavaScript planos, sin frameworks ni bundlers. Sin dependencias nuevas, sin migraciones nuevas.

**Spec:** `docs/2026-11-13-pos-restaurante-design.md` — §10 "Decisiones técnicas" (frontend), §8 nota del modelo ("El login por PIN funciona seleccionando el nombre de una lista y luego tecleando el PIN"). Depende de `docs/superpowers/plans/2026-11-19-backend-fundamentos.md` (`AuthController`, ya implementado).

## Restricciones Globales

- **El frontend se sirve en el mismo origen que la API** (ver Decisión de arquitectura arriba) — ningún archivo HTML/CSS/JS hace una petición cross-origin.
- **`SecurityConfig` necesita permitir el acceso sin token a los archivos estáticos** (`/`, `/*.html`, `/css/**`, `/js/**`) — hoy `.anyRequest().authenticated()` los bloquearía a todos, y ni la página de login ni su propio CSS/JS podrían cargar. Esta regla se agrega a la lista `permitAll()` que ya existe (`/actuator/health`, `/api/auth/login/**`, `/api/auth/staff`), sin tocar ninguna regla de `/api/**` — esas siguen exactamente igual.
- **La sesión (`token`, `nombre`, `rol`) se guarda en `localStorage` bajo una sola clave (`pos.sesion`), como JSON**, a través de un wrapper compartido en `js/api.js` — nunca se repite esa lógica en cada página.
- **La guardia de sesión en cada pantalla de destino es solo para experiencia de usuario, no un control de seguridad real.** Si no hay sesión guardada, o el rol guardado no coincide con el que esa pantalla espera, redirige a `index.html`. El control real sigue siendo cada endpoint de la API, que ya rechaza con `401`/`403` sin el token correcto — eso no cambia con este plan.
- **Un error de login (`401`) se muestra como texto dentro del formulario mismo** — nunca un `alert()` del navegador ni una página de error genérica, y sin perder lo ya escrito en los demás campos del formulario.
- **El selector de "Personal" lista nombre y rol juntos** (ej. "Luis — MESERO") para distinguir personal con nombres parecidos, ya que el PIN no es buscable por sí solo — consistente con la nota ya documentada en la spec.

## Puntos de Atención para Revisión

- Verificar que abrir cualquier `.html` de destino directamente, sin sesión, redirige a `index.html` en vez de mostrar una pantalla rota o vacía — y que una sesión del rol equivocado (ej. un `CAJERO` abriendo `mesero.html`) también redirige.
- Verificar que la regla nueva de `SecurityConfig` no afloje ningún endpoint de `/api/**` — confirmarlo abriendo una ruta de la API directamente sin token después del cambio y viendo que sigue dando `401`.
- Confirmar visualmente (captura de pantalla) que cada flujo funciona en un navegador real, no solo que el código "se ve bien" leído.
- Confirmar que cerrar sesión limpia `localStorage` de verdad — recargar la pantalla de destino después del logout debe redirigir a `index.html`, no quedarse ahí por una sesión vieja en memoria.

---

## Tarea 1: Servir estáticos + login de dueño

**Archivos:**
- Crear: `backend/src/main/resources/static/index.html`
- Crear: `backend/src/main/resources/static/css/estilos.css`
- Crear: `backend/src/main/resources/static/js/api.js`
- Crear: `backend/src/main/resources/static/js/login.js`
- Crear: `backend/src/main/resources/static/dueno.html`
- Modificar: `backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java`

**Interfaces:**
- Consume: `POST /api/auth/login/dueno` (ya existe) → `AuthResponse(token, nombre, rol)`.
- Produce: `localStorage['pos.sesion'] = {token, nombre, rol}` en éxito; redirección a `dueno.html`; mensaje de error en el formulario si las credenciales son inválidas.

- [ ] **Paso 1: Regla de `SecurityConfig`**

```java
.requestMatchers("/actuator/health", "/api/auth/login/**", "/api/auth/staff",
        "/", "/*.html", "/css/**", "/js/**").permitAll()
```

(reemplaza la lista `permitAll()` que ya existe, agregando los cuatro patrones estáticos al final)

- [ ] **Paso 2: `css/estilos.css`**

```css
body {
    font-family: system-ui, sans-serif;
    background: #f4f4f5;
    margin: 0;
    display: flex;
    min-height: 100vh;
    align-items: center;
    justify-content: center;
}

.tarjeta {
    background: #fff;
    padding: 2rem;
    border-radius: 8px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, .1);
    width: 320px;
}

label {
    display: block;
    margin: .6rem 0 .2rem;
    font-size: .9rem;
}

input, select, button {
    width: 100%;
    padding: .5rem;
    box-sizing: border-box;
    font-size: 1rem;
}

button {
    margin-top: 1rem;
    background: #1d4ed8;
    color: #fff;
    border: none;
    border-radius: 4px;
    cursor: pointer;
}

.error {
    color: #b91c1c;
    font-size: .9rem;
    min-height: 1.2rem;
}

.tabs {
    display: flex;
    margin-bottom: 1rem;
    gap: .4rem;
}

.tabs button {
    margin-top: 0;
    background: #e5e7eb;
    color: #111;
}

.tabs button.activa {
    background: #1d4ed8;
    color: #fff;
}

.oculto {
    display: none;
}
```

- [ ] **Paso 3: `js/api.js`**

```javascript
const CLAVE_SESION = 'pos.sesion';

async function apiPost(ruta, cuerpo) {
    const respuesta = await fetch(ruta, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cuerpo),
    });
    if (!respuesta.ok) {
        throw new Error('la peticion fallo con estado ' + respuesta.status);
    }
    return respuesta.json();
}

function guardarSesion(auth) {
    localStorage.setItem(CLAVE_SESION, JSON.stringify(auth));
}

function obtenerSesion() {
    const crudo = localStorage.getItem(CLAVE_SESION);
    return crudo ? JSON.parse(crudo) : null;
}

function cerrarSesion() {
    localStorage.removeItem(CLAVE_SESION);
    location.href = 'index.html';
}

function requerirSesion(rolEsperado) {
    const sesion = obtenerSesion();
    if (!sesion || sesion.rol !== rolEsperado) {
        location.href = 'index.html';
        return null;
    }
    return sesion;
}

function destinoSegunRol(rol) {
    switch (rol) {
        case 'DUENO': return 'dueno.html';
        case 'MESERO': return 'mesero.html';
        case 'CHEF': return 'cocina.html';
        case 'CAJERO': return 'caja.html';
        default: throw new Error('rol desconocido: ' + rol);
    }
}
```

- [ ] **Paso 4: `index.html`**

```html
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <title>Punto de Venta — Iniciar sesión</title>
    <link rel="stylesheet" href="css/estilos.css">
</head>
<body>
<div class="tarjeta">
    <h1>Iniciar sesión</h1>
    <form id="form-dueno">
        <label for="dueno-username">Usuario</label>
        <input type="text" id="dueno-username" required>
        <label for="dueno-password">Contraseña</label>
        <input type="password" id="dueno-password" required>
        <button type="submit">Entrar</button>
        <p class="error" id="dueno-error"></p>
    </form>
</div>
<script src="js/api.js"></script>
<script src="js/login.js"></script>
</body>
</html>
```

- [ ] **Paso 5: `js/login.js`**

```javascript
document.getElementById('form-dueno').addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const username = document.getElementById('dueno-username').value;
    const password = document.getElementById('dueno-password').value;
    const error = document.getElementById('dueno-error');
    error.textContent = '';
    try {
        const auth = await apiPost('/api/auth/login/dueno', { username, password });
        guardarSesion(auth);
        location.href = destinoSegunRol(auth.rol);
    } catch (e) {
        error.textContent = 'Usuario o contraseña incorrectos.';
    }
});
```

- [ ] **Paso 6: `dueno.html` (stub)**

```html
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <title>Punto de Venta — Dueño</title>
    <link rel="stylesheet" href="css/estilos.css">
</head>
<body>
<div class="tarjeta">
    <h1 id="bienvenida"></h1>
    <button id="btn-salir">Cerrar sesión</button>
</div>
<script src="js/api.js"></script>
<script>
    const sesion = requerirSesion('DUENO');
    if (sesion) {
        document.getElementById('bienvenida').textContent = 'Bienvenido, ' + sesion.nombre;
    }
    document.getElementById('btn-salir').addEventListener('click', cerrarSesion);
</script>
</body>
</html>
```

- [ ] **Paso 7: Verificación manual**

Levantar una Postgres real local y el backend contra ella (Flyway migra solo al iniciar — la V3 ya siembra `admin`/`cambiame123`), luego abrir `http://localhost:8080/` con Playwright y confirmar:
1. Login con `admin` / `cambiame123` → redirige a `dueno.html`, muestra "Bienvenido, Administrador".
2. Login con una contraseña incorrecta → mensaje de error visible en el formulario, se queda en `index.html`, el campo de usuario no se borra.
3. Abrir `http://localhost:8080/dueno.html` directamente, sin haber iniciado sesión → redirige a `index.html`.
4. Desde `dueno.html` ya logueado, click en "Cerrar sesión" → vuelve a `index.html`; recargar `dueno.html` después de eso también redirige a `index.html` (la sesión quedó realmente borrada).
5. Abrir una ruta de la API directamente sin token (ej. `GET /api/usuarios`) y confirmar que sigue dando `401` — la regla nueva de estáticos no aflojó nada de `/api/**`.

Capturar al menos una captura de pantalla como evidencia.

- [ ] **Paso 8: Commit**

```bash
git add backend/src/main/resources/static backend/src/main/java/com/posrestaurante/backend/security/SecurityConfig.java
git commit -m "feat: serve static frontend and add dueno login screen"
```

---

## Tarea 2: Login de personal por PIN + stubs de mesero/cocina/caja

**Archivos:**
- Modificar: `backend/src/main/resources/static/index.html` (agregar la pestaña "Personal")
- Modificar: `backend/src/main/resources/static/js/login.js` (lógica de la pestaña personal)
- Crear: `backend/src/main/resources/static/mesero.html`
- Crear: `backend/src/main/resources/static/cocina.html`
- Crear: `backend/src/main/resources/static/caja.html`

**Interfaces:**
- Consume: `GET /api/auth/staff` (ya existe, ya `permitAll`) → `List<StaffListItem(id, nombre, rol)>`; `POST /api/auth/login/pin` (ya existe) `{usuarioId, pin}` → `AuthResponse`.
- Produce: redirección según rol usando `destinoSegunRol` (Tarea 1): `MESERO → mesero.html`, `CHEF → cocina.html`, `CAJERO → caja.html`.

- [ ] **Paso 1: `index.html` (reemplazo completo, agrega la pestaña)**

```html
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <title>Punto de Venta — Iniciar sesión</title>
    <link rel="stylesheet" href="css/estilos.css">
</head>
<body>
<div class="tarjeta">
    <h1>Iniciar sesión</h1>
    <div class="tabs">
        <button type="button" id="tab-dueno" class="activa">Dueño</button>
        <button type="button" id="tab-personal">Personal</button>
    </div>

    <form id="form-dueno">
        <label for="dueno-username">Usuario</label>
        <input type="text" id="dueno-username" required>
        <label for="dueno-password">Contraseña</label>
        <input type="password" id="dueno-password" required>
        <button type="submit">Entrar</button>
        <p class="error" id="dueno-error"></p>
    </form>

    <form id="form-personal" class="oculto">
        <label for="personal-usuario">Nombre</label>
        <select id="personal-usuario" required></select>
        <label for="personal-pin">PIN</label>
        <input type="password" id="personal-pin" inputmode="numeric" required>
        <button type="submit">Entrar</button>
        <p class="error" id="personal-error"></p>
    </form>
</div>
<script src="js/api.js"></script>
<script src="js/login.js"></script>
</body>
</html>
```

- [ ] **Paso 2: `js/login.js` (reemplazo completo, agrega la pestaña personal)**

```javascript
const tabDueno = document.getElementById('tab-dueno');
const tabPersonal = document.getElementById('tab-personal');
const formDueno = document.getElementById('form-dueno');
const formPersonal = document.getElementById('form-personal');

tabDueno.addEventListener('click', () => mostrarPestana('dueno'));
tabPersonal.addEventListener('click', () => mostrarPestana('personal'));

function mostrarPestana(cual) {
    const esDueno = cual === 'dueno';
    tabDueno.classList.toggle('activa', esDueno);
    tabPersonal.classList.toggle('activa', !esDueno);
    formDueno.classList.toggle('oculto', !esDueno);
    formPersonal.classList.toggle('oculto', esDueno);
    if (!esDueno) {
        cargarPersonal();
    }
}

async function cargarPersonal() {
    const selector = document.getElementById('personal-usuario');
    if (selector.options.length > 0) {
        return; // ya se cargo una vez
    }
    const respuesta = await fetch('/api/auth/staff');
    const lista = await respuesta.json();
    for (const persona of lista) {
        const opcion = document.createElement('option');
        opcion.value = persona.id;
        opcion.textContent = persona.nombre + ' — ' + persona.rol;
        selector.appendChild(opcion);
    }
}

formDueno.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const username = document.getElementById('dueno-username').value;
    const password = document.getElementById('dueno-password').value;
    const error = document.getElementById('dueno-error');
    error.textContent = '';
    try {
        const auth = await apiPost('/api/auth/login/dueno', { username, password });
        guardarSesion(auth);
        location.href = destinoSegunRol(auth.rol);
    } catch (e) {
        error.textContent = 'Usuario o contraseña incorrectos.';
    }
});

formPersonal.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const usuarioId = document.getElementById('personal-usuario').value;
    const pin = document.getElementById('personal-pin').value;
    const error = document.getElementById('personal-error');
    error.textContent = '';
    try {
        const auth = await apiPost('/api/auth/login/pin', { usuarioId, pin });
        guardarSesion(auth);
        location.href = destinoSegunRol(auth.rol);
    } catch (e) {
        error.textContent = 'PIN incorrecto.';
    }
});
```

- [ ] **Paso 3: `mesero.html`, `cocina.html`, `caja.html` (mismo stub que `dueno.html`, cambiando el rol esperado y el título)**

```html
<!-- mesero.html: título "Punto de Venta — Mesero", requerirSesion('MESERO') -->
<!-- cocina.html: título "Punto de Venta — Cocina", requerirSesion('CHEF') -->
<!-- caja.html: título "Punto de Venta — Caja", requerirSesion('CAJERO') -->
```

(estructura idéntica al `dueno.html` de la Tarea 1 — solo cambia el string pasado a `requerirSesion` y el `<title>`)

- [ ] **Paso 4: Verificación manual**

Primero, con el backend ya corriendo y una sesión de dueño, crear un usuario de cada rol de personal vía `POST /api/usuarios` (ya existe, del plan de Aprovisionamiento de Personal) para tener con quién probar — por ejemplo un `MESERO` con PIN `1111`, un `CHEF` con PIN `2222`, un `CAJERO` con PIN `3333`. Luego, con Playwright sobre `http://localhost:8080/`:
1. Pestaña "Personal" → el selector se llena con los tres (nombre — rol); elegir el `MESERO`, PIN `1111` → redirige a `mesero.html`, muestra su nombre.
2. Mismo `MESERO`, PIN incorrecto → mensaje de error, se queda en `index.html`.
3. Repetir con el `CHEF` → `cocina.html`, y con el `CAJERO` → `caja.html`.
4. Abrir `mesero.html` directamente sin sesión → redirige a `index.html`.
5. Con una sesión de `CAJERO` activa (sin cerrar sesión), abrir `mesero.html` directamente → también redirige a `index.html` (la guardia valida el rol, no solo que exista alguna sesión).

Capturar evidencia.

- [ ] **Paso 5: Commit**

```bash
git add backend/src/main/resources/static
git commit -m "feat: staff PIN login and per-role landing stubs"
```

---

## Fuera de Alcance (este plan)

- **Las pantallas reales de cada rol** (tomar pedidos del mesero, tickets de cocina, cobro en caja, menú/usuarios/reportes del dueño) — un plan de frontend separado por rol, igual que el backend.
- **Cualquier framework o build tool** (React, Vite, bundlers, TypeScript) — la spec pide HTML/CSS/JS plano; no se introduce tooling que nadie pidió.
- **Refresh token o "recordarme" persistente** — el JWT ya expira a las 8 horas (`jwt.expiration-minutes: 480`), y ninguna historia pide sesión más larga que eso.
- **Validación de rol en el servidor para las páginas estáticas** — las páginas HTML no son el mecanismo de seguridad real (lo es cada endpoint `/api/**`, ya protegido); la guardia de sesión en el cliente es solo para experiencia de usuario, y así queda documentado explícitamente para que nadie lo confunda con un control de acceso real.
- **Diseño visual pulido o responsive avanzado** — un layout simple, legible y centrado es suficiente; nadie pidió un sistema de diseño.
- **Pruebas automatizadas de frontend** (Jest, Playwright como suite de CI, etc.) — ver "Metodología de verificación" arriba; se verifica manualmente por ahora.
