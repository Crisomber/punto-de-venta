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
