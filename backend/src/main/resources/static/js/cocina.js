let sesion = null;
let intervaloPolling = null;

async function iniciar() {
    sesion = requerirSesion('CHEF');
    if (!sesion) {
        return;
    }
    document.getElementById('nombre-usuario').textContent = sesion.nombre;
    document.getElementById('btn-salir').addEventListener('click', () => {
        if (intervaloPolling) {
            clearInterval(intervaloPolling);
        }
        cerrarSesion();
    });
    document.getElementById('btn-actualizar').addEventListener('click', cargarTickets);

    await cargarTickets();
    intervaloPolling = setInterval(cargarTickets, 8000);
}

async function cargarTickets() {
    const error = document.getElementById('error-general');
    try {
        const tickets = await apiGet('/api/tickets/propios');
        tickets.sort((a, b) => new Date(a.fechaGenerado) - new Date(b.fechaGenerado));
        renderTickets(tickets);
        error.textContent = '';
    } catch (e) {
        error.textContent = e.message;
    }
}

function renderTickets(tickets) {
    const contenedor = document.getElementById('lista-tickets');
    contenedor.innerHTML = '';

    if (tickets.length === 0) {
        contenedor.innerHTML = '<p class="vacio">No hay tickets pendientes.</p>';
        return;
    }

    for (const ticket of tickets) {
        const tarjeta = document.createElement('div');
        tarjeta.className = 'panel';

        const cabecera = document.createElement('div');
        cabecera.className = 'ticket-cabecera';
        const lugar = ticket.mesaNumero != null ? 'Mesa ' + ticket.mesaNumero : 'Para llevar';
        cabecera.innerHTML = '<h2 style="margin:0">' + lugar + '</h2><span class="estado-pill">' +
            ticket.estado + '</span>';
        tarjeta.appendChild(cabecera);

        for (const item of ticket.items) {
            tarjeta.appendChild(renderItem(item));
        }

        contenedor.appendChild(tarjeta);
    }
}

function renderItem(item) {
    const fila = document.createElement('div');
    fila.className = 'item-fila';

    const mods = item.modificadorNombres.length ? ' (' + item.modificadorNombres.join(', ') + ')' : '';
    const texto = document.createElement('span');
    texto.textContent = item.cantidad + 'x ' + item.platilloNombre + mods;

    const acciones = document.createElement('div');
    acciones.className = 'item-acciones';

    const pill = document.createElement('span');
    pill.className = 'estado-pill';
    pill.textContent = item.estado;

    const btnPrep = document.createElement('button');
    btnPrep.className = 'boton-mini';
    btnPrep.textContent = 'En preparación';
    btnPrep.addEventListener('click', () => marcarEstado(item.id, 'EN_PREPARACION'));

    const btnListo = document.createElement('button');
    btnListo.className = 'boton-mini';
    btnListo.textContent = 'Listo';
    btnListo.addEventListener('click', () => marcarEstado(item.id, 'LISTO'));

    acciones.appendChild(pill);
    acciones.appendChild(btnPrep);
    acciones.appendChild(btnListo);

    fila.appendChild(texto);
    fila.appendChild(acciones);
    return fila;
}

async function marcarEstado(itemId, estado) {
    const error = document.getElementById('error-general');
    error.textContent = '';
    try {
        await apiPatchAuth('/api/ordenes/items/' + itemId + '/estado', { estado });
        await cargarTickets();
    } catch (e) {
        error.textContent = e.message;
    }
}

iniciar();
