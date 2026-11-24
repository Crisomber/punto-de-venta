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

// Muestra el spinner y desactiva el boton mientras `tarea` esta en curso.
async function conBotonCargando(boton, tarea) {
    boton.disabled = true;
    boton.classList.add('cargando');
    try {
        await tarea();
    } finally {
        boton.disabled = false;
        boton.classList.remove('cargando');
    }
}

formDueno.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const username = document.getElementById('dueno-username').value;
    const password = document.getElementById('dueno-password').value;
    const error = document.getElementById('dueno-error');
    const boton = document.getElementById('dueno-submit');
    error.textContent = '';
    await conBotonCargando(boton, async () => {
        try {
            const auth = await apiPost('/api/auth/login/dueno', { username, password });
            guardarSesion(auth);
            location.href = destinoSegunRol(auth.rol);
        } catch (e) {
            error.textContent = 'Usuario o contraseña incorrectos.';
        }
    });
});

formPersonal.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const usuarioId = document.getElementById('personal-usuario').value;
    const pin = document.getElementById('personal-pin').value;
    const error = document.getElementById('personal-error');
    const boton = document.getElementById('personal-submit');
    error.textContent = '';
    await conBotonCargando(boton, async () => {
        try {
            const auth = await apiPost('/api/auth/login/pin', { usuarioId, pin });
            guardarSesion(auth);
            location.href = destinoSegunRol(auth.rol);
        } catch (e) {
            error.textContent = 'PIN incorrecto.';
        }
    });
});
