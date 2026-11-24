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

// Peticion autenticada generica: adjunta el Authorization de la sesion actual.
// Si la API responde 401 (token vencido o invalido), limpia la sesion y manda
// a index.html en vez de dejar la pantalla en un estado roto.
async function apiFetch(metodo, ruta, cuerpo) {
    const sesion = obtenerSesion();
    const opciones = { method: metodo, headers: {} };
    if (sesion) {
        opciones.headers['Authorization'] = 'Bearer ' + sesion.token;
    }
    if (cuerpo !== undefined) {
        opciones.headers['Content-Type'] = 'application/json';
        opciones.body = JSON.stringify(cuerpo);
    }
    const respuesta = await fetch(ruta, opciones);
    if (respuesta.status === 401) {
        cerrarSesion();
        throw new Error('sesion expirada');
    }
    if (!respuesta.ok) {
        let mensaje;
        try {
            mensaje = (await respuesta.json()).message;
        } catch (e) {
            mensaje = null;
        }
        throw new Error(mensaje || ('la peticion fallo con estado ' + respuesta.status));
    }
    if (respuesta.status === 204) {
        return null;
    }
    return respuesta.json();
}

function apiGet(ruta) {
    return apiFetch('GET', ruta);
}

function apiPostAuth(ruta, cuerpo) {
    return apiFetch('POST', ruta, cuerpo ?? {});
}

function apiPatchAuth(ruta, cuerpo) {
    return apiFetch('PATCH', ruta, cuerpo ?? {});
}

function apiDeleteAuth(ruta) {
    return apiFetch('DELETE', ruta);
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
