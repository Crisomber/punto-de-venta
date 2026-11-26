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
// Si la API responde 401, normalmente es el token vencido o invalido, y lo
// correcto es limpiar la sesion y mandar a index.html en vez de dejar la
// pantalla en un estado roto. La excepcion es un endpoint donde un 401
// significa otra cosa de negocio (ej. "contrasena actual incorrecta", igual
// que un login fallido) — para esos, pasar {cerrarSesionSi401: false} y
// manejar el error como cualquier otro.
async function apiFetch(metodo, ruta, cuerpo, opciones = {}) {
    const cerrarSesionSi401 = opciones.cerrarSesionSi401 !== false;
    const sesion = obtenerSesion();
    const fetchOpciones = { method: metodo, headers: {} };
    if (sesion) {
        fetchOpciones.headers['Authorization'] = 'Bearer ' + sesion.token;
    }
    if (cuerpo !== undefined) {
        fetchOpciones.headers['Content-Type'] = 'application/json';
        fetchOpciones.body = JSON.stringify(cuerpo);
    }
    const respuesta = await fetch(ruta, fetchOpciones);
    if (respuesta.status === 401 && cerrarSesionSi401) {
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

function apiPatchAuth(ruta, cuerpo, opciones) {
    return apiFetch('PATCH', ruta, cuerpo ?? {}, opciones);
}

function apiDeleteAuth(ruta) {
    return apiFetch('DELETE', ruta);
}

// Deshabilita el boton y le muestra un spinner (via CSS, clase "cargando")
// mientras `tarea` esta en curso, sin requerir marcado adicional en el HTML:
// el texto se guarda y se restaura solo. Util para cualquier accion que
// dispare una peticion al servidor y no sea instantanea.
async function conBotonCargando(boton, tarea) {
    if (!boton) {
        return tarea();
    }
    const textoOriginal = boton.textContent;
    boton.disabled = true;
    boton.classList.add('cargando');
    boton.textContent = '';
    try {
        return await tarea();
    } finally {
        boton.disabled = false;
        boton.classList.remove('cargando');
        boton.textContent = textoOriginal;
    }
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

// --- Modo oscuro ---
// La clase "oscuro" vive en <html>, no en <body>: así un script minimo en el
// <head> de cada pagina puede aplicarla antes de que el body exista, evitando
// el parpadeo de tema incorrecto al cargar.
const CLAVE_TEMA = 'pos.tema';

function temaActual() {
    return localStorage.getItem(CLAVE_TEMA) === 'oscuro' ? 'oscuro' : 'claro';
}

function aplicarTema(tema) {
    document.documentElement.classList.toggle('oscuro', tema === 'oscuro');
}

function alternarTema() {
    const nuevo = temaActual() === 'oscuro' ? 'claro' : 'oscuro';
    localStorage.setItem(CLAVE_TEMA, nuevo);
    aplicarTema(nuevo);
    actualizarBotonTema();
}

function actualizarBotonTema() {
    const boton = document.getElementById('btn-tema');
    if (boton) {
        boton.textContent = temaActual() === 'oscuro' ? 'Modo claro' : 'Modo oscuro';
    }
}

function iniciarBotonTema() {
    actualizarBotonTema();
    const boton = document.getElementById('btn-tema');
    if (boton) {
        boton.addEventListener('click', alternarTema);
    }
}

document.addEventListener('DOMContentLoaded', iniciarBotonTema);
